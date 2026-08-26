package com.razorpay.buildathon.recon.engine.rules;

import com.razorpay.buildathon.recon.config.ReconMatchingConfig;
import com.razorpay.buildathon.recon.engine.CandidateSet;
import com.razorpay.buildathon.recon.engine.MatchScorer;
import com.razorpay.buildathon.recon.engine.TxnBuilder;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link AmountMismatchRule}. */
class AmountMismatchRuleTest {

    private AmountMismatchRule rule;
    private MatchScorer scorer;
    private ReconMatchingConfig config;

    @BeforeEach
    void setup() {
        config = new ReconMatchingConfig();
        scorer = new MatchScorer(config);
        rule = new AmountMismatchRule(config);
    }

    @Test
    void largeMismatch_noFeeExplanation_exception() {
        // Gateway=2000, bank=1500 — gap of 500, no fee to explain it
        var gw   = TxnBuilder.gateway("GW-001").amount("2000.00")
                .withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("1500.00").build();
        var cs   = new CandidateSet(gw, List.of(bank), List.of());

        var result = rule.evaluate(cs, scorer.score(cs));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(MatchStatus.EXCEPTION);
        assertThat(result.get().exceptionCategory())
                .isEqualTo(ExceptionCategory.AMOUNT_MISMATCH_BEYOND_TOLERANCE);
        assertThat(result.get().reasoning()).contains("2000");
        assertThat(result.get().reasoning()).contains("1500");
    }

    @Test
    void exactAmountMatch_doesNotFire() {
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("1000.00").build();
        var cs   = new CandidateSet(gw, List.of(bank), List.of());

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }

    @Test
    void feeExplainsGap_doesNotFire() {
        // Gateway=1000, fee=30, bank=970 — fee explains the difference
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("30.00", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("970.00").build();
        var cs   = new CandidateSet(gw, List.of(bank), List.of());

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }

    @Test
    void smallDiffWithinFeeBand_doesNotFire() {
        // Gateway=1000, bank=990 — 1% difference is within fee band
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("990.00").build();
        var cs   = new CandidateSet(gw, List.of(bank), List.of());

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }

    @Test
    void noBank_doesNotFire() {
        var gw = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "SUCCESS").build();
        var cs = new CandidateSet(gw, List.of(), List.of());

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }
}
