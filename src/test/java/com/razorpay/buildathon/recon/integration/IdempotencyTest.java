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
 * Idempotency tests for the deterministic reconciliation engine.
 *
 * Key invariant: calling POST /api/runs/{id}/reconcile twice must produce
 * exactly the same results as calling it once. There must be no:
 *   - Duplicate MatchResult rows
 *   - Duplicate AuditLogEntry rows
 *   - Change in status/score/reasoning between runs
 */
@SpringBootTest
@ActiveProfiles("test")
class IdempotencyTest {

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

        // Simple 2-transaction dataset: one reconciled, one exception
        Instant base = Instant.parse("2024-02-01T09:00:00Z");

        NormalizedTransaction gw1 = make(run, SourceType.GATEWAY, "GW-IDEM001", "500.00", base,
                "{\"order_id\":\"GW-IDEM001\",\"amount\":\"500.00\",\"fee\":\"15.00\",\"status\":\"SUCCESS\"}");
        NormalizedTransaction bk1 = make(run, SourceType.BANK, "SET-IDEM001", "485.00",
                base.plusSeconds(86400),
                "{\"reference_note\":\"SET-IDEM001\",\"settled_amount\":\"485.00\"}");
        NormalizedTransaction lg1 = make(run, SourceType.LEDGER, "PAY-IDEM001", "500.00", base,
                "{\"order_ref\":\"PAY-IDEM001\",\"amount\":\"500.00\",\"status\":\"PAID\"}");

        // GW-IDEM002 has no bank record → EXCEPTION
        NormalizedTransaction gw2 = make(run, SourceType.GATEWAY, "GW-IDEM002", "200.00", base,
                "{\"order_id\":\"GW-IDEM002\",\"amount\":\"200.00\",\"fee\":\"0\",\"status\":\"SUCCESS\"}");
        NormalizedTransaction lg2 = make(run, SourceType.LEDGER, "PAY-IDEM002", "200.00", base,
                "{\"order_ref\":\"PAY-IDEM002\",\"amount\":\"200.00\",\"status\":\"PAID\"}");

        txnRepo.saveAll(List.of(gw1, bk1, lg1, gw2, lg2));
    }

    @Test
    void reconcileTwice_sameNumberOfMatchResults() {
        reconService.reconcile(run.getId());
        int firstCount = matchRepo.findByRunId(run.getId()).size();

        // Reset run status to allow re-run
        ReconciliationRun r = runRepo.findById(run.getId()).get();
        r.setStatus(RunStatus.NORMALIZING);
        runRepo.save(r);

        reconService.reconcile(run.getId());
        int secondCount = matchRepo.findByRunId(run.getId()).size();

        assertThat(secondCount).isEqualTo(firstCount);
    }

    @Test
    void reconcileTwice_sameNumberOfAuditEntries() {
        reconService.reconcile(run.getId());
        int firstAudit = auditRepo.findByRunIdOrderByCreatedAtAsc(run.getId()).size();

        ReconciliationRun r = runRepo.findById(run.getId()).get();
        r.setStatus(RunStatus.NORMALIZING);
        runRepo.save(r);

        reconService.reconcile(run.getId());
        int secondAudit = auditRepo.findByRunIdOrderByCreatedAtAsc(run.getId()).size();

        assertThat(secondAudit).isEqualTo(firstAudit);
    }

    @Test
    void reconcileTwice_sameStatuses() {
        reconService.reconcile(run.getId());
        List<MatchStatus> firstStatuses = matchRepo.findByRunId(run.getId()).stream()
                .map(MatchResult::getStatus)
                .sorted(java.util.Comparator.comparing(Enum::name))
                .toList();

        ReconciliationRun r = runRepo.findById(run.getId()).get();
        r.setStatus(RunStatus.NORMALIZING);
        runRepo.save(r);

        reconService.reconcile(run.getId());
        List<MatchStatus> secondStatuses = matchRepo.findByRunId(run.getId()).stream()
                .map(MatchResult::getStatus)
                .sorted(java.util.Comparator.comparing(Enum::name))
                .toList();

        assertThat(secondStatuses).isEqualTo(firstStatuses);
    }

    @Test
    void reconcileTwice_runMetricsConsistent() {
        reconService.reconcile(run.getId());
        ReconciliationRun first = runRepo.findById(run.getId()).get();

        first.setStatus(RunStatus.NORMALIZING);
        runRepo.save(first);

        reconService.reconcile(run.getId());
        ReconciliationRun second = runRepo.findById(run.getId()).get();

        assertThat(second.getMatchedCount()).isEqualTo(first.getMatchedCount());
        assertThat(second.getExceptionCount()).isEqualTo(first.getExceptionCount());
    }

    // -------------------------------------------------------------------------

    private NormalizedTransaction make(ReconciliationRun r, SourceType type, String ref,
                                        String amount, Instant ts, String rawJson) {
        NormalizedTransaction t = new NormalizedTransaction();
        t.setRun(r);
        t.setSourceType(type);
        t.setExternalRef(ref);
        t.setAmount(new BigDecimal(amount));
        t.setTimestamp(ts);
        t.setRawRowJson(rawJson);
        return t;
    }
}
