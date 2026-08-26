package com.razorpay.buildathon.recon.ai.service;

import com.razorpay.buildathon.recon.ai.dto.EvaluationMetricsResponse;
import com.razorpay.buildathon.recon.model.MatchMethod;
import com.razorpay.buildathon.recon.model.MatchResult;
import com.razorpay.buildathon.recon.model.MatchStatus;
import com.razorpay.buildathon.recon.model.ReconciliationRun;
import com.razorpay.buildathon.recon.repository.MatchResultRepository;
import com.razorpay.buildathon.recon.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Service that calculates comparative metrics evaluating Phase 3 baseline
 * (deterministic engine) against Phase 4 (deterministic + AI reasoning agent).
 */
@Service
public class ReconciliationEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(ReconciliationEvaluationService.class);

    private final ReconciliationRunRepository runRepository;
    private final MatchResultRepository matchResultRepository;

    public ReconciliationEvaluationService(ReconciliationRunRepository runRepository,
                                           MatchResultRepository matchResultRepository) {
        this.runRepository = runRepository;
        this.matchResultRepository = matchResultRepository;
    }

    @Transactional(readOnly = true)
    public EvaluationMetricsResponse evaluateRun(Long runId) {
        ReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No such run: " + runId));

        List<MatchResult> matches = matchResultRepository.findByRunIdWithTxns(runId);
        int total = matches.size();

        // 1. Calculate Phase 3 Baseline (deterministic rules only: RULE_EXACT, RULE_HEURISTIC)
        long baselineReconciled = matches.stream()
                .filter(m -> m.getMethod() == MatchMethod.RULE_EXACT || m.getMethod() == MatchMethod.RULE_HEURISTIC)
                .filter(m -> m.getStatus() == MatchStatus.RECONCILED)
                .count();

        long baselineReview = matches.stream()
                .filter(m -> m.getMethod() == MatchMethod.RULE_EXACT || m.getMethod() == MatchMethod.RULE_HEURISTIC)
                .filter(m -> m.getStatus() == MatchStatus.REVIEW_REQUIRED)
                .count();

        // Items originally REVIEW_REQUIRED or UNRESOLVED under deterministic rules
        long baselineUnresolved = total - baselineReconciled;
        long baselineExceptions = total - (baselineReconciled + baselineReview);

        double baselineMatchRate = total > 0 ? (baselineReconciled * 100.0 / total) : 0.0;
        double baselineAutomationRate = total > 0 ? ((baselineReconciled + baselineReview) * 100.0 / total) : 0.0;

        EvaluationMetricsResponse.BaselineMetrics baseline = new EvaluationMetricsResponse.BaselineMetrics(
                (int) baselineReconciled,
                (int) baselineReview,
                (int) baselineExceptions,
                round(baselineMatchRate),
                round(baselineAutomationRate)
        );

        // 2. Calculate Phase 4 AI-Enhanced Current Metrics
        long currentReconciled = matches.stream().filter(m -> m.getStatus() == MatchStatus.RECONCILED).count();
        long currentReview = matches.stream().filter(m -> m.getStatus() == MatchStatus.REVIEW_REQUIRED).count();
        long currentExceptions = matches.stream().filter(m -> m.getStatus() == MatchStatus.EXCEPTION).count();
        long aiAssistedCount = matches.stream().filter(m -> m.getMethod() == MatchMethod.AI_ASSISTED).count();

        double currentMatchRate = total > 0 ? (currentReconciled * 100.0 / total) : 0.0;
        double currentAutomationRate = total > 0 ? ((currentReconciled + currentReview) * 100.0 / total) : 0.0;

        EvaluationMetricsResponse.AiEnhancedMetrics aiEnhanced = new EvaluationMetricsResponse.AiEnhancedMetrics(
                (int) currentReconciled,
                (int) currentReview,
                (int) currentExceptions,
                (int) aiAssistedCount,
                round(currentMatchRate),
                round(currentAutomationRate)
        );

        // 3. Compute Delta Statistics
        int additionalReconciled = (int) (currentReconciled - baselineReconciled);
        int reducedReview = (int) (baselineReview - currentReview);
        double matchRateDelta = currentMatchRate - baselineMatchRate;
        double automationRateDelta = currentAutomationRate - baselineAutomationRate;

        String summaryText;
        if (aiAssistedCount == 0) {
            summaryText = "AI reasoning agent has not yet been executed on this run.";
        } else if (additionalReconciled > 0) {
            summaryText = String.format(
                    "AI Exception Reasoning Agent successfully resolved %d ambiguous cases into RECONCILED status, improving overall match rate by +%.2f%% and reducing human review load.",
                    additionalReconciled, matchRateDelta);
        } else {
            summaryText = "AI Exception Reasoning Agent analyzed ambiguous cases and confirmed appropriate exception/review categorization.";
        }

        EvaluationMetricsResponse.DeltaMetrics delta = new EvaluationMetricsResponse.DeltaMetrics(
                additionalReconciled,
                reducedReview,
                round(matchRateDelta),
                round(automationRateDelta),
                summaryText
        );

        return new EvaluationMetricsResponse(run.getId(), total, baseline, aiEnhanced, delta, Instant.now());
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
