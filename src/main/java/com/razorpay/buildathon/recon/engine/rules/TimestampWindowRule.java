package com.razorpay.buildathon.recon.engine.rules;

import com.razorpay.buildathon.recon.config.ReconMatchingConfig;
import com.razorpay.buildathon.recon.engine.CandidateSet;
import com.razorpay.buildathon.recon.engine.MatchScore;
import com.razorpay.buildathon.recon.engine.ScoreSignal;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchMethod;
import com.razorpay.buildathon.recon.model.MatchStatus;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 4: Timestamp difference within configured window.
 *
 * Handles records that match on IDs and amounts but whose timestamps differ
 * (a very common scenario since bank settlement happens 1–5 days after
 * gateway authorization).
 *
 * Fires when:
 *   - IDs or amounts match strongly enough (score threshold met)
 *   - TIMESTAMP_WINDOW signal fired (timestamps are within lag window)
 *
 * If the score is high enough → RECONCILED; otherwise → REVIEW_REQUIRED.
 */
@Component
@Order(7)
public class TimestampWindowRule implements ReconciliationRule {

    private final ReconMatchingConfig config;

    public TimestampWindowRule(ReconMatchingConfig config) {
        this.config = config;
    }

    @Override
    public Optional<RuleOutcome> evaluate(CandidateSet cs, MatchScore score) {
        NormalizedTransaction gw   = cs.gatewayTxn();
        NormalizedTransaction bank = cs.primaryBankTxn().orElse(null);
        NormalizedTransaction ledg = cs.primaryLedgerTxn().orElse(null);

        if (gw == null || (bank == null && ledg == null)) return Optional.empty();
        if (!score.hasFired(ScoreSignal.TIMESTAMP_WINDOW)) return Optional.empty();

        // Only apply when there IS a meaningful ID or amount signal too
        boolean hasIdEvidence = score.hasFired(ScoreSignal.EXACT_ID)
                || score.hasFired(ScoreSignal.REF_SIMILARITY);
        boolean hasAmountEvidence = score.hasFired(ScoreSignal.AMOUNT_EXACT)
                || score.hasFired(ScoreSignal.FEE_ADJUSTED);
        if (!hasIdEvidence && !hasAmountEvidence) return Optional.empty();

        int total = score.getTotalScore();
        if (total >= config.getReconciledThreshold()) {
            String bankTs  = bank != null ? bank.getTimestamp().toString() : "N/A";
            String ledgTs  = ledg != null ? ledg.getTimestamp().toString() : "N/A";
            return Optional.of(new RuleOutcome(
                    MatchStatus.RECONCILED,
                    MatchMethod.RULE_HEURISTIC,
                    ExceptionCategory.NONE,
                    String.format("Timestamp-window match: gw=%s bank=%s ledger=%s " +
                            "(window=%d days). Score=%d",
                            gw.getTimestamp(), bankTs, ledgTs,
                            config.getSettlementLagDaysMax(), total)
            ));
        } else if (total >= config.getReviewThreshold()) {
            return Optional.of(new RuleOutcome(
                    MatchStatus.REVIEW_REQUIRED,
                    MatchMethod.RULE_HEURISTIC,
                    ExceptionCategory.NONE,
                    String.format("Timestamps within window but combined score=%d insufficient " +
                            "for auto-reconciliation (threshold=%d)",
                            total, config.getReconciledThreshold())
            ));
        }
        return Optional.empty();
    }
}
