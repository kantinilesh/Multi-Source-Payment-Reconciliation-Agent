package com.razorpay.buildathon.recon.ai.dto;

import java.time.Instant;

/**
 * Comparative evaluation response comparing Phase 3 (baseline deterministic engine)
 * with Phase 4 (deterministic + AI reasoning agent).
 */
public record EvaluationMetricsResponse(
        Long runId,
        int totalTransactions,

        // Phase 3 Baseline (Deterministic Only)
        BaselineMetrics baseline,

        // Phase 4 AI-Enhanced
        AiEnhancedMetrics aiEnhanced,

        // Delta / Improvement Stats
        DeltaMetrics delta,

        Instant evaluatedAt
) {
    public record BaselineMetrics(
            int reconciledCount,
            int reviewRequiredCount,
            int exceptionCount,
            double matchRatePct,
            double automationRatePct
    ) {}

    public record AiEnhancedMetrics(
            int reconciledCount,
            int reviewRequiredCount,
            int exceptionCount,
            int aiAssistedCount,
            double matchRatePct,
            double automationRatePct
    ) {}

    public record DeltaMetrics(
            int additionalReconciled,
            int reducedReviewRequired,
            double matchRateImprovementPct,
            double automationRateImprovementPct,
            String summary
    ) {}
}
