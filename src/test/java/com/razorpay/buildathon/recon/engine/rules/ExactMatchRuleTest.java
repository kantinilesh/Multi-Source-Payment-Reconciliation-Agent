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
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/** Unit tests for {@link ExactMatchRule}. */
class ExactMatchRuleTest {

    private ExactMatchRule rule;
    private MatchScorer scorer;
    private ReconMatchingConfig config;

    @BeforeEach
    void setup() {
        config = new ReconMatchingConfig();
        scorer = new MatchScorer(config);
        rule = new ExactMatchRule();
    }

    @Test
    void allThreeSources_sameCore_returnsReconciled() {
        var gw   = TxnBuilder.gateway("GW-83921").withFeeAndStatus("30.00", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-83921").amount("970.00").build();
        var ledg = TxnBuilder.ledger("PAY-83921").withStatus("PAID").build();
        var cs   = new CandidateSet(gw, List.of(bank), List.of(ledg));
        MatchScore score = scorer.score(cs);

        Optional<ReconciliationRule.RuleOutcome> result = rule.evaluate(cs, score);

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(MatchStatus.RECONCILED);
    }

    @Test
    void missingBank_doesNotFire() {
        var gw   = TxnBuilder.gateway("GW-83921").withFeeAndStatus("0", "SUCCESS").build();
        var ledg = TxnBuilder.ledger("PAY-83921").withStatus("PAID").build();
        var cs   = new CandidateSet(gw, List.of(), List.of(ledg));
        MatchScore score = scorer.score(cs);

        assertThat(rule.evaluate(cs, score)).isEmpty();
    }

    @Test
    void missingLedger_doesNotFire() {
        var gw   = TxnBuilder.gateway("GW-83921").withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-83921").build();
        var cs   = new CandidateSet(gw, List.of(bank), List.of());
        MatchScore score = scorer.score(cs);

        assertThat(rule.evaluate(cs, score)).isEmpty();
    }

    @Test
    void differentCores_doesNotFire() {
        var gw   = TxnBuilder.gateway("GW-11111").withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-22222").build();
        var ledg = TxnBuilder.ledger("PAY-33333").withStatus("PAID").build();
        var cs   = new CandidateSet(gw, List.of(bank), List.of(ledg));
        MatchScore score = scorer.score(cs);

        assertThat(rule.evaluate(cs, score)).isEmpty();
    }
}
