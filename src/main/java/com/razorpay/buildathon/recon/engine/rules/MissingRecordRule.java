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
 * Rule 5: Detect missing source records and classify them.
 *
 * If a gateway record has no matching bank OR no matching ledger record,
 * this is an exception — the financial event is only partially evidenced.
 *
 * Cases handled:
 *   - Gateway + Ledger only (no bank):  MISSING_IN_BANK_FILE
 *   - Gateway + Bank only (no ledger):  MISSING_IN_LEDGER
 *   - Gateway only (no bank, no ledger): MISSING_IN_BANK_FILE (primary concern)
 *   - Bank or Ledger only (no gateway): MISSING_IN_GATEWAY
 *
 * Note: A missing record is NOT automatically a failure — it might just mean the
 * settlement hasn't arrived yet. But it IS an exception that requires attention.
 */
@Component
@Order(1)
public class MissingRecordRule implements ReconciliationRule {

    @Override
    public Optional<RuleOutcome> evaluate(CandidateSet cs, MatchScore score) {
        boolean hasGateway = cs.gatewayTxn() != null;
        boolean hasBank    = !cs.isMissingBank();
        boolean hasLedger  = !cs.isMissingLedger();

        if (hasGateway && hasBank && hasLedger) return Optional.empty(); // all present

        // --- Orphan: only bank or ledger, no gateway ---
        if (!hasGateway) {
            ExceptionCategory cat = hasBank
                    ? ExceptionCategory.MISSING_IN_GATEWAY
                    : ExceptionCategory.MISSING_IN_GATEWAY;
            String source = hasBank ? "bank" : "ledger";
            return Optional.of(new RuleOutcome(
                    MatchStatus.EXCEPTION,
                    MatchMethod.UNRESOLVED,
                    ExceptionCategory.MISSING_IN_GATEWAY,
                    "Orphan " + source + " record with no corresponding gateway transaction"
            ));
        }

        // --- Gateway present, bank missing ---
        if (!hasBank && hasLedger) {
            return Optional.of(new RuleOutcome(
                    MatchStatus.EXCEPTION,
                    MatchMethod.UNRESOLVED,
                    ExceptionCategory.MISSING_IN_BANK_FILE,
                    "Gateway transaction has ledger entry but no matching bank settlement record. " +
                    "Gateway ref=" + cs.gatewayTxn().getExternalRef()
            ));
        }

        // --- Gateway present, ledger missing ---
        if (hasBank && !hasLedger) {
            return Optional.of(new RuleOutcome(
                    MatchStatus.EXCEPTION,
                    MatchMethod.UNRESOLVED,
                    ExceptionCategory.MISSING_IN_LEDGER,
                    "Gateway transaction has bank settlement but no matching internal ledger record. " +
                    "Gateway ref=" + cs.gatewayTxn().getExternalRef()
            ));
        }

        // --- Gateway present, both bank and ledger missing ---
        return Optional.of(new RuleOutcome(
                MatchStatus.EXCEPTION,
                MatchMethod.UNRESOLVED,
                ExceptionCategory.MISSING_IN_BANK_FILE,
                "Gateway transaction has no corresponding bank settlement or ledger record. " +
                "Gateway ref=" + cs.gatewayTxn().getExternalRef()
        ));
    }
}
