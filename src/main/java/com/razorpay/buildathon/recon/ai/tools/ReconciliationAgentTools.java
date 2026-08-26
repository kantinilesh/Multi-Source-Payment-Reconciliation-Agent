package com.razorpay.buildathon.recon.ai.tools;

import com.razorpay.buildathon.recon.engine.RawFieldExtractor;
import com.razorpay.buildathon.recon.model.MatchResult;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import com.razorpay.buildathon.recon.model.ReconciliationRun;
import com.razorpay.buildathon.recon.model.SourceType;
import com.razorpay.buildathon.recon.repository.MatchResultRepository;
import com.razorpay.buildathon.recon.repository.NormalizedTransactionRepository;
import com.razorpay.buildathon.recon.repository.ReconciliationRunRepository;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.*;

/**
 * Controlled, read-only agent toolset for the AI Exception Reasoning Agent.
 * Exposes focused domain query methods without providing arbitrary database access.
 */
@Component
public class ReconciliationAgentTools {

    private final NormalizedTransactionRepository transactionRepository;
    private final MatchResultRepository matchResultRepository;
    private final ReconciliationRunRepository runRepository;

    public ReconciliationAgentTools(NormalizedTransactionRepository transactionRepository,
                                    MatchResultRepository matchResultRepository,
                                    ReconciliationRunRepository runRepository) {
        this.transactionRepository = transactionRepository;
        this.matchResultRepository = matchResultRepository;
        this.runRepository = runRepository;
    }

    /**
     * Tool 1: Fetch a normalized transaction by its primary key ID.
     */
    public Optional<NormalizedTransaction> getTransaction(Long id) {
        if (id == null) return Optional.empty();
        return transactionRepository.findById(id);
    }

    /**
     * Tool 2: Fetch related transactions for a given reference core across all sources in a run.
     */
    public List<NormalizedTransaction> getRelatedTransactions(Long runId, String externalRef) {
        if (runId == null || externalRef == null || externalRef.isBlank()) {
            return Collections.emptyList();
        }
        String core = RawFieldExtractor.extractNumericCore(externalRef);
        if (core.isBlank()) return Collections.emptyList();

        List<NormalizedTransaction> allInRun = transactionRepository.findByRunId(runId);
        List<NormalizedTransaction> matches = new ArrayList<>();
        for (NormalizedTransaction t : allInRun) {
            String tCore = RawFieldExtractor.extractNumericCore(t.getExternalRef());
            if (core.equalsIgnoreCase(tCore) || t.getExternalRef().toUpperCase().contains(core)) {
                matches.add(t);
            }
        }
        return matches;
    }

    /**
     * Tool 3: Side-by-side candidate comparison map for a given match result.
     */
    public Map<String, Object> compareCandidates(MatchResult matchResult) {
        Map<String, Object> comp = new HashMap<>();
        if (matchResult == null) return comp;

        comp.put("matchResultId", matchResult.getId());
        comp.put("status", matchResult.getStatus() != null ? matchResult.getStatus().name() : null);
        comp.put("method", matchResult.getMethod() != null ? matchResult.getMethod().name() : null);
        comp.put("confidence", matchResult.getConfidence());

        if (matchResult.getGatewayTxn() != null) {
            NormalizedTransaction gw = matchResult.getGatewayTxn();
            comp.put("gateway", Map.of(
                    "id", gw.getId(),
                    "ref", gw.getExternalRef(),
                    "amount", gw.getAmount(),
                    "fee", RawFieldExtractor.extractGatewayFee(gw),
                    "status", RawFieldExtractor.extractStatus(gw),
                    "timestamp", gw.getTimestamp().toString()
            ));
        }

        if (matchResult.getBankTxn() != null) {
            NormalizedTransaction bk = matchResult.getBankTxn();
            comp.put("bank", Map.of(
                    "id", bk.getId(),
                    "ref", bk.getExternalRef(),
                    "amount", bk.getAmount(),
                    "timestamp", bk.getTimestamp().toString()
            ));
        }

        if (matchResult.getLedgerTxn() != null) {
            NormalizedTransaction lg = matchResult.getLedgerTxn();
            comp.put("ledger", Map.of(
                    "id", lg.getId(),
                    "ref", lg.getExternalRef(),
                    "amount", lg.getAmount(),
                    "status", RawFieldExtractor.extractStatus(lg),
                    "timestamp", lg.getTimestamp().toString()
            ));
        }

        return comp;
    }

    /**
     * Tool 4: Calculate expected net settlement amount and fee percentage for a gateway charge.
     */
    public Map<String, Object> calculateExpectedSettlement(BigDecimal gwAmount, BigDecimal fee) {
        if (gwAmount == null || gwAmount.compareTo(BigDecimal.ZERO) <= 0) {
            return Map.of("error", "Invalid gross amount");
        }
        BigDecimal actualFee = fee != null ? fee : BigDecimal.ZERO;
        BigDecimal expectedNet = gwAmount.subtract(actualFee);
        BigDecimal feePct = actualFee.divide(gwAmount, 6, RoundingMode.HALF_UP)
                                     .multiply(BigDecimal.valueOf(100));

        Map<String, Object> res = new HashMap<>();
        res.put("grossAmount", gwAmount);
        res.put("fee", actualFee);
        res.put("expectedNetSettlement", expectedNet);
        res.put("feePercentage", feePct.setScale(2, RoundingMode.HALF_UP));
        return res;
    }

    /**
     * Tool 5: Extract gateway fee, payment method, and status information from a gateway transaction.
     */
    public Map<String, Object> getFeeInformation(NormalizedTransaction gwTxn) {
        if (gwTxn == null || gwTxn.getSourceType() != SourceType.GATEWAY) {
            return Map.of("error", "Not a gateway transaction");
        }
        BigDecimal fee = RawFieldExtractor.extractGatewayFee(gwTxn);
        String status = RawFieldExtractor.extractStatus(gwTxn);
        String method = gwTxn.getPaymentMethod() != null ? gwTxn.getPaymentMethod() : "UNKNOWN";

        return Map.of(
                "txnId", gwTxn.getId(),
                "externalRef", gwTxn.getExternalRef(),
                "grossAmount", gwTxn.getAmount(),
                "fee", fee,
                "status", status,
                "paymentMethod", method
        );
    }

    /**
     * Tool 6: Fetch high-level summary context for a reconciliation run.
     */
    public Map<String, Object> getRunContext(Long runId) {
        if (runId == null) return Collections.emptyMap();
        Optional<ReconciliationRun> runOpt = runRepository.findById(runId);
        if (runOpt.isEmpty()) return Collections.emptyMap();

        ReconciliationRun run = runOpt.get();
        List<NormalizedTransaction> txns = transactionRepository.findByRunId(runId);

        long gwCount = txns.stream().filter(t -> t.getSourceType() == SourceType.GATEWAY).count();
        long bkCount = txns.stream().filter(t -> t.getSourceType() == SourceType.BANK).count();
        long lgCount = txns.stream().filter(t -> t.getSourceType() == SourceType.LEDGER).count();

        Map<String, Object> ctx = new HashMap<>();
        ctx.put("runId", run.getId());
        ctx.put("status", run.getStatus() != null ? run.getStatus().name() : null);
        ctx.put("totalNormalizedTransactions", txns.size());
        ctx.put("gatewayCount", gwCount);
        ctx.put("bankCount", bkCount);
        ctx.put("ledgerCount", lgCount);
        ctx.put("gatewayFileName", run.getGatewayFileName());
        ctx.put("bankFileName", run.getBankFileName());
        ctx.put("ledgerFileName", run.getLedgerFileName());
        return ctx;
    }
}
