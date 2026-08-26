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

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Rule 6: Amount mismatch beyond tolerance.
 *
 * If all 3 records are present (or at least gateway + one other), the amounts
 * do not reconcile even after accounting for the gateway fee, and there is no
 * other explanation (refund, duplicate), this is a genuine financial discrepancy.
 *
 * Example:
 *   Gateway = ₹2,000 | Bank = ₹1,500 | Ledger = ₹2,000
 *   Fee could account for up to ₹80 (4%), but the ₹500 gap has no explanation.
 *   → EXCEPTION / AMOUNT_MISMATCH_BEYOND_TOLERANCE
 */
@Component
@Order(2)
public class AmountMismatchRule implements ReconciliationRule {

    private final ReconMatchingConfig config;

    public AmountMismatchRule(ReconMatchingConfig config) {
        this.config = config;
    }

    @Override
    public Optional<RuleOutcome> evaluate(CandidateSet cs, MatchScore score) {
        NormalizedTransaction gw   = cs.gatewayTxn();
        NormalizedTransaction bank = cs.primaryBankTxn().orElse(null);
        NormalizedTransaction ledg = cs.primaryLedgerTxn().orElse(null);

        if (gw == null || bank == null) return Optional.empty();

        BigDecimal gwAmt   = gw.getAmount();
        BigDecimal bankAmt = bank.getAmount();
        BigDecimal fee     = RawFieldExtractor.extractGatewayFee(gw);

        // Check exact equality first
        if (gwAmt.compareTo(bankAmt) == 0) return Optional.empty(); // no mismatch

        // Check fee-adjusted equality (gateway − fee = bank)
        if (fee.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal net = gwAmt.subtract(fee);
            if (bankAmt.compareTo(net) == 0) return Optional.empty(); // fee explains it
        }

        // Check if it's within the fee tolerance band as a plausible fee deduction
        BigDecimal diff = gwAmt.subtract(bankAmt);
        if (diff.compareTo(BigDecimal.ZERO) > 0) {
            BigDecimal pct = diff.divide(gwAmt, 6, java.math.RoundingMode.HALF_UP)
                                 .multiply(BigDecimal.valueOf(100));
            if (pct.compareTo(BigDecimal.valueOf(config.getFeeTolerancePctMin())) >= 0
                    && pct.compareTo(BigDecimal.valueOf(config.getFeeTolerancePctMax())) <= 0) {
                return Optional.empty(); // plausible fee — other rules handle this
            }
        }

        // Ledger check: if ledger amount also differs from gateway, mention it
        String ledgNote = "";
        if (ledg != null && gwAmt.compareTo(ledg.getAmount()) != 0) {
            ledgNote = String.format(" Ledger also mismatched: ledger=%s.", ledg.getAmount());
        }

        return Optional.of(new RuleOutcome(
                MatchStatus.EXCEPTION,
                MatchMethod.UNRESOLVED,
                ExceptionCategory.AMOUNT_MISMATCH_BEYOND_TOLERANCE,
                String.format("Amount mismatch with no fee explanation: gateway=%s bank=%s " +
                        "(diff=%s, fee=%s).%s Gateway ref=%s",
                        gwAmt, bankAmt, gwAmt.subtract(bankAmt), fee, ledgNote,
                        gw.getExternalRef())
        ));
    }
}
