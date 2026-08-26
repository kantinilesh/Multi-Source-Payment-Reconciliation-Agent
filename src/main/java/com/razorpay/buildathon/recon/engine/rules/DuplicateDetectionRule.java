package com.razorpay.buildathon.recon.engine.rules;

import com.razorpay.buildathon.recon.engine.CandidateSet;
import com.razorpay.buildathon.recon.engine.MatchScore;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchMethod;
import com.razorpay.buildathon.recon.model.MatchStatus;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 8: Duplicate detection.
 *
 * If the CandidateGenerator found multiple bank or ledger records that all
 * appear to correspond to the same gateway transaction, this is a potential
 * duplicate and must NOT be silently merged.
 *
 * The engine cannot deterministically pick which record is the "real" one —
 * that requires either a domain expert or the Phase 4 AI layer.
 */
@Component
@Order(3)
public class DuplicateDetectionRule implements ReconciliationRule {

    @Override
    public Optional<RuleOutcome> evaluate(CandidateSet cs, MatchScore score) {
        boolean hasBankDupes   = cs.hasBankDuplicates();
        boolean hasLedgerDupes = cs.hasLedgerDuplicates();

        if (!hasBankDupes && !hasLedgerDupes) return Optional.empty();

        String gwRef = cs.gatewayTxn() != null
                ? cs.gatewayTxn().getExternalRef() : "N/A";

        StringBuilder detail = new StringBuilder("Duplicate candidate(s) detected. Gateway ref=")
                .append(gwRef);

        if (hasBankDupes) {
            detail.append(". ").append(cs.bankCandidates().size())
                  .append(" bank candidates with matching signals.");
        }
        if (hasLedgerDupes) {
            detail.append(". ").append(cs.ledgerCandidates().size())
                  .append(" ledger candidates with matching signals.");
        }
        detail.append(" Cannot deterministically pick one — flagged for review.");

        return Optional.of(new RuleOutcome(
                MatchStatus.EXCEPTION,
                MatchMethod.UNRESOLVED,
                ExceptionCategory.DUPLICATE_DETECTED,
                detail.toString()
        ));
    }
}
