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

/** Unit tests for {@link RefundRule}. */
class RefundRuleTest {

    private RefundRule rule;
    private MatchScorer scorer;

    @BeforeEach
    void setup() {
        var config = new ReconMatchingConfig();
        scorer = new MatchScorer(config);
        rule = new RefundRule();
    }

    @Test
    void bothRefunded_reconciledRefund() {
        var gw   = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "REFUNDED").build();
        var ledg = TxnBuilder.ledger("PAY-001").withStatus("REFUNDED").build();
        var cs   = new CandidateSet(gw, List.of(), List.of(ledg));

        var result = rule.evaluate(cs, scorer.score(cs));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(MatchStatus.RECONCILED);
    }

    @Test
    void gatewaySuccessLedgerRefunded_exceptionRefundMismatch() {
        var gw   = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "SUCCESS").build();
        var ledg = TxnBuilder.ledger("PAY-001").withStatus("REFUNDED").build();
        var cs   = new CandidateSet(gw, List.of(), List.of(ledg));

        var result = rule.evaluate(cs, scorer.score(cs));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(MatchStatus.EXCEPTION);
        assertThat(result.get().exceptionCategory()).isEqualTo(ExceptionCategory.REFUND_MISMATCH);
    }

    @Test
    void gatewayRefundedLedgerSuccess_exceptionRefundMismatch() {
        var gw   = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "REFUNDED").build();
        var ledg = TxnBuilder.ledger("PAY-001").withStatus("PAID").build();
        var cs   = new CandidateSet(gw, List.of(), List.of(ledg));

        var result = rule.evaluate(cs, scorer.score(cs));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(MatchStatus.EXCEPTION);
        assertThat(result.get().exceptionCategory()).isEqualTo(ExceptionCategory.REFUND_MISMATCH);
    }

    @Test
    void bothSuccess_doesNotFire() {
        var gw   = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "SUCCESS").build();
        var ledg = TxnBuilder.ledger("PAY-001").withStatus("PAID").build();
        var cs   = new CandidateSet(gw, List.of(), List.of(ledg));

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }

    @Test
    void noGateway_doesNotFire() {
        var bank = TxnBuilder.bank("SET-001").build();
        var cs = new CandidateSet(null, List.of(bank), List.of());

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }
}
