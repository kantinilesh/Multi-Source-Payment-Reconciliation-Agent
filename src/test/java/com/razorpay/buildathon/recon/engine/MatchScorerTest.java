package com.razorpay.buildathon.recon.engine;

import com.razorpay.buildathon.recon.config.ReconMatchingConfig;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link MatchScorer} — verifies per-signal scoring in isolation.
 */
class MatchScorerTest {

    private MatchScorer scorer;
    private ReconMatchingConfig config;

    @BeforeEach
    void setup() {
        config = new ReconMatchingConfig();
        scorer = new MatchScorer(config);
    }

    // -------------------------------------------------------------------------
    // EXACT_ID signal
    // -------------------------------------------------------------------------

    @Test
    void exactId_allThreeCoresMatch_fullWeight() {
        var gw   = TxnBuilder.gateway("GW-83921").withFeeAndStatus("30.00", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-83921").build();
        var ledg = TxnBuilder.ledger("PAY-83921").withStatus("PAID").build();

        var cs = new CandidateSet(gw, List.of(bank), List.of(ledg));
        MatchScore score = scorer.score(cs);

        assertThat(score.hasFired(ScoreSignal.EXACT_ID)).isTrue();
        assertThat(score.getSignalPoints().get(ScoreSignal.EXACT_ID))
                .isEqualTo(config.getScore().getExactIdWeight());
    }

    @Test
    void exactId_twoCoresMatch_halfWeight() {
        var gw   = TxnBuilder.gateway("GW-83921").withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-83921").build();
        var ledg = TxnBuilder.ledger("PAY-99999").withStatus("PAID").build(); // different core

        var cs = new CandidateSet(gw, List.of(bank), List.of(ledg));
        MatchScore score = scorer.score(cs);

        int half = config.getScore().getExactIdWeight() / 2;
        assertThat(score.getSignalPoints().getOrDefault(ScoreSignal.EXACT_ID, 0))
                .isEqualTo(half);
    }

    @Test
    void exactId_noMatch_zeroPoints() {
        var gw   = TxnBuilder.gateway("GW-11111").withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-22222").build();
        var ledg = TxnBuilder.ledger("PAY-33333").withStatus("PAID").build();

        var cs = new CandidateSet(gw, List.of(bank), List.of(ledg));
        MatchScore score = scorer.score(cs);

        assertThat(score.getSignalPoints().getOrDefault(ScoreSignal.EXACT_ID, 0)).isZero();
    }

    // -------------------------------------------------------------------------
    // FEE_ADJUSTED signal
    // -------------------------------------------------------------------------

    @Test
    void feeAdjusted_bankEqualsGatewayMinusFee_fullWeight() {
        // Gateway=1000, fee=30, bank should be 970
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("30.00", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("970.00").build();

        var cs = new CandidateSet(gw, List.of(bank), List.of());
        MatchScore score = scorer.score(cs);

        assertThat(score.hasFired(ScoreSignal.FEE_ADJUSTED)).isTrue();
        assertThat(score.getSignalPoints().get(ScoreSignal.FEE_ADJUSTED))
                .isEqualTo(config.getScore().getFeeAdjustedWeight());
    }

    @Test
    void feeAdjusted_bankAmountWrong_zeroPoints() {
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("30.00", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("500.00").build(); // clearly wrong

        var cs = new CandidateSet(gw, List.of(bank), List.of());
        MatchScore score = scorer.score(cs);

        assertThat(score.getSignalPoints().getOrDefault(ScoreSignal.FEE_ADJUSTED, 0)).isZero();
    }

    @Test
    void feeAdjusted_noFee_zeroPoints() {
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("1000.00").build();

        var cs = new CandidateSet(gw, List.of(bank), List.of());
        MatchScore score = scorer.score(cs);

        // Fee=0, signal should not fire
        assertThat(score.getSignalPoints().getOrDefault(ScoreSignal.FEE_ADJUSTED, 0)).isZero();
    }

    // -------------------------------------------------------------------------
    // TIMESTAMP_WINDOW signal
    // -------------------------------------------------------------------------

    @Test
    void timestampWindow_bankWithinLag_fullWeight() {
        var gw   = TxnBuilder.gateway("GW-001").timestamp("2024-01-15T10:00:00Z")
                .withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").timestamp("2024-01-17T10:00:00Z").build(); // 2 days later

        var cs = new CandidateSet(gw, List.of(bank), List.of());
        MatchScore score = scorer.score(cs);

        assertThat(score.hasFired(ScoreSignal.TIMESTAMP_WINDOW)).isTrue();
    }

    @Test
    void timestampWindow_bankOutsideLag_zeroPoints() {
        var gw   = TxnBuilder.gateway("GW-001").timestamp("2024-01-15T10:00:00Z")
                .withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").timestamp("2024-01-25T10:00:00Z").build(); // 10 days later

        var cs = new CandidateSet(gw, List.of(bank), List.of());
        MatchScore score = scorer.score(cs);

        assertThat(score.getSignalPoints().getOrDefault(ScoreSignal.TIMESTAMP_WINDOW, 0)).isZero();
    }

    @Test
    void timestampWindow_boundaryExactlyAtLag_included() {
        // Exactly 5 days = 432000 seconds — should be within window
        var gw   = TxnBuilder.gateway("GW-001").timestamp("2024-01-15T00:00:00Z")
                .withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").timestamp("2024-01-20T00:00:00Z").build(); // exactly 5d

        var cs = new CandidateSet(gw, List.of(bank), List.of());
        MatchScore score = scorer.score(cs);

        assertThat(score.hasFired(ScoreSignal.TIMESTAMP_WINDOW)).isTrue();
    }

    // -------------------------------------------------------------------------
    // STATUS_COMPAT signal
    // -------------------------------------------------------------------------

    @Test
    void statusCompat_successAndPaid_fullWeight() {
        var gw   = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "SUCCESS").build();
        var ledg = TxnBuilder.ledger("PAY-001").withStatus("PAID").build();

        var cs = new CandidateSet(gw, List.of(), List.of(ledg));
        MatchScore score = scorer.score(cs);

        assertThat(score.hasFired(ScoreSignal.STATUS_COMPAT)).isTrue();
    }

    @Test
    void statusCompat_successAndRefunded_zeroPoints() {
        var gw   = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "SUCCESS").build();
        var ledg = TxnBuilder.ledger("PAY-001").withStatus("REFUNDED").build();

        var cs = new CandidateSet(gw, List.of(), List.of(ledg));
        MatchScore score = scorer.score(cs);

        assertThat(score.getSignalPoints().getOrDefault(ScoreSignal.STATUS_COMPAT, 0)).isZero();
    }

    @Test
    void statusCompat_refundAndRefunded_fullWeight() {
        var gw   = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "REFUNDED").build();
        var ledg = TxnBuilder.ledger("PAY-001").withStatus("REFUNDED").build();

        var cs = new CandidateSet(gw, List.of(), List.of(ledg));
        MatchScore score = scorer.score(cs);

        assertThat(score.hasFired(ScoreSignal.STATUS_COMPAT)).isTrue();
    }

    // -------------------------------------------------------------------------
    // Total score
    // -------------------------------------------------------------------------

    @Test
    void totalScore_perfectMatch_maximumScore() {
        // All signals should fire for a perfect 3-way match
        var gw   = TxnBuilder.gateway("GW-83921").amount("1000.00")
                .timestamp("2024-01-15T10:00:00Z")
                .withFeeAndStatus("30.00", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-83921").amount("970.00")
                .timestamp("2024-01-17T00:00:00Z").build();
        var ledg = TxnBuilder.ledger("PAY-83921").amount("1000.00")
                .timestamp("2024-01-15T10:00:00Z").withStatus("PAID").build();

        var cs = new CandidateSet(gw, List.of(bank), List.of(ledg));
        MatchScore score = scorer.score(cs);

        // EXACT_ID (40) + REF_SIM (20) + FEE_ADJUSTED (15) + TIMESTAMP (10) + STATUS (5) = 90
        // AMOUNT_EXACT: bank=970 != gw=1000, ledger=1000 = gw → partial (10)
        // Total >= 70 (reconciled threshold)
        assertThat(score.getTotalScore()).isGreaterThanOrEqualTo(70);
        assertThat(score.getConfidence()).isBetween(0.0, 1.0);
    }

    @Test
    void explanation_containsAllSignals() {
        var gw   = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "SUCCESS").build();
        var cs   = new CandidateSet(gw, List.of(), List.of());
        MatchScore score = scorer.score(cs);

        // Explanation should mention every signal
        for (ScoreSignal signal : ScoreSignal.values()) {
            assertThat(score.getExplanation()).contains(signal.name());
        }
    }
}
