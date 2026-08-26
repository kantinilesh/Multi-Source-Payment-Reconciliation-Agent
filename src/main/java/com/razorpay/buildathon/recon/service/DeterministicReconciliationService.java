package com.razorpay.buildathon.recon.service;

import com.razorpay.buildathon.recon.engine.CandidateSet;
import com.razorpay.buildathon.recon.engine.DeterministicReconciliationEngine;
import com.razorpay.buildathon.recon.engine.MatchScore;
import com.razorpay.buildathon.recon.engine.RawFieldExtractor;
import com.razorpay.buildathon.recon.engine.rules.ReconciliationRule;
import com.razorpay.buildathon.recon.model.*;
import com.razorpay.buildathon.recon.repository.AuditLogEntryRepository;
import com.razorpay.buildathon.recon.repository.MatchResultRepository;
import com.razorpay.buildathon.recon.repository.NormalizedTransactionRepository;
import com.razorpay.buildathon.recon.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates the Phase 3 deterministic reconciliation pipeline for one run.
 *
 * Idempotent: deletes existing MatchResult/AuditLogEntry rows before re-running.
 */
@Service
public class DeterministicReconciliationService {

    private static final Logger log = LoggerFactory.getLogger(DeterministicReconciliationService.class);

    private final ReconciliationRunRepository       runRepository;
    private final NormalizedTransactionRepository   transactionRepository;
    private final MatchResultRepository             matchResultRepository;
    private final AuditLogEntryRepository           auditLogEntryRepository;
    private final DeterministicReconciliationEngine engine;
    private final com.razorpay.buildathon.recon.ai.service.AiExceptionReasoningService aiReasoningService;

    public DeterministicReconciliationService(
            ReconciliationRunRepository runRepository,
            NormalizedTransactionRepository transactionRepository,
            MatchResultRepository matchResultRepository,
            AuditLogEntryRepository auditLogEntryRepository,
            DeterministicReconciliationEngine engine,
            com.razorpay.buildathon.recon.ai.service.AiExceptionReasoningService aiReasoningService) {
        this.runRepository = runRepository;
        this.transactionRepository = transactionRepository;
        this.matchResultRepository = matchResultRepository;
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.engine = engine;
        this.aiReasoningService = aiReasoningService;
    }

    /**
     * Run (or re-run) the reconciliation pipeline for the given run ID.
     * Safe to call multiple times — idempotent by design.
     * Integrates Phase 3 (Deterministic Engine) + Phase 4 (AI Exception Reasoning).
     */
    @Transactional
    public ReconciliationRun reconcile(Long runId) {
        ReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No such run: " + runId));

        if (run.getStatus() == RunStatus.FAILED) {
            throw new IllegalStateException(
                    "Run " + runId + " is in FAILED state and cannot be reconciled.");
        }

        long startMs = System.currentTimeMillis();
        log.info("Starting reconciliation for run={}", runId);

        // --- Idempotency: remove any previous results for this run ---
        List<AuditLogEntry> existingAudit = auditLogEntryRepository.findByRunIdOrderByCreatedAtAsc(runId);
        if (!existingAudit.isEmpty()) {
            log.info("Idempotency: deleting {} existing audit entries for run={}", existingAudit.size(), runId);
            auditLogEntryRepository.deleteAll(existingAudit);
        }
        List<MatchResult> existingMatches = matchResultRepository.findByRunId(runId);
        if (!existingMatches.isEmpty()) {
            log.info("Idempotency: deleting {} existing match results for run={}", existingMatches.size(), runId);
            matchResultRepository.deleteAll(existingMatches);
        }

        // --- Mark run as MATCHING ---
        run.setStatus(RunStatus.MATCHING);
        run = runRepository.save(run);

        // --- Load normalized transactions ---
        List<NormalizedTransaction> allTxns = transactionRepository.findByRunId(runId);
        log.info("Loaded {} normalized transactions for run={}", allTxns.size(), runId);

        try {
            // --- Phase 3: Run Deterministic Engine ---
            List<DeterministicReconciliationEngine.EngineResult> engineResults =
                    engine.reconcile(allTxns);

            // --- Persist initial deterministic results ---
            List<MatchResult> matchResults = new ArrayList<>(engineResults.size());
            List<AuditLogEntry> auditEntries = new ArrayList<>(engineResults.size());

            for (DeterministicReconciliationEngine.EngineResult er : engineResults) {
                MatchResult mr = buildMatchResult(run, er);
                matchResults.add(mr);
            }

            matchResults = matchResultRepository.saveAll(matchResults);

            for (int i = 0; i < engineResults.size(); i++) {
                AuditLogEntry audit = buildAuditEntry(run, matchResults.get(i), engineResults.get(i));
                auditEntries.add(audit);
            }
            auditLogEntryRepository.saveAll(auditEntries);

            // --- Phase 4: Run AI Exception Reasoning Agent on ambiguous/unresolved matches ---
            run = aiReasoningService.processRun(runId);

            run.setStatus(RunStatus.COMPLETED);
            run.setProcessingTimeMs(System.currentTimeMillis() - startMs);
            run = runRepository.save(run);

            log.info("Reconciliation complete for run={}: {} reconciled, {} review-required, " +
                    "{} exceptions, {} AI-assisted in {}ms",
                    runId, run.getMatchedCount(), run.getPartiallyMatchedCount(), run.getExceptionCount(),
                    run.getAiAssistedCount(), run.getProcessingTimeMs());

            return run;

        } catch (Exception e) {
            log.error("Reconciliation failed for run={}: {}", runId, e.getMessage(), e);
            run.setStatus(RunStatus.FAILED);
            runRepository.save(run);
            throw new RuntimeException("Reconciliation failed for run " + runId + ": " + e.getMessage(), e);
        }
    }

    // -------------------------------------------------------------------------

    private MatchResult buildMatchResult(ReconciliationRun run,
                                         DeterministicReconciliationEngine.EngineResult er) {
        ReconciliationRule.RuleOutcome outcome = er.outcome();
        CandidateSet cs = er.candidateSet();
        MatchScore score = er.score();

        MatchResult mr = new MatchResult();
        mr.setRun(run);
        mr.setStatus(outcome.status());
        mr.setMethod(outcome.method());
        mr.setExceptionCategory(outcome.exceptionCategory() != null
                ? outcome.exceptionCategory() : ExceptionCategory.NONE);
        mr.setGatewayTxn(cs.gatewayTxn());
        mr.setBankTxn(cs.primaryBankTxn().orElse(null));
        mr.setLedgerTxn(cs.primaryLedgerTxn().orElse(null));
        mr.setConfidence(score.getConfidence());
        mr.setReasoning(outcome.reasoning());
        return mr;
    }

    private AuditLogEntry buildAuditEntry(ReconciliationRun run, MatchResult mr,
                                           DeterministicReconciliationEngine.EngineResult er) {
        CandidateSet cs = er.candidateSet();
        MatchScore score = er.score();

        StringBuilder inputs = new StringBuilder();
        if (cs.gatewayTxn() != null) {
            inputs.append("GATEWAY txn_id=").append(cs.gatewayTxn().getId())
                  .append(" ref=").append(cs.gatewayTxn().getExternalRef())
                  .append(" amount=").append(cs.gatewayTxn().getAmount())
                  .append(" fee=").append(RawFieldExtractor.extractGatewayFee(cs.gatewayTxn()))
                  .append(" status=").append(RawFieldExtractor.extractStatus(cs.gatewayTxn()))
                  .append(" ts=").append(cs.gatewayTxn().getTimestamp()).append("; ");
        }
        cs.bankCandidates().forEach(b ->
            inputs.append("BANK txn_id=").append(b.getId())
                  .append(" ref=").append(b.getExternalRef())
                  .append(" amount=").append(b.getAmount())
                  .append(" ts=").append(b.getTimestamp()).append("; ")
        );
        cs.ledgerCandidates().forEach(l ->
            inputs.append("LEDGER txn_id=").append(l.getId())
                  .append(" ref=").append(l.getExternalRef())
                  .append(" amount=").append(l.getAmount())
                  .append(" ts=").append(l.getTimestamp()).append("; ")
        );

        AuditLogEntry audit = new AuditLogEntry();
        audit.setRun(run);
        audit.setMatchResult(mr);
        audit.setMethod(er.outcome().method());
        audit.setConfidence(score.getConfidence());
        audit.setInputsConsidered(inputs.toString());
        audit.setReasoning(
                "Score=" + score.getTotalScore() + " signals=[" + score.getExplanation() + "] " +
                "decision=" + er.outcome().status() + " reason=" + er.outcome().reasoning()
        );
        return audit;
    }
}
