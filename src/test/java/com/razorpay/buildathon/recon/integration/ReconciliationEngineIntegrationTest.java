package com.razorpay.buildathon.recon.integration;

import com.razorpay.buildathon.recon.model.*;
import com.razorpay.buildathon.recon.repository.AuditLogEntryRepository;
import com.razorpay.buildathon.recon.repository.MatchResultRepository;
import com.razorpay.buildathon.recon.repository.NormalizedTransactionRepository;
import com.razorpay.buildathon.recon.repository.ReconciliationRunRepository;
import com.razorpay.buildathon.recon.service.DeterministicReconciliationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Integration tests for the deterministic reconciliation engine.
 *
 * All findByGatewayRef lookups use the JOIN FETCH query so that lazy
 * associations are loaded without needing an open Session.
 *
 * Scenario matrix:
 *   TX001 → RECONCILED (exact match: all 3 refs share core)
 *   TX002 → RECONCILED (fee-adjusted: bank = gw − fee)
 *   TX003 → RECONCILED (reference variants: GW-TX003 / GW-TX003 / PAY-TX003)
 *   TX004 → RECONCILED (timestamp within settlement lag)
 *   TX005 → EXCEPTION  (gateway + ledger only → MISSING_IN_BANK_FILE)
 *   TX006 → EXCEPTION  (gateway + bank only  → MISSING_IN_LEDGER)
 *   TX007 → EXCEPTION  (amount mismatch beyond tolerance)
 *   TX008 → RECONCILED (symmetric refund on gateway + ledger)
 *   TX009 → EXCEPTION  (duplicate gateway and bank/ledger candidates)
 *   TX010 → RECONCILED (fee-adjusted, standard case)
 */
@SpringBootTest
@ActiveProfiles("test")
class ReconciliationEngineIntegrationTest {

    @Autowired private DeterministicReconciliationService reconService;
    @Autowired private ReconciliationRunRepository        runRepo;
    @Autowired private NormalizedTransactionRepository    txnRepo;
    @Autowired private MatchResultRepository              matchRepo;
    @Autowired private AuditLogEntryRepository            auditRepo;

    private ReconciliationRun run;

    @BeforeEach
    void setup() {
        auditRepo.deleteAll();
        matchRepo.deleteAll();
        txnRepo.deleteAll();
        runRepo.deleteAll();

        run = new ReconciliationRun();
        run.setStatus(RunStatus.NORMALIZING);
        run = runRepo.save(run);

        txnRepo.saveAll(buildGroundTruthTransactions(run));
    }

    // =========================================================================
    // Core scenario assertions — use findByRunIdWithTxns to avoid lazy-load
    // =========================================================================

    @Test
    void reconcile_producesResults_forAllTransactions() {
        ReconciliationRun completed = reconService.reconcile(run.getId());

        assertThat(completed.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(matchRepo.findByRunId(run.getId())).isNotEmpty();
    }

    @Test
    void reconcile_tx001_exactMatch_isReconciled() {
        reconService.reconcile(run.getId());
        MatchResult mr = findByGatewayRef("TX001");

        assertThat(mr.getStatus()).isEqualTo(MatchStatus.RECONCILED);
        assertThat(mr.getGatewayTxn()).isNotNull();
        assertThat(mr.getBankTxn()).isNotNull();
        assertThat(mr.getLedgerTxn()).isNotNull();
    }

    @Test
    void reconcile_tx002_feeAdjusted_isReconciled() {
        reconService.reconcile(run.getId());
        MatchResult mr = findByGatewayRef("TX002");

        assertThat(mr.getStatus()).isEqualTo(MatchStatus.RECONCILED);
        assertThat(mr.getExceptionCategory()).isEqualTo(ExceptionCategory.NONE);
    }

    @Test
    void reconcile_tx005_missingBank_isException() {
        reconService.reconcile(run.getId());
        MatchResult mr = findByGatewayRef("TX005");

        assertThat(mr.getStatus()).isEqualTo(MatchStatus.EXCEPTION);
        assertThat(mr.getExceptionCategory()).isEqualTo(ExceptionCategory.MISSING_IN_BANK_FILE);
        assertThat(mr.getBankTxn()).isNull();
    }

    @Test
    void reconcile_tx006_missingLedger_isException() {
        reconService.reconcile(run.getId());
        MatchResult mr = findByGatewayRef("TX006");

        assertThat(mr.getStatus()).isEqualTo(MatchStatus.EXCEPTION);
        assertThat(mr.getExceptionCategory()).isEqualTo(ExceptionCategory.MISSING_IN_LEDGER);
        assertThat(mr.getLedgerTxn()).isNull();
    }

    @Test
    void reconcile_tx007_amountMismatch_isException() {
        reconService.reconcile(run.getId());
        MatchResult mr = findByGatewayRef("TX007");

        assertThat(mr.getStatus()).isEqualTo(MatchStatus.EXCEPTION);
        assertThat(mr.getExceptionCategory())
                .isEqualTo(ExceptionCategory.AMOUNT_MISMATCH_BEYOND_TOLERANCE);
    }

    @Test
    void reconcile_tx008_refund_isHandledCorrectly() {
        reconService.reconcile(run.getId());
        MatchResult mr = findByGatewayRef("TX008");

        // Both gateway and ledger show REFUNDED → should be RECONCILED
        assertThat(mr.getStatus()).isEqualTo(MatchStatus.RECONCILED);
    }

    @Test
    void reconcile_tx009_duplicate_isException() {
        reconService.reconcile(run.getId());

        // TX009/TX009B share the same core "009" — at least one match result
        // should be DUPLICATE_DETECTED or DUPLICATE_CANDIDATE.
        List<MatchResult> allResults = matchRepo.findByRunId(run.getId());
        boolean hasDuplicate = allResults.stream()
                .anyMatch(mr -> mr.getExceptionCategory() == ExceptionCategory.DUPLICATE_DETECTED
                        || mr.getExceptionCategory() == ExceptionCategory.DUPLICATE_CANDIDATE);
        assertThat(hasDuplicate)
                .as("Expected at least one DUPLICATE_DETECTED or DUPLICATE_CANDIDATE result")
                .isTrue();
    }

    // =========================================================================
    // Persistence invariants
    // =========================================================================

    @Test
    void reconcile_matchResultsPersisted_withRunFk() {
        reconService.reconcile(run.getId());

        List<MatchResult> results = matchRepo.findByRunId(run.getId());
        assertThat(results).isNotEmpty();
        results.forEach(mr ->
            assertThat(mr.getRun().getId()).isEqualTo(run.getId())
        );
    }

    @Test
    void reconcile_auditEntriesPersisted_onePerMatchResult() {
        reconService.reconcile(run.getId());

        List<MatchResult>   results = matchRepo.findByRunId(run.getId());
        List<AuditLogEntry> audits  = auditRepo.findByRunIdOrderByCreatedAtAsc(run.getId());

        assertThat(audits).hasSameSizeAs(results);
        audits.forEach(a -> {
            assertThat(a.getInputsConsidered()).isNotBlank();
            assertThat(a.getReasoning()).isNotBlank();
            assertThat(a.getMethod()).isNotNull();
        });
    }

    @Test
    void reconcile_summaryMetrics_populatedOnRun() {
        ReconciliationRun completed = reconService.reconcile(run.getId());

        assertThat(completed.getMatchedCount()).isGreaterThanOrEqualTo(0);
        assertThat(completed.getExceptionCount()).isGreaterThanOrEqualTo(0);
        assertThat(completed.getMatchRatePct()).isNotNull().isGreaterThanOrEqualTo(0.0);
        assertThat(completed.getProcessingTimeMs()).isNotNull().isGreaterThanOrEqualTo(0);
        assertThat(completed.getCompletedAt()).isNotNull();
    }

    // =========================================================================
    // Edge cases
    // =========================================================================

    @Test
    void reconcile_emptyRun_completesWithZeroCounts() {
        ReconciliationRun emptyRun = new ReconciliationRun();
        emptyRun.setStatus(RunStatus.NORMALIZING);
        emptyRun = runRepo.save(emptyRun);

        ReconciliationRun completed = reconService.reconcile(emptyRun.getId());

        assertThat(completed.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(matchRepo.findByRunId(emptyRun.getId())).isEmpty();
    }

    @Test
    void reconcile_throwsForNonExistentRun() {
        org.junit.jupiter.api.Assertions.assertThrows(
                IllegalArgumentException.class,
                () -> reconService.reconcile(99999L)
        );
    }

    // =========================================================================
    // Helper: eager-load all associations to avoid LazyInitializationException
    // =========================================================================

    private MatchResult findByGatewayRef(String txRef) {
        // findByRunIdWithTxns uses JOIN FETCH so all associations are loaded
        return matchRepo.findByRunIdWithTxns(run.getId()).stream()
                .filter(mr -> mr.getGatewayTxn() != null
                        && mr.getGatewayTxn().getExternalRef().contains(txRef))
                .findFirst()
                .orElseThrow(() -> new AssertionError(
                        "No MatchResult found for gateway ref containing: " + txRef));
    }

    // =========================================================================
    // Ground-truth dataset builder
    // =========================================================================

    private List<NormalizedTransaction> buildGroundTruthTransactions(ReconciliationRun r) {
        Instant base     = Instant.parse("2024-01-15T10:00:00Z");
        Instant bankDate = Instant.parse("2024-01-17T00:00:00Z");

        // TX001 – exact match (all 3 refs share core "TX001")
        var gw1  = gw(r, "GW-TX001",  "1000.00", "30.00",  "SUCCESS",  base);
        var bk1  = bank(r, "SET-TX001", "970.00",  bankDate);
        var lg1  = ledger(r, "PAY-TX001", "1000.00", "PAID",   base);

        // TX002 – fee-adjusted (gw=2500, fee=75, bank=2425)
        var gw2  = gw(r, "GW-TX002",  "2500.00", "75.00",  "SUCCESS",  base.plusSeconds(3600));
        var bk2  = bank(r, "SET-TX002", "2425.00", bankDate);
        var lg2  = ledger(r, "PAY-TX002", "2500.00", "PAID",   base.plusSeconds(3600));

        // TX003 – reference variants (GW-TX003 / GW-TX003 / PAY-TX003 → same core TX003)
        var gw3  = gw(r, "GW-TX003",  "500.00",  "0.00",   "SUCCESS",  base.plusSeconds(7200));
        var bk3  = bank(r, "GW-TX003",  "500.00",  bankDate);
        var lg3  = ledger(r, "PAY-TX003", "500.00",  "PAID",   base.plusSeconds(7200));

        // TX004 – settlement lag (5 days, within window)
        var gw4  = gw(r, "GW-TX004",  "1800.00", "54.00",  "SUCCESS",  base.plusSeconds(10800));
        var bk4  = bank(r, "PAY-TX004", "1746.00", Instant.parse("2024-01-20T00:00:00Z"));
        var lg4  = ledger(r, "PAY-TX004", "1800.00", "PAID",   base.plusSeconds(10800));

        // TX005 – missing bank
        var gw5  = gw(r, "GW-TX005",  "3200.00", "96.00",  "SUCCESS",  base.plusSeconds(14400));
        var lg5  = ledger(r, "PAY-TX005", "3200.00", "PAID",   base.plusSeconds(14400));

        // TX006 – missing ledger
        var gw6  = gw(r, "GW-TX006",  "750.00",  "22.50",  "SUCCESS",  base.plusSeconds(18000));
        var bk6  = bank(r, "SET-TX006", "727.50",  bankDate);

        // TX007 – amount mismatch (bank=1500 vs gw=2000, fee only 60 → 500 gap unexplained)
        var gw7  = gw(r, "GW-TX007",  "2000.00", "60.00",  "SUCCESS",  base.plusSeconds(21600));
        var bk7  = bank(r, "SET-TX007", "1500.00", bankDate);
        var lg7  = ledger(r, "PAY-TX007", "2000.00", "PAID",   base.plusSeconds(21600));

        // TX008 – symmetric refund
        var gw8  = gw(r, "GW-TX008",  "1200.00", "36.00",  "REFUNDED", base.plusSeconds(25200));
        var bk8  = bank(r, "SET-TX008", "1164.00", bankDate);
        var lg8  = ledger(r, "PAY-TX008", "1200.00", "REFUNDED", base.plusSeconds(25200));

        // TX009 – duplicates (two gateway + two bank + two ledger)
        var gw9a = gw(r, "GW-TX009",  "900.00",  "27.00",  "SUCCESS",  base.plusSeconds(28800));
        var gw9b = gw(r, "GW-TX009B", "900.00",  "27.00",  "SUCCESS",  base.plusSeconds(28800).plusSeconds(300));
        var bk9a = bank(r, "SET-TX009",  "873.00",  bankDate);
        var bk9b = bank(r, "SET-TX009B", "873.00",  bankDate);
        var lg9a = ledger(r, "PAY-TX009",  "900.00",  "PAID",   base.plusSeconds(28800));
        var lg9b = ledger(r, "PAY-TX009B", "900.00",  "PAID",   base.plusSeconds(28800));

        // TX010 – fee-adjusted (gw=600, fee=18, bank=582)
        var gw10 = gw(r, "GW-TX010",  "600.00",  "18.00",  "SUCCESS",  base.plusSeconds(32400));
        var bk10 = bank(r, "SET-TX010", "582.00",  bankDate);
        var lg10 = ledger(r, "PAY-TX010", "600.00",  "PAID",   base.plusSeconds(32400));

        return List.of(
                gw1, bk1, lg1,
                gw2, bk2, lg2,
                gw3, bk3, lg3,
                gw4, bk4, lg4,
                gw5,      lg5,          // no bank for TX005
                gw6, bk6,               // no ledger for TX006
                gw7, bk7, lg7,
                gw8, bk8, lg8,
                gw9a, gw9b, bk9a, bk9b, lg9a, lg9b,
                gw10, bk10, lg10
        );
    }

    // -------------------------------------------------------------------------
    // Transaction builders
    // -------------------------------------------------------------------------

    private NormalizedTransaction gw(ReconciliationRun r, String ref, String amount,
                                      String fee, String status, Instant ts) {
        NormalizedTransaction t = new NormalizedTransaction();
        t.setRun(r);
        t.setSourceType(SourceType.GATEWAY);
        t.setExternalRef(ref);
        t.setAmount(new BigDecimal(amount));
        t.setTimestamp(ts);
        t.setPaymentMethod("UPI");
        t.setRawRowJson(String.format(
                "{\"order_id\":\"%s\",\"amount\":\"%s\",\"fee\":\"%s\",\"status\":\"%s\"}",
                ref, amount, fee, status));
        return t;
    }

    private NormalizedTransaction bank(ReconciliationRun r, String ref, String amount, Instant ts) {
        NormalizedTransaction t = new NormalizedTransaction();
        t.setRun(r);
        t.setSourceType(SourceType.BANK);
        t.setExternalRef(ref);
        t.setAmount(new BigDecimal(amount));
        t.setTimestamp(ts);
        t.setRawRowJson(String.format(
                "{\"reference_note\":\"%s\",\"settled_amount\":\"%s\"}", ref, amount));
        return t;
    }

    private NormalizedTransaction ledger(ReconciliationRun r, String ref, String amount,
                                          String status, Instant ts) {
        NormalizedTransaction t = new NormalizedTransaction();
        t.setRun(r);
        t.setSourceType(SourceType.LEDGER);
        t.setExternalRef(ref);
        t.setAmount(new BigDecimal(amount));
        t.setTimestamp(ts);
        t.setRawRowJson(String.format(
                "{\"order_ref\":\"%s\",\"amount\":\"%s\",\"status\":\"%s\"}", ref, amount, status));
        return t;
    }
}
