package com.razorpay.buildathon.recon.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Type-safe binding for all {@code recon.matching.*} properties defined in
 * {@code application.yml}. Injected into the engine and its sub-components so
 * that every tuning knob is configurable without recompilation.
 */
@ConfigurationProperties(prefix = "recon.matching")
public class ReconMatchingConfig {

    /** Gateway fee as % of gross amount — lower bound of the acceptable fee range. */
    private double feeTolerancePctMin = 0.5;

    /** Gateway fee as % of gross amount — upper bound of the acceptable fee range. */
    private double feeTolerancePctMax = 4.0;

    /** Max calendar days between gateway timestamp and bank settlement date. */
    private int settlementLagDaysMax = 5;

    /** Minimum reference similarity score to count as a "hint" match. */
    private double idSimilarityThreshold = 0.6;

    /** Score threshold: score >= reconciledThreshold → RECONCILED. */
    private int reconciledThreshold = 70;

    /** Score threshold: reconciledThreshold > score >= reviewThreshold → REVIEW_REQUIRED. */
    private int reviewThreshold = 40;

    private Score score = new Score();

    // --- Getters / setters (Lombok not used here — ConfigurationProperties needs them) ---

    public double getFeeTolerancePctMin() { return feeTolerancePctMin; }
    public void setFeeTolerancePctMin(double v) { this.feeTolerancePctMin = v; }

    public double getFeeTolerancePctMax() { return feeTolerancePctMax; }
    public void setFeeTolerancePctMax(double v) { this.feeTolerancePctMax = v; }

    public int getSettlementLagDaysMax() { return settlementLagDaysMax; }
    public void setSettlementLagDaysMax(int v) { this.settlementLagDaysMax = v; }

    public double getIdSimilarityThreshold() { return idSimilarityThreshold; }
    public void setIdSimilarityThreshold(double v) { this.idSimilarityThreshold = v; }

    public int getReconciledThreshold() { return reconciledThreshold; }
    public void setReconciledThreshold(int v) { this.reconciledThreshold = v; }

    public int getReviewThreshold() { return reviewThreshold; }
    public void setReviewThreshold(int v) { this.reviewThreshold = v; }

    public Score getScore() { return score; }
    public void setScore(Score s) { this.score = s; }

    /**
     * Per-signal scoring weights. The maximum achievable score is the sum of all
     * weights when all signals fire simultaneously.
     */
    public static class Score {
        private int exactIdWeight = 40;
        private int refSimilarityWeight = 20;
        private int amountExactWeight = 20;
        private int feeAdjustedWeight = 15;
        private int timestampWeight = 10;
        private int statusCompatWeight = 5;

        public int getExactIdWeight() { return exactIdWeight; }
        public void setExactIdWeight(int v) { this.exactIdWeight = v; }

        public int getRefSimilarityWeight() { return refSimilarityWeight; }
        public void setRefSimilarityWeight(int v) { this.refSimilarityWeight = v; }

        public int getAmountExactWeight() { return amountExactWeight; }
        public void setAmountExactWeight(int v) { this.amountExactWeight = v; }

        public int getFeeAdjustedWeight() { return feeAdjustedWeight; }
        public void setFeeAdjustedWeight(int v) { this.feeAdjustedWeight = v; }

        public int getTimestampWeight() { return timestampWeight; }
        public void setTimestampWeight(int v) { this.timestampWeight = v; }

        public int getStatusCompatWeight() { return statusCompatWeight; }
        public void setStatusCompatWeight(int v) { this.statusCompatWeight = v; }
    }
}
