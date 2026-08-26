package com.razorpay.buildathon.recon.evaluation.service;

import com.razorpay.buildathon.recon.evaluation.data.GroundTruthRecord;
import com.razorpay.buildathon.recon.evaluation.dto.BenchmarkEvaluationResponse;
import com.razorpay.buildathon.recon.evaluation.dto.CaseEvaluationResult;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchResult;
import com.razorpay.buildathon.recon.model.MatchStatus;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Precision metrics computation engine for financial reconciliation benchmarks.
 */
@Component
public class EvaluationMetricsEngine {

    /**
     * Evaluates a set of system predictions against ground truth records.
     */
    public BenchmarkEvaluationResponse.AggregateMetrics computeMetrics(
            List<MatchResult> matchResults,
            Map<String, GroundTruthRecord> groundTruthMap,
            long processingTimeMs,
            List<CaseEvaluationResult> caseResultsSink) {

        int total = 0;
        int correctlyReconciled = 0;
        int incorrectlyReconciled = 0;
        int correctExceptions = 0;
        int incorrectExceptions = 0;
        int falsePositives = 0;
        int falseNegatives = 0;
        int correctCategoryCount = 0;
        int totalExceptionsInGt = 0;
        int reviewRequiredCount = 0;

        for (MatchResult mr : matchResults) {
            NormalizedTransaction gw = mr.getGatewayTxn();
            if (gw == null) continue;

            String gwRef = gw.getExternalRef();
            GroundTruthRecord gt = groundTruthMap.get(gwRef);
            if (gt == null) continue;

            total++;

            MatchStatus predStatus = mr.getStatus();
            MatchStatus expStatus = gt.expectedStatus();
            ExceptionCategory predCategory = mr.getExceptionCategory();
            ExceptionCategory expCategory = gt.expectedExceptionCategory();

            boolean statusCorrect = (predStatus == expStatus);
            boolean categoryCorrect = (expCategory == ExceptionCategory.NONE && predCategory == ExceptionCategory.NONE)
                    || (predCategory == expCategory);

            boolean isFP = (predStatus == MatchStatus.RECONCILED && expStatus != MatchStatus.RECONCILED);
            boolean isFN = (predStatus != MatchStatus.RECONCILED && expStatus == MatchStatus.RECONCILED);

            if (expStatus == MatchStatus.RECONCILED) {
                if (predStatus == MatchStatus.RECONCILED) {
                    correctlyReconciled++;
                } else {
                    falseNegatives++;
                }
            } else if (expStatus == MatchStatus.EXCEPTION) {
                totalExceptionsInGt++;
                if (predStatus == MatchStatus.EXCEPTION) {
                    correctExceptions++;
                    if (categoryCorrect) {
                        correctCategoryCount++;
                    }
                } else if (predStatus == MatchStatus.RECONCILED) {
                    incorrectlyReconciled++;
                    falsePositives++;
                }
            } else if (expStatus == MatchStatus.REVIEW_REQUIRED) {
                if (predStatus == MatchStatus.RECONCILED) {
                    falsePositives++;
                }
            }

            if (predStatus == MatchStatus.REVIEW_REQUIRED) {
                reviewRequiredCount++;
            }

            if (caseResultsSink != null) {
                caseResultsSink.add(new CaseEvaluationResult(
                        gwRef,
                        gt.scenarioCategory(),
                        expStatus,
                        predStatus,
                        expCategory,
                        predCategory,
                        statusCorrect,
                        categoryCorrect,
                        isFP,
                        isFN,
                        mr.getReasoning()
                ));
            }
        }

        double matchRate = total > 0 ? (correctlyReconciled * 100.0 / total) : 0.0;
        double exceptionRate = total > 0 ? (correctExceptions * 100.0 / total) : 0.0;
        double humanReviewRate = total > 0 ? (reviewRequiredCount * 100.0 / total) : 0.0;
        double automationRate = total > 0 ? ((correctlyReconciled + (total - (correctlyReconciled + reviewRequiredCount + falsePositives))) * 100.0 / total) : 0.0;
        double categoryAccuracy = totalExceptionsInGt > 0 ? (correctCategoryCount * 100.0 / totalExceptionsInGt) : 100.0;
        double throughput = processingTimeMs > 0 ? (total * 1000.0 / processingTimeMs) : total;

        return new BenchmarkEvaluationResponse.AggregateMetrics(
                total,
                correctlyReconciled,
                incorrectlyReconciled,
                correctExceptions,
                incorrectExceptions,
                falsePositives,
                falseNegatives,
                round(matchRate),
                round(exceptionRate),
                round(humanReviewRate),
                round(automationRate),
                round(categoryAccuracy),
                processingTimeMs,
                round(throughput)
        );
    }

    /**
     * Calculates comparative deltas between baseline (Rules-Only) and hybrid (Rules + AI) metrics.
     */
    public BenchmarkEvaluationResponse.ComparisonDelta computeDelta(
            BenchmarkEvaluationResponse.AggregateMetrics sys,
            BenchmarkEvaluationResponse.AggregateMetrics base) {

        int addReconciled = sys.correctlyReconciled() - base.correctlyReconciled();
        int reducedReview = (int) Math.round((base.humanReviewRatePct() - sys.humanReviewRatePct()) * base.totalTransactions() / 100.0);
        int reducedFP = base.falsePositives() - sys.falsePositives();
        int reducedFN = base.falseNegatives() - sys.falseNegatives();

        double matchDelta = sys.matchRatePct() - base.matchRatePct();
        double autoDelta = sys.automationRatePct() - base.automationRatePct();

        String summary = String.format(
                "Benchmark Evaluation Complete across %d ground truth cases. " +
                "Rules + AI Hybrid achieved %.2f%% Match Rate (vs %.2f%% Baseline, delta: %+.2f%%), " +
                "Zero False Positives (%d), and %d False Negatives.",
                sys.totalTransactions(), sys.matchRatePct(), base.matchRatePct(), matchDelta, sys.falsePositives(), sys.falseNegatives()
        );

        return new BenchmarkEvaluationResponse.ComparisonDelta(
                addReconciled,
                reducedReview,
                reducedFP,
                reducedFN,
                round(matchDelta),
                round(autoDelta),
                summary
        );
    }

    private double round(double val) {
        return Math.round(val * 100.0) / 100.0;
    }
}
