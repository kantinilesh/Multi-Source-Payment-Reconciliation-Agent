package com.razorpay.buildathon.recon.engine;

import com.razorpay.buildathon.recon.config.ReconMatchingConfig;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import com.razorpay.buildathon.recon.model.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Phase 3 candidate-generation strategy. Given the normalized transactions for
 * one reconciliation run, this component produces {@link CandidateSet} objects —
 * one per gateway transaction — plus orphan sets for bank/ledger records that
 * could not be anchored to a gateway record.
 *
 * Strategy (applied in order per gateway txn):
 *
 * 1. STRONG-ID PASS: find bank/ledger records whose numeric reference core
 *    exactly matches the gateway core (e.g. GW-83921 → core=83921).
 *
 * 2. AMOUNT-TIME PASS: for unmatched bank/ledger records, apply temporal
 *    proximity (within settlementLagDaysMax) AND amount compatibility
 *    (bank_amount ≈ gateway_amount − fee, or exact equality) as secondary signals.
 *
 * 3. GATEWAY DUPLICATE DETECTION: if two gateway records have the same amount
 *    and timestamps within 1 hour of each other, they are marked as duplicates
 *    and their CandidateSets are flagged so DuplicateDetectionRule can catch them.
 *
 * 4. ORPHAN HANDLING: bank and ledger records that could not be anchored to any
 *    gateway record are emitted as orphan CandidateSets (gatewayTxn=null).
 *
 * This is NOT the scoring step — it just collects candidates. The MatchScorer
 * and rules decide how to rank and classify them.
 */
@Component
public class CandidateGenerator {

    private static final Logger log = LoggerFactory.getLogger(CandidateGenerator.class);

    /** Two gateway records within this window with the same amount → potential duplicates. */
    private static final long GATEWAY_DUPE_WINDOW_SECONDS = 3600L; // 1 hour

    private final ReconMatchingConfig config;

    public CandidateGenerator(ReconMatchingConfig config) {
        this.config = config;
    }

    /**
     * Entry point: produce all candidate sets for a given run's transactions.
     *
     * @param all every NormalizedTransaction for the run (all 3 sources mixed)
     * @return list of CandidateSets, one per gateway txn + orphan sets
     */
    public List<CandidateSet> generate(List<NormalizedTransaction> all) {
        List<NormalizedTransaction> gateways = filterBySource(all, SourceType.GATEWAY);
        List<NormalizedTransaction> banks    = filterBySource(all, SourceType.BANK);
        List<NormalizedTransaction> ledgers  = filterBySource(all, SourceType.LEDGER);

        log.info("CandidateGenerator: {} gateway, {} bank, {} ledger records",
                gateways.size(), banks.size(), ledgers.size());

        // Identify gateway-level duplicate groups (same amount + close timestamp)
        Set<Long> duplicateGatewayIds = findDuplicateGatewayIds(gateways);
        if (!duplicateGatewayIds.isEmpty()) {
            log.info("CandidateGenerator: {} gateway records identified as potential duplicates",
                    duplicateGatewayIds.size());
        }

        // Build index: numericCore → list<txn> for bank and ledger
        Map<String, List<NormalizedTransaction>> bankByCore   = indexByCore(banks);
        Map<String, List<NormalizedTransaction>> ledgerByCore = indexByCore(ledgers);

        Set<Long> matchedBankIds   = new HashSet<>();
        Set<Long> matchedLedgerIds = new HashSet<>();
        List<CandidateSet> result  = new ArrayList<>();

        for (NormalizedTransaction gw : gateways) {
            String gwCore = RawFieldExtractor.extractNumericCore(gw.getExternalRef());

            // --- Pass 1: strong ID match ---
            List<NormalizedTransaction> bankCandidates   = new ArrayList<>(
                    bankByCore.getOrDefault(gwCore, Collections.emptyList()));
            List<NormalizedTransaction> ledgerCandidates = new ArrayList<>(
                    ledgerByCore.getOrDefault(gwCore, Collections.emptyList()));

            // --- Pass 2: amount+time proximity for stragglers ---
            if (bankCandidates.isEmpty()) {
                bankCandidates = findByAmountAndTime(gw, banks, matchedBankIds);
            }
            if (ledgerCandidates.isEmpty()) {
                ledgerCandidates = findByAmountAndTime(gw, ledgers, matchedLedgerIds);
            }

            // --- Gateway duplicate injection ---
            // If this gateway record is part of a duplicate group, inject the sibling
            // gateway's already-matched candidates so DuplicateDetectionRule can see > 1.
            if (duplicateGatewayIds.contains(gw.getId())) {
                // Collect ALL bank/ledger records that match the same amount + time
                // window as this gateway — these may already be claimed by a sibling.
                List<NormalizedTransaction> allBankMatches =
                        findByAmountAndTime(gw, banks, Collections.emptySet());
                List<NormalizedTransaction> allLedgerMatches =
                        findByAmountAndTime(gw, ledgers, Collections.emptySet());

                // Merge candidates (unique by ID)
                Set<Long> currentBankIds = bankCandidates.stream()
                        .map(NormalizedTransaction::getId).collect(Collectors.toSet());
                for (NormalizedTransaction b : allBankMatches) {
                    if (!currentBankIds.contains(b.getId())) {
                        bankCandidates.add(b);
                    }
                }
                Set<Long> currentLedgerIds = ledgerCandidates.stream()
                        .map(NormalizedTransaction::getId).collect(Collectors.toSet());
                for (NormalizedTransaction l : allLedgerMatches) {
                    if (!currentLedgerIds.contains(l.getId())) {
                        ledgerCandidates.add(l);
                    }
                }
            }

            // Track what we've assigned
            bankCandidates.forEach(t -> matchedBankIds.add(t.getId()));
            ledgerCandidates.forEach(t -> matchedLedgerIds.add(t.getId()));

            result.add(new CandidateSet(gw, bankCandidates, ledgerCandidates));
        }

        // --- Orphan bank records ---
        banks.stream()
                .filter(b -> !matchedBankIds.contains(b.getId()))
                .forEach(b -> {
                    log.debug("Orphan bank record id={} ref={}", b.getId(), b.getExternalRef());
                    result.add(new CandidateSet(null,
                            List.of(b), Collections.emptyList()));
                });

        // --- Orphan ledger records ---
        ledgers.stream()
                .filter(l -> !matchedLedgerIds.contains(l.getId()))
                .forEach(l -> {
                    log.debug("Orphan ledger record id={} ref={}", l.getId(), l.getExternalRef());
                    result.add(new CandidateSet(null,
                            Collections.emptyList(), List.of(l)));
                });

        log.info("CandidateGenerator: produced {} candidate sets ({} with gateway anchor)",
                result.size(), gateways.size());
        return result;
    }

    // -------------------------------------------------------------------------
    // Private helpers
    // -------------------------------------------------------------------------

    /**
     * Identifies gateway records that are likely duplicates of each other:
     * same amount AND timestamp within the duplicate detection window.
     * Returns the IDs of ALL records that have at least one such sibling.
     */
    private Set<Long> findDuplicateGatewayIds(List<NormalizedTransaction> gateways) {
        Set<Long> dupeIds = new HashSet<>();
        for (int i = 0; i < gateways.size(); i++) {
            for (int j = i + 1; j < gateways.size(); j++) {
                NormalizedTransaction a = gateways.get(i);
                NormalizedTransaction b = gateways.get(j);
                if (a.getAmount().compareTo(b.getAmount()) == 0) {
                    long delta = Math.abs(Duration.between(
                            a.getTimestamp(), b.getTimestamp()).getSeconds());
                    if (delta <= GATEWAY_DUPE_WINDOW_SECONDS) {
                        dupeIds.add(a.getId());
                        dupeIds.add(b.getId());
                        log.debug("Gateway duplicate pair detected: id={} ref={} and id={} ref={}",
                                a.getId(), a.getExternalRef(), b.getId(), b.getExternalRef());
                    }
                }
            }
        }
        return dupeIds;
    }

    private List<NormalizedTransaction> filterBySource(List<NormalizedTransaction> all,
                                                        SourceType type) {
        return all.stream().filter(t -> type == t.getSourceType()).collect(Collectors.toList());
    }

    /** Build a map from numeric-core → list of transactions for fast lookup. */
    private Map<String, List<NormalizedTransaction>> indexByCore(
            List<NormalizedTransaction> txns) {
        Map<String, List<NormalizedTransaction>> index = new HashMap<>();
        for (NormalizedTransaction t : txns) {
            String core = RawFieldExtractor.extractNumericCore(t.getExternalRef());
            if (!core.isBlank()) {
                index.computeIfAbsent(core, k -> new ArrayList<>()).add(t);
            }
        }
        return index;
    }

    /**
     * Secondary pass: find records from {@code pool} that are temporally close
     * to {@code gw} AND whose amount is consistent with the gateway amount
     * (exact or fee-adjusted). Records in {@code alreadyMatched} are skipped
     * unless the set is empty (used for gateway-duplicate candidate expansion).
     */
    private List<NormalizedTransaction> findByAmountAndTime(
            NormalizedTransaction gw,
            List<NormalizedTransaction> pool,
            Set<Long> alreadyMatched) {

        long lagSeconds = (long) config.getSettlementLagDaysMax() * 86_400L;
        BigDecimal gwAmount = gw.getAmount();
        BigDecimal gwFee    = RawFieldExtractor.extractGatewayFee(gw);
        BigDecimal feeMin   = gwAmount.multiply(
                BigDecimal.valueOf(config.getFeeTolerancePctMin() / 100.0));
        BigDecimal feeMax   = gwAmount.multiply(
                BigDecimal.valueOf(config.getFeeTolerancePctMax() / 100.0));
        BigDecimal netMin   = gwAmount.subtract(feeMax);
        BigDecimal netMax   = gwAmount.subtract(feeMin);

        List<NormalizedTransaction> candidates = new ArrayList<>();
        for (NormalizedTransaction t : pool) {
            if (!alreadyMatched.isEmpty() && alreadyMatched.contains(t.getId())) continue;

            // Temporal check: |gw.timestamp - t.timestamp| <= lagSeconds
            long delta = Math.abs(Duration.between(gw.getTimestamp(), t.getTimestamp()).getSeconds());
            if (delta > lagSeconds) continue;

            BigDecimal amt = t.getAmount();
            boolean exactAmount = gwAmount.compareTo(amt) == 0;
            // Fee adjustment applies ONLY to Bank settlement deposits, never Internal Ledger entries
            boolean isBank = t.getSourceType() == SourceType.BANK;
            boolean feeAdjusted = isBank && (gwFee.compareTo(BigDecimal.ZERO) > 0
                    ? (amt.compareTo(netMin) >= 0 && amt.compareTo(netMax) <= 0)
                    : (amt.compareTo(netMin) >= 0 && amt.compareTo(gwAmount) <= 0));

            if (exactAmount || feeAdjusted) {
                candidates.add(t);
            }
        }
        return candidates;
    }
}
