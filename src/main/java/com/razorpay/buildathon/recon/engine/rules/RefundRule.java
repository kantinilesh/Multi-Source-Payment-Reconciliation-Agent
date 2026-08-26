package com.razorpay.buildathon.recon.engine.rules;

import com.razorpay.buildathon.recon.engine.CandidateSet;
import com.razorpay.buildathon.recon.engine.MatchScore;
import com.razorpay.buildathon.recon.engine.MatchScorer;
import com.razorpay.buildathon.recon.engine.RawFieldExtractor;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchMethod;
import com.razorpay.buildathon.recon.model.MatchStatus;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 7: Refund detection.
 *
 * Handles scenarios where a transaction has been refunded. Refund records
 * should NOT be treated as failed payments — they represent a legitimate
 * financial event with their own reconciliation logic.
 *
 * Cases:
 *   - Both gateway and ledger show REFUND status → RECONCILED (consistent refund)
 *   - Gateway shows SUCCESS but ledger shows REFUND → EXCEPTION/REFUND_MISMATCH
 *     (the ledger has been updated to reflect a refund but the gateway shows the
 *     original charge — needs investigation)
 */
@Component
@Order(8)
public class RefundRule implements ReconciliationRule {

    @Override
    public Optional<RuleOutcome> evaluate(CandidateSet cs, MatchScore score) {
        NormalizedTransaction gw   = cs.gatewayTxn();
        NormalizedTransaction ledg = cs.primaryLedgerTxn().orElse(null);

        if (gw == null) return Optional.empty();

        String gwStatus   = RawFieldExtractor.extractStatus(gw);
        String ledgStatus = ledg != null ? RawFieldExtractor.extractStatus(ledg) : "";

        boolean gwRefund   = MatchScorer.REFUND_STATUSES.contains(gwStatus);
        boolean ledgRefund = !ledgStatus.isBlank() && MatchScorer.REFUND_STATUSES.contains(ledgStatus);
        boolean gwSuccess  = !gwStatus.isBlank() && !gwRefund;

        // Both sides agree it's a refund → reconciled refund
        if (gwRefund && ledgRefund) {
            return Optional.of(new RuleOutcome(
                    MatchStatus.RECONCILED,
                    MatchMethod.RULE_HEURISTIC,
                    ExceptionCategory.NONE,
                    String.format("Consistent refund: gateway status='%s' ledger status='%s'. " +
                            "Gateway ref=%s", gwStatus, ledgStatus, gw.getExternalRef())
            ));
        }

        // Gateway shows success but ledger shows refund — status mismatch
        if (gwSuccess && ledgRefund) {
            return Optional.of(new RuleOutcome(
                    MatchStatus.EXCEPTION,
                    MatchMethod.UNRESOLVED,
                    ExceptionCategory.REFUND_MISMATCH,
                    String.format("Status mismatch: gateway='%s' but ledger='%s'. " +
                            "The ledger records a refund that the gateway does not show. " +
                            "Gateway ref=%s", gwStatus, ledgStatus, gw.getExternalRef())
            ));
        }

        // Gateway shows refund but ledger shows success (or is absent)
        if (gwRefund && !ledgRefund) {
            return Optional.of(new RuleOutcome(
                    MatchStatus.EXCEPTION,
                    MatchMethod.UNRESOLVED,
                    ExceptionCategory.REFUND_MISMATCH,
                    String.format("Gateway shows refund ('%s') but ledger is absent or " +
                            "shows '%s'. Gateway ref=%s",
                            gwStatus,
                            ledgStatus.isBlank() ? "no entry" : ledgStatus,
                            gw.getExternalRef())
            ));
        }

        return Optional.empty(); // neither side is a refund; other rules apply
    }
}
