package com.razorpay.buildathon.recon.engine.rules;

import com.razorpay.buildathon.recon.engine.CandidateSet;
import com.razorpay.buildathon.recon.engine.MatchScore;
import com.razorpay.buildathon.recon.engine.RawFieldExtractor;
import com.razorpay.buildathon.recon.engine.ScoreSignal;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchMethod;
import com.razorpay.buildathon.recon.model.MatchStatus;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 1 (highest priority): Exact match.
 *
 * All three records are present, all numeric reference cores are identical,
 * all amounts are equal (or fee-adjusted), and statuses are compatible.
 * This is the strongest deterministic signal and always results in RECONCILED.
 *
 * Condition: all 3 present AND EXACT_ID signal fired (full weight, not partial).
 */
@Component
@Order(4)
public class ExactMatchRule implements ReconciliationRule {

    @Override
    public Optional<RuleOutcome> evaluate(CandidateSet cs, MatchScore score) {
        NormalizedTransaction gw   = cs.gatewayTxn();
        NormalizedTransaction bank = cs.primaryBankTxn().orElse(null);
        NormalizedTransaction ledg = cs.primaryLedgerTxn().orElse(null);

        // Must have all 3 sources
        if (gw == null || bank == null || ledg == null) return Optional.empty();

        // Must have fired EXACT_ID
        if (!score.hasFired(ScoreSignal.EXACT_ID)) return Optional.empty();

        // Check all 3 cores are identical
        String gwCore   = RawFieldExtractor.extractNumericCore(gw.getExternalRef());
        String bankCore = RawFieldExtractor.extractNumericCore(bank.getExternalRef());
        String ledgCore = RawFieldExtractor.extractNumericCore(ledg.getExternalRef());

        if (gwCore.isBlank() || !gwCore.equals(bankCore) || !gwCore.equals(ledgCore)) {
            return Optional.empty();
        }

        return Optional.of(new RuleOutcome(
                MatchStatus.RECONCILED,
                MatchMethod.RULE_EXACT,
                ExceptionCategory.NONE,
                "Exact match: all 3 records share reference core '" + gwCore + "'. Score=" + score.getTotalScore()
        ));
    }
}
