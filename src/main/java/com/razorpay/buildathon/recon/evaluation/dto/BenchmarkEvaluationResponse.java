package com.razorpay.buildathon.recon.evaluation.dto;

import java.time.Instant;
import java.util.List;

/**
 * Machine-readable and human-readable benchmark evaluation response object.
 */
public record BenchmarkEvaluationResponse(
        Long runId,
        Instant evaluatedAt,

        // Aggregate Metrics for Current System (Rules + AI)
        AggregateMetrics systemMetrics,

        // Baseline Metrics for Rules-Only Pass
        AggregateMetrics baselineMetrics,

        // Comparative Delta Metrics (Rules vs Rules+AI)
        ComparisonDelta comparisonDelta,

        // Per-Case Evaluation Results
        List<CaseEvaluationResult> caseBreakdown,

        // Formatted Markdown Report
        String humanReadableReport
) {
    public record AggregateMetrics(
            int totalTransactions,
            int correctlyReconciled,
            int incorrectlyReconciled,
            int correctExceptions,
            int incorrectExceptions,
            int falsePositives,
            int falseNegatives,
            double matchRatePct,
            double exceptionRatePct,
            double humanReviewRatePct,
            double automationRatePct,
            double exceptionCategoryAccuracyPct,
            long processingTimeMs,
            double throughputPerSec
    ) {}

    public record ComparisonDelta(
            int additionalReconciled,
            int reducedReviewRequired,
            int reducedFalsePositives,
            int reducedFalseNegatives,
            double matchRateImprovementPct,
            double automationRateImprovementPct,
            String executiveSummary
    ) {}
}
