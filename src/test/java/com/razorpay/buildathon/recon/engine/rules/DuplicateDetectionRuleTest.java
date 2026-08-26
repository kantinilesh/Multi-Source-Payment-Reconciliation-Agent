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

/** Unit tests for {@link DuplicateDetectionRule}. */
class DuplicateDetectionRuleTest {

    private DuplicateDetectionRule rule;
    private MatchScorer scorer;

    @BeforeEach
    void setup() {
        var config = new ReconMatchingConfig();
        scorer = new MatchScorer(config);
        rule = new DuplicateDetectionRule();
    }

    @Test
    void twoBankCandidates_exceptionDuplicateDetected() {
        var gw    = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "SUCCESS").build();
        var bank1 = TxnBuilder.bank("SET-001A").build();
        var bank2 = TxnBuilder.bank("SET-001B").build();
        var ledg  = TxnBuilder.ledger("PAY-001").withStatus("PAID").build();
        var cs = new CandidateSet(gw, List.of(bank1, bank2), List.of(ledg));

        var result = rule.evaluate(cs, scorer.score(cs));

        assertThat(result).isPresent();
        assertThat(result.get().status()).isEqualTo(MatchStatus.EXCEPTION);
        assertThat(result.get().exceptionCategory()).isEqualTo(ExceptionCategory.DUPLICATE_DETECTED);
        assertThat(result.get().reasoning()).contains("2 bank candidates");
    }

    @Test
    void twoLedgerCandidates_exceptionDuplicateDetected() {
        var gw    = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "SUCCESS").build();
        var bank  = TxnBuilder.bank("SET-001").build();
        var ledg1 = TxnBuilder.ledger("PAY-001A").withStatus("PAID").build();
        var ledg2 = TxnBuilder.ledger("PAY-001B").withStatus("PAID").build();
        var cs = new CandidateSet(gw, List.of(bank), List.of(ledg1, ledg2));

        var result = rule.evaluate(cs, scorer.score(cs));

        assertThat(result).isPresent();
        assertThat(result.get().exceptionCategory()).isEqualTo(ExceptionCategory.DUPLICATE_DETECTED);
    }

    @Test
    void singleCandidatesEach_doesNotFire() {
        var gw   = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "SUCCESS").build();
        var bank = TxnBuilder.bank("SET-001").build();
        var ledg = TxnBuilder.ledger("PAY-001").withStatus("PAID").build();
        var cs = new CandidateSet(gw, List.of(bank), List.of(ledg));

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }

    @Test
    void emptyLists_doesNotFire() {
        var gw = TxnBuilder.gateway("GW-001").withFeeAndStatus("0", "SUCCESS").build();
        var cs = new CandidateSet(gw, List.of(), List.of());

        assertThat(rule.evaluate(cs, scorer.score(cs))).isEmpty();
    }
}
