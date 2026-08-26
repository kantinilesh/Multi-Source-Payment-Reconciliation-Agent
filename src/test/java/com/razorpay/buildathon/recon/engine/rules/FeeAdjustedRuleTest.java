package com.razorpay.buildathon.recon.engine.rules;

import com.razorpay.buildathon.recon.config.ReconMatchingConfig;
import com.razorpay.buildathon.recon.engine.CandidateSet;
import com.razorpay.buildathon.recon.engine.MatchScore;
import com.razorpay.buildathon.recon.engine.MatchScorer;
import com.razorpay.buildathon.recon.engine.TxnBuilder;
import com.razorpay.buildathon.recon.model.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link FeeAdjustedRule}. */
class FeeAdjustedRuleTest {

    private FeeAdjustedRule rule;
    private MatchScorer scorer;
    private ReconMatchingConfig config;

    @BeforeEach
    void setup() {
        config = new ReconMatchingConfig(); // fee range: 0.5% – 4.0%
        scorer = new MatchScorer(config);
        rule = new FeeAdjustedRule(config);
    }

    @Test
    void gatewayMinusFeeEqualsBank_reconciledResult() {
        // Gateway=1000, fee=30 (3%), bank=970
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("30.00", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("970.00").build();
        var ledg = TxnBuilder.ledger("PAY-001").amount("1000.00").withStatus("PAID").build();
        var cs = new CandidateSet(gw, List.of(bank), List.of(ledg));

        var result = rule.evaluate(cs, scorer.score(cs));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(MatchStatus.RECONCILED);
        assertThat(result.get().reasoning()).contains("970");
    }

    @Test
    void feeOutsideToleranceBand_doesNotFire() {
        // Fee = 100 on 1000 = 10% — above 4% max
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("100.00", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("900.00").build();
        var cs = new CandidateSet(gw, List.of(bank), List.of());

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }

    @Test
    void zeroFee_doesNotFire() {
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("1000.00").build();
        var cs = new CandidateSet(gw, List.of(bank), List.of());

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }

    @Test
    void ledgerAmountMismatch_doesNotFire() {
        // Ledger should match gateway gross; if it doesn't, rule doesn't reconcile
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("30.00", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("970.00").build();
        var ledg = TxnBuilder.ledger("PAY-001").amount("500.00").withStatus("PAID").build(); // wrong
        var cs = new CandidateSet(gw, List.of(bank), List.of(ledg));

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }

    @Test
    void boundaryFee_lowerBound_fires() {
        // Fee = 0.5% of 1000 = 5.00 — exactly at lower bound
        var gw   = TxnBuilder.gateway("GW-001").amount("1000.00")
                .withFeeAndStatus("5.00", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").amount("995.00").build();
        var cs = new CandidateSet(gw, List.of(bank), List.of());

        assertThat(rule.evaluate(cs, scorer.score(cs))).isPresent();
    }
}
