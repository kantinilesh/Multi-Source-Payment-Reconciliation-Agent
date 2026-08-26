package com.razorpay.buildathon.recon.engine.rules;

import com.razorpay.buildathon.recon.engine.CandidateSet;
import com.razorpay.buildathon.recon.engine.MatchScore;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchMethod;
import com.razorpay.buildathon.recon.model.MatchStatus;

import java.util.Optional;

/**
 * A single deterministic reconciliation rule.
 *
 * Rules are evaluated in priority order by the engine. The FIRST rule that
 * produces a conclusive result (non-empty Optional) wins; subsequent rules are
 * not evaluated for that CandidateSet.
 *
 * A rule returns empty if it cannot make a determination — not every rule
 * applies to every candidate set.
 */
public interface ReconciliationRule {

    /**
     * Evaluate this rule against the candidate set and the pre-computed score.
     *
     * @param cs    the candidate set (gateway + bank + ledger candidates)
     * @param score the deterministic score computed by {@link com.razorpay.buildathon.recon.engine.MatchScorer}
     * @return the rule outcome, or empty if this rule does not apply
     */
    Optional<RuleOutcome> evaluate(CandidateSet cs, MatchScore score);

    // -------------------------------------------------------------------------

    /**
     * The outcome of a single rule evaluation: everything the engine needs to
     * populate a {@link com.razorpay.buildathon.recon.model.MatchResult}.
     */
    record RuleOutcome(
            MatchStatus status,
            MatchMethod method,
            ExceptionCategory exceptionCategory,
            String reasoning
    ) {}
}
