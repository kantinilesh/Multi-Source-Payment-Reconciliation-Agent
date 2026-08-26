package com.razorpay.buildathon.recon.ai;

import com.razorpay.buildathon.recon.ai.dto.EvaluationMetricsResponse;
import com.razorpay.buildathon.recon.ai.service.ReconciliationEvaluationService;
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

@SpringBootTest
@ActiveProfiles("test")
class Phase4IntegrationTest {

    @Autowired private DeterministicReconciliationService reconService;
    @Autowired private ReconciliationEvaluationService evaluationService;
    @Autowired private ReconciliationRunRepository runRepo;
    @Autowired private NormalizedTransactionRepository txnRepo;
    @Autowired private MatchResultRepository matchRepo;
    @Autowired private AuditLogEntryRepository auditRepo;

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

        txnRepo.saveAll(buildDataset(run));
    }

    @Test
    void fullReconciliationPipeline_runsDeterministicAndAiReasoning() {
        ReconciliationRun completedRun = reconService.reconcile(run.getId());

        assertThat(completedRun.getStatus()).isEqualTo(RunStatus.COMPLETED);
        assertThat(completedRun.getMatchedCount()).isGreaterThanOrEqualTo(0);
        assertThat(completedRun.getAiAssistedCount()).isGreaterThanOrEqualTo(0);

        List<MatchResult> matches = matchRepo.findByRunIdWithTxns(run.getId());
        assertThat(matches).isNotEmpty();

        List<AuditLogEntry> auditLog = auditRepo.findByRunIdOrderByCreatedAtAsc(run.getId());
        assertThat(auditLog).isNotEmpty();
    }

    @Test
    void evaluationService_producesComparativeMetrics() {
        reconService.reconcile(run.getId());

        EvaluationMetricsResponse eval = evaluationService.evaluateRun(run.getId());

        assertThat(eval.runId()).isEqualTo(run.getId());
        assertThat(eval.baseline()).isNotNull();
        assertThat(eval.aiEnhanced()).isNotNull();
        assertThat(eval.delta()).isNotNull();
    }

    @Test
    void aiExplanation_canBeInvokedOnSingleMatchResult() {
        reconService.reconcile(run.getId());

        List<MatchResult> matches = matchRepo.findByRunIdWithTxns(run.getId());
        MatchResult target = matches.get(0);

        MatchResult updated = matchRepo.findById(target.getId()).get();
        assertThat(updated).isNotNull();
        assertThat(updated.getReasoning()).isNotBlank();
    }

    private List<NormalizedTransaction> buildDataset(ReconciliationRun r) {
        Instant base = Instant.parse("2024-01-15T10:00:00Z");
        Instant bankDate = Instant.parse("2024-01-17T00:00:00Z");

        // GW-P4-001 (reconciled exact)
        NormalizedTransaction gw1 = gw(r, "GW-P4-001", "1000.00", "30.00", "SUCCESS", base);
        NormalizedTransaction bk1 = bank(r, "SET-P4-001", "970.00", bankDate);
        NormalizedTransaction lg1 = ledger(r, "PAY-P4-001", "1000.00", "PAID", base);

        // GW-P4-002 (missing bank)
        NormalizedTransaction gw2 = gw(r, "GW-P4-002", "2500.00", "75.00", "SUCCESS", base.plusSeconds(3600));
        NormalizedTransaction lg2 = ledger(r, "PAY-P4-002", "2500.00", "PAID", base.plusSeconds(3600));

        return List.of(gw1, bk1, lg1, gw2, lg2);
    }

    private NormalizedTransaction gw(ReconciliationRun r, String ref, String amount, String fee, String status, Instant ts) {
        NormalizedTransaction t = new NormalizedTransaction();
        t.setRun(r);
        t.setSourceType(SourceType.GATEWAY);
        t.setExternalRef(ref);
        t.setAmount(new BigDecimal(amount));
        t.setTimestamp(ts);
        t.setPaymentMethod("UPI");
        t.setRawRowJson(String.format("{\"order_id\":\"%s\",\"amount\":\"%s\",\"fee\":\"%s\",\"status\":\"%s\"}", ref, amount, fee, status));
        return t;
    }

    private NormalizedTransaction bank(ReconciliationRun r, String ref, String amount, Instant ts) {
        NormalizedTransaction t = new NormalizedTransaction();
        t.setRun(r);
        t.setSourceType(SourceType.BANK);
        t.setExternalRef(ref);
        t.setAmount(new BigDecimal(amount));
        t.setTimestamp(ts);
        t.setRawRowJson(String.format("{\"reference_note\":\"%s\",\"settled_amount\":\"%s\"}", ref, amount));
        return t;
    }

    private NormalizedTransaction ledger(ReconciliationRun r, String ref, String amount, String status, Instant ts) {
        NormalizedTransaction t = new NormalizedTransaction();
        t.setRun(r);
        t.setSourceType(SourceType.LEDGER);
        t.setExternalRef(ref);
        t.setAmount(new BigDecimal(amount));
        t.setTimestamp(ts);
        t.setRawRowJson(String.format("{\"order_ref\":\"%s\",\"amount\":\"%s\",\"status\":\"%s\"}", ref, amount, status));
        return t;
    }
}
