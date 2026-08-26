package com.razorpay.buildathon.recon.controller;

import com.razorpay.buildathon.recon.ai.dto.EvaluationMetricsResponse;
import com.razorpay.buildathon.recon.ai.service.AiExceptionReasoningService;
import com.razorpay.buildathon.recon.ai.service.ReconciliationEvaluationService;
import com.razorpay.buildathon.recon.dto.*;
import com.razorpay.buildathon.recon.model.MatchResult;
import com.razorpay.buildathon.recon.model.MatchStatus;
import com.razorpay.buildathon.recon.model.ReconciliationRun;
import com.razorpay.buildathon.recon.repository.AuditLogEntryRepository;
import com.razorpay.buildathon.recon.repository.MatchResultRepository;
import com.razorpay.buildathon.recon.service.DeterministicReconciliationService;
import com.razorpay.buildathon.recon.service.ReconciliationRunService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

/**
 * REST controller for all reconciliation run operations across Phase 2, Phase 3, and Phase 4.
 */
@RestController
@RequestMapping("/api/runs")
public class ReconciliationRunController {

    private final ReconciliationRunService          runService;
    private final DeterministicReconciliationService reconService;
    private final MatchResultRepository             matchResultRepository;
    private final AuditLogEntryRepository           auditLogEntryRepository;
    private final AiExceptionReasoningService       aiReasoningService;
    private final ReconciliationEvaluationService   evaluationService;

    public ReconciliationRunController(
            ReconciliationRunService runService,
            DeterministicReconciliationService reconService,
            MatchResultRepository matchResultRepository,
            AuditLogEntryRepository auditLogEntryRepository,
            AiExceptionReasoningService aiReasoningService,
            ReconciliationEvaluationService evaluationService) {
        this.runService = runService;
        this.reconService = reconService;
        this.matchResultRepository = matchResultRepository;
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.aiReasoningService = aiReasoningService;
        this.evaluationService = evaluationService;
    }

    // =========================================================================
    // Phase 2: upload + status + transactions
    // =========================================================================

    /**
     * Upload the three source files for one reconciliation run.
     * Expects multipart/form-data with parts: gatewayFile, bankFile, ledgerFile.
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<RunUploadResponse> uploadRun(
            @RequestParam("gatewayFile") MultipartFile gatewayFile,
            @RequestParam("bankFile")    MultipartFile bankFile,
            @RequestParam("ledgerFile")  MultipartFile ledgerFile) {

        RunUploadResponse response = runService.createRunFromUpload(gatewayFile, bankFile, ledgerFile);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{runId}")
    public ResponseEntity<RunStatusResponse> getRun(@PathVariable Long runId) {
        return ResponseEntity.ok(RunStatusResponse.from(runService.getRun(runId)));
    }

    @GetMapping("/{runId}/transactions")
    public ResponseEntity<List<NormalizedTransactionResponse>> getTransactions(
            @PathVariable Long runId) {
        List<NormalizedTransactionResponse> txns = runService.getTransactionsForRun(runId).stream()
                .map(NormalizedTransactionResponse::from)
                .toList();
        return ResponseEntity.ok(txns);
    }

    // =========================================================================
    // Phase 3 & Phase 4: reconciliation trigger + result inspection
    // =========================================================================

    /**
     * Trigger the full reconciliation pipeline (Phase 3 deterministic + Phase 4 AI reasoning).
     * Safe to call multiple times — idempotent.
     *
     * POST /api/runs/{runId}/reconcile
     */
    @PostMapping("/{runId}/reconcile")
    public ResponseEntity<RunStatusResponse> triggerReconciliation(@PathVariable Long runId) {
        ReconciliationRun run = reconService.reconcile(runId);
        return ResponseEntity.ok(RunStatusResponse.from(run));
    }

    /**
     * All MatchResult rows for a run (reconciled, review-required, and exceptions).
     *
     * GET /api/runs/{runId}/matches
     */
    @GetMapping("/{runId}/matches")
    public ResponseEntity<List<MatchResultResponse>> getMatches(@PathVariable Long runId) {
        List<MatchResultResponse> matches = matchResultRepository.findByRunId(runId).stream()
                .map(MatchResultResponse::from)
                .toList();
        return ResponseEntity.ok(matches);
    }

    /**
     * Only EXCEPTION-status MatchResult rows for a run.
     *
     * GET /api/runs/{runId}/exceptions
     */
    @GetMapping("/{runId}/exceptions")
    public ResponseEntity<List<MatchResultResponse>> getExceptions(@PathVariable Long runId) {
        List<MatchResultResponse> exceptions =
                matchResultRepository.findByRunIdAndStatus(runId, MatchStatus.EXCEPTION).stream()
                        .map(MatchResultResponse::from)
                        .toList();
        return ResponseEntity.ok(exceptions);
    }

    /**
     * Aggregated reconciliation summary metrics for a run.
     *
     * GET /api/runs/{runId}/summary
     */
    @GetMapping("/{runId}/summary")
    public ResponseEntity<ReconciliationSummaryResponse> getSummary(@PathVariable Long runId) {
        ReconciliationRun run = runService.getRun(runId);
        int total = (int) matchResultRepository.findByRunId(runId).size();
        return ResponseEntity.ok(ReconciliationSummaryResponse.from(run, total));
    }

    /**
     * Full audit trail for a run — showing deterministic rules + AI agent decisions.
     *
     * GET /api/runs/{runId}/audit
     */
    @GetMapping("/{runId}/audit")
    public ResponseEntity<List<AuditLogEntryResponse>> getAuditLog(@PathVariable Long runId) {
        List<AuditLogEntryResponse> entries =
                auditLogEntryRepository.findByRunIdOrderByCreatedAtAsc(runId).stream()
                        .map(AuditLogEntryResponse::from)
                        .toList();
        return ResponseEntity.ok(entries);
    }

    // =========================================================================
    // Phase 4: AI Exception Reasoning & Comparative Evaluation
    // =========================================================================

    /**
     * Trigger on-demand AI Exception Reasoning for a specific MatchResult.
     *
     * POST /api/runs/{runId}/matches/{matchId}/ai-explain
     */
    @PostMapping("/{runId}/matches/{matchId}/ai-explain")
    public ResponseEntity<MatchResultResponse> explainMatchWithAi(
            @PathVariable Long runId,
            @PathVariable Long matchId) {
        MatchResult mr = matchResultRepository.findById(matchId)
                .orElseThrow(() -> new IllegalArgumentException("No such match result: " + matchId));

        MatchResult updated = aiReasoningService.analyzeSingleMatch(mr, null);
        matchResultRepository.save(updated);

        return ResponseEntity.ok(MatchResultResponse.from(updated));
    }

    /**
     * Fetch comparative evaluation metrics comparing Phase 3 baseline vs Phase 4 AI-enhanced performance.
     *
     * GET /api/runs/{runId}/evaluation
     */
    @GetMapping("/{runId}/evaluation")
    public ResponseEntity<EvaluationMetricsResponse> getEvaluationMetrics(@PathVariable Long runId) {
        EvaluationMetricsResponse metrics = evaluationService.evaluateRun(runId);
        return ResponseEntity.ok(metrics);
    }
}
