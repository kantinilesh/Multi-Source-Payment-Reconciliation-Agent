package com.razorpay.buildathon.recon.engine.rules;

import com.razorpay.buildathon.recon.config.ReconMatchingConfig;
import com.razorpay.buildathon.recon.engine.CandidateSet;
import com.razorpay.buildathon.recon.engine.MatchScore;
import com.razorpay.buildathon.recon.engine.RawFieldExtractor;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchMethod;
import com.razorpay.buildathon.recon.model.MatchStatus;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.Optional;

/**
 * Rule 3: Reference-variant reconciliation.
 *
 * Handles the common case where gateway, bank, and ledger use different ID
 * formats for the same transaction (GW-83921, SET-83921, PAY-83921).
 *
 * After stripping source-specific prefixes the numeric cores are identical,
 * but the raw refs look different. This rule fires when:
 *   - All 3 records are present
 *   - At least 2 of 3 numeric cores match
 *   - The total score exceeds the RECONCILED threshold
 *
 * If the score only reaches REVIEW_REQUIRED, we emit that instead.
 */
@Component
@Order(6)
public class ReferenceVariantRule implements ReconciliationRule {

    private final ReconMatchingConfig config;

    public ReferenceVariantRule(ReconMatchingConfig config) {
        this.config = config;
    }

    @Override
    public Optional<RuleOutcome> evaluate(CandidateSet cs, MatchScore score) {
        NormalizedTransaction gw   = cs.gatewayTxn();
        NormalizedTransaction bank = cs.primaryBankTxn().orElse(null);
        NormalizedTransaction ledg = cs.primaryLedgerTxn().orElse(null);

        if (gw == null) return Optional.empty();

        String gwCore   = RawFieldExtractor.extractNumericCore(gw.getExternalRef());
        String bankCore = bank != null ? RawFieldExtractor.extractNumericCore(bank.getExternalRef()) : null;
        String ledgCore = ledg != null ? RawFieldExtractor.extractNumericCore(ledg.getExternalRef()) : null;

        // Need at least one secondary source
        if (bank == null && ledg == null) return Optional.empty();

        boolean bankMatch = bankCore != null && gwCore.equals(bankCore);
        boolean ledgMatch = ledgCore != null && gwCore.equals(ledgCore);

        if (!bankMatch && !ledgMatch) return Optional.empty(); // no core overlap at all

        // The raw refs must differ (otherwise ExactMatchRule would have fired)
        boolean refsActuallyDiffer =
                (bank != null && !gw.getExternalRef().equals(bank.getExternalRef())) ||
                (ledg != null && !gw.getExternalRef().equals(ledg.getExternalRef()));

        if (!refsActuallyDiffer) return Optional.empty(); // ExactMatchRule should handle this

        int total = score.getTotalScore();
        if (total >= config.getReconciledThreshold()) {
            return Optional.of(new RuleOutcome(
                    MatchStatus.RECONCILED,
                    MatchMethod.RULE_HEURISTIC,
                    ExceptionCategory.NONE,
                    String.format("Reference variant match: gw='%s' bank='%s' ledger='%s' → core='%s'. Score=%d",
                            gw.getExternalRef(),
                            bank  != null ? bank.getExternalRef()  : "N/A",
                            ledg  != null ? ledg.getExternalRef() : "N/A",
                            gwCore, total)
            ));
        } else if (total >= config.getReviewThreshold()) {
            return Optional.of(new RuleOutcome(
                    MatchStatus.REVIEW_REQUIRED,
                    MatchMethod.RULE_HEURISTIC,
                    ExceptionCategory.NONE,
                    String.format("Reference variant — insufficient score for auto-reconciliation: " +
                            "gw='%s' bank='%s' ledger='%s' score=%d (threshold=%d)",
                            gw.getExternalRef(),
                            bank  != null ? bank.getExternalRef()  : "N/A",
                            ledg  != null ? ledg.getExternalRef() : "N/A",
                            total, config.getReconciledThreshold())
            ));
        }
        return Optional.empty(); // too low — let later rules handle it
    }
}
