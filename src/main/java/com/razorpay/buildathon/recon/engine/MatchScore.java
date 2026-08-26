package com.razorpay.buildathon.recon.engine;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.StringJoiner;

/**
 * Immutable value object that captures the detailed scoring breakdown for one
 * candidate match. Every field is populated by the deterministic engine before
 * a MatchResult is persisted, giving the audit trail full transparency.
 *
 * Design note: we avoid mutable builders here deliberately — a MatchScore is
 * computed once and never updated, so immutability is the right default.
 */
public final class MatchScore {

    /** Fired signals and the points each contributed. */
    private final Map<ScoreSignal, Integer> signalPoints;

    /** Sum of all signal points. */
    private final int totalScore;

    /** Human-readable explanation of why each signal did or did not fire. */
    private final String explanation;

    private MatchScore(Map<ScoreSignal, Integer> signalPoints, int totalScore, String explanation) {
        this.signalPoints = Collections.unmodifiableMap(new EnumMap<>(signalPoints));
        this.totalScore = totalScore;
        this.explanation = explanation;
    }

    public Map<ScoreSignal, Integer> getSignalPoints() { return signalPoints; }
    public int getTotalScore() { return totalScore; }
    public String getExplanation() { return explanation; }

    /** Normalise to [0.0, 1.0] relative to the theoretical maximum (110 pts). */
    public double getConfidence() {
        return Math.min(1.0, totalScore / 110.0);
    }

    public boolean hasFired(ScoreSignal signal) {
        return signalPoints.containsKey(signal) && signalPoints.get(signal) > 0;
    }

    // -------------------------------------------------------------------------
    // Builder
    // -------------------------------------------------------------------------

    public static Builder builder() { return new Builder(); }

    public static final class Builder {
        private final Map<ScoreSignal, Integer> signalPoints = new EnumMap<>(ScoreSignal.class);
        private final StringJoiner explanationJoiner = new StringJoiner("; ");

        public Builder addSignal(ScoreSignal signal, int points, String reason) {
            if (points > 0) {
                signalPoints.merge(signal, points, Integer::sum);
                explanationJoiner.add(signal.name() + "+" + points + "(" + reason + ")");
            } else {
                explanationJoiner.add(signal.name() + "+0(" + reason + ")");
            }
            return this;
        }

        public MatchScore build() {
            int total = signalPoints.values().stream().mapToInt(Integer::intValue).sum();
            return new MatchScore(signalPoints, total, explanationJoiner.toString());
        }
    }
}
