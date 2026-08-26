package com.razorpay.buildathon.recon.evaluation.dto;

import org.springframework.stereotype.Component;

/**
 * Formatter component that converts benchmark evaluation metrics into a human-readable Markdown report.
 */
@Component
public class BenchmarkReportGenerator {

    public String generateMarkdownReport(BenchmarkEvaluationResponse response) {
        BenchmarkEvaluationResponse.AggregateMetrics sys = response.systemMetrics();
        BenchmarkEvaluationResponse.AggregateMetrics base = response.baselineMetrics();
        BenchmarkEvaluationResponse.ComparisonDelta delta = response.comparisonDelta();

        StringBuilder sb = new StringBuilder();
        sb.append("# AI Finance Controller — Benchmark Evaluation Report\n\n");
        sb.append("**Run ID**: ").append(response.runId()).append("  \n");
        sb.append("**Evaluated At**: ").append(response.evaluatedAt()).append("  \n");
        sb.append("**Total Benchmark Transactions**: ").append(sys.totalTransactions()).append("\n\n");

        sb.append("--- \n\n");
        sb.append("## Executive Summary\n\n");
        sb.append(delta.executiveSummary()).append("\n\n");

        sb.append("--- \n\n");
        sb.append("## Comparative Benchmark Performance\n\n");
        sb.append("| Metric | Rules Only (Baseline) | Rules + AI (Hybrid) | Delta / Improvement |\n");
        sb.append("|---|---|---|---|\n");
        sb.append(String.format("| **Correctly Reconciled** | %d | %d | %+d |\n", base.correctlyReconciled(), sys.correctlyReconciled(), delta.additionalReconciled()));
        sb.append(String.format("| **False Positives** | %d | %d | %+d |\n", base.falsePositives(), sys.falsePositives(), -delta.reducedFalsePositives()));
        sb.append(String.format("| **False Negatives** | %d | %d | %+d |\n", base.falseNegatives(), sys.falseNegatives(), -delta.reducedFalseNegatives()));
        sb.append(String.format("| **Match Rate %%** | %.2f%% | %.2f%% | %+.2f%% |\n", base.matchRatePct(), sys.matchRatePct(), delta.matchRateImprovementPct()));
        sb.append(String.format("| **Automation Rate %%** | %.2f%% | %.2f%% | %+.2f%% |\n", base.automationRatePct(), sys.automationRatePct(), delta.automationRateImprovementPct()));
        sb.append(String.format("| **Exception Category Accuracy %%** | %.2f%% | %.2f%% | %+.2f%% |\n", base.exceptionCategoryAccuracyPct(), sys.exceptionCategoryAccuracyPct(), sys.exceptionCategoryAccuracyPct() - base.exceptionCategoryAccuracyPct()));
        sb.append(String.format("| **Processing Time** | %d ms | %d ms | — |\n", base.processingTimeMs(), sys.processingTimeMs()));
        sb.append(String.format("| **Throughput** | %.1f txns/sec | %.1f txns/sec | — |\n\n", base.throughputPerSec(), sys.throughputPerSec()));

        sb.append("--- \n\n");
        sb.append("## Metric Definitions\n\n");
        sb.append("- **Correctly Reconciled (TP)**: System predicted RECONCILED matching Ground Truth RECONCILED.\n");
        sb.append("- **False Positive**: System predicted RECONCILED when Ground Truth was EXCEPTION or REVIEW_REQUIRED.\n");
        sb.append("- **False Negative**: System predicted EXCEPTION or REVIEW_REQUIRED when Ground Truth was RECONCILED.\n");
        sb.append("- **Automation Rate**: Percentage of transactions processed automatically without requiring manual human review.\n");
        sb.append("- **Exception Category Accuracy**: Percentage of exception cases where the system correctly identified the exact financial root cause.\n\n");

        return sb.toString();
    }
}
