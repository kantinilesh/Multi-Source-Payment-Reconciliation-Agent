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
 * Rule 2: Gateway-fee reconciliation.
 *
 * Handles the extremely common case where:
 *   bank_settled_amount = gateway_gross_amount − gateway_fee
 *
 * All 3 records must be present. The fee percentage must fall within the
 * configured tolerance band. If so, this is still a RECONCILED result because
 * the financial logic fully explains the discrepancy.
 */
@Component
@Order(5)
public class FeeAdjustedRule implements ReconciliationRule {

    private final ReconMatchingConfig config;

    public FeeAdjustedRule(ReconMatchingConfig config) {
        this.config = config;
    }

    @Override
    public Optional<RuleOutcome> evaluate(CandidateSet cs, MatchScore score) {
        NormalizedTransaction gw   = cs.gatewayTxn();
        NormalizedTransaction bank = cs.primaryBankTxn().orElse(null);
        NormalizedTransaction ledg = cs.primaryLedgerTxn().orElse(null);

        if (gw == null || bank == null) return Optional.empty();

        BigDecimal fee = RawFieldExtractor.extractGatewayFee(gw);
        if (fee.compareTo(BigDecimal.ZERO) == 0) return Optional.empty(); // no fee, different rule

        BigDecimal gwAmt   = gw.getAmount();
        BigDecimal bankAmt = bank.getAmount();
        BigDecimal expectedNet = gwAmt.subtract(fee);

        if (bankAmt.compareTo(expectedNet) != 0) return Optional.empty();

        // Fee % must be in configured band
        BigDecimal feePct = fee.divide(gwAmt, 6, java.math.RoundingMode.HALF_UP)
                              .multiply(BigDecimal.valueOf(100));
        if (feePct.compareTo(BigDecimal.valueOf(config.getFeeTolerancePctMin())) < 0
                || feePct.compareTo(BigDecimal.valueOf(config.getFeeTolerancePctMax())) > 0) {
            return Optional.empty();
        }

        // Ledger should still match gateway gross (it records the full charge)
        boolean ledgOk = ledg == null || gwAmt.compareTo(ledg.getAmount()) == 0;
        if (!ledgOk) return Optional.empty();

        String reasoning = String.format(
                "Fee-adjusted reconciliation: gateway=%s fee=%s (%.2f%%) net=%s bank=%s. " +
                "Score=%d",
                gwAmt, fee, feePct, expectedNet, bankAmt, score.getTotalScore());

        return Optional.of(new RuleOutcome(
                MatchStatus.RECONCILED,
                MatchMethod.RULE_HEURISTIC,
                ExceptionCategory.NONE,
                reasoning
        ));
    }
}
