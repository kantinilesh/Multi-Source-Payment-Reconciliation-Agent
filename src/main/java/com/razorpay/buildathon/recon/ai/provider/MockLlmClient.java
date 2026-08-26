package com.razorpay.buildathon.recon.ai.provider;

import com.razorpay.buildathon.recon.ai.dto.AiReasoningRequest;
import com.razorpay.buildathon.recon.ai.dto.AiReasoningResponse;
import com.razorpay.buildathon.recon.engine.RawFieldExtractor;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchStatus;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;

/**
 * Mock / Heuristic LLM Provider used when running offline, in test environments,
 * or when mock mode is enabled in configuration.
 * Evaluates financial evidence deterministically to simulate AI reasoning.
 */
@Component
public class MockLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(MockLlmClient.class);

    @Override
    public AiReasoningResponse analyze(AiReasoningRequest request) {
        log.info("MockLlmClient evaluating matchResultId={}", request.matchResultId());

        NormalizedTransaction gw = request.gatewayTxn();
        NormalizedTransaction bank = request.bankTxn();
        NormalizedTransaction ledger = request.ledgerTxn();

        List<String> evidence = new ArrayList<>();

        // Case 1: Missing Bank
        if (gw != null && bank == null && ledger != null) {
            evidence.add("Gateway transaction " + gw.getExternalRef() + " gross amount " + gw.getAmount() + " present.");
            evidence.add("Internal ledger record " + ledger.getExternalRef() + " confirms payment recorded.");
            evidence.add("Bank settlement file contains no entry matching reference core or timestamp window.");
            return new AiReasoningResponse(
                    MatchStatus.EXCEPTION,
                    0.90,
                    ExceptionCategory.MISSING_IN_BANK_FILE,
                    "Gateway charge and internal ledger record exist, but bank settlement file is missing the deposit. Likely settlement delay or processing batch cutoff.",
                    evidence,
                    "Contact acquiring bank to verify settlement batch for gateway ref: " + gw.getExternalRef()
            );
        }

        // Case 2: Missing Ledger
        if (gw != null && bank != null && ledger == null) {
            evidence.add("Gateway transaction " + gw.getExternalRef() + " gross amount " + gw.getAmount() + " present.");
            evidence.add("Bank settlement record " + bank.getExternalRef() + " net amount " + bank.getAmount() + " deposited.");
            evidence.add("Internal ERP ledger has no corresponding order entry.");
            return new AiReasoningResponse(
                    MatchStatus.EXCEPTION,
                    0.88,
                    ExceptionCategory.MISSING_IN_LEDGER,
                    "Payment was authorized by gateway and deposited by bank, but internal ERP ledger was not updated. Indicates a webhook failure or ledger sync drop.",
                    evidence,
                    "Trigger manual ledger posting for gateway ref: " + gw.getExternalRef()
            );
        }

        // Case 3: Gateway Fee Reconciliation
        if (gw != null && bank != null) {
            BigDecimal gwAmt = gw.getAmount();
            BigDecimal bankAmt = bank.getAmount();
            BigDecimal fee = RawFieldExtractor.extractGatewayFee(gw);

            if (fee.compareTo(BigDecimal.ZERO) > 0) {
                BigDecimal expectedNet = gwAmt.subtract(fee);
                if (bankAmt.compareTo(expectedNet) == 0) {
                    BigDecimal feePct = fee.divide(gwAmt, 4, RoundingMode.HALF_UP).multiply(BigDecimal.valueOf(100));
                    evidence.add("Gateway gross amount: " + gwAmt);
                    evidence.add("Gateway fee deducted: " + fee + " (" + feePct + "%)");
                    evidence.add("Bank settled amount: " + bankAmt + " matches expected net " + expectedNet);

                    return new AiReasoningResponse(
                            MatchStatus.RECONCILED,
                            0.95,
                            ExceptionCategory.NONE,
                            "AI verified that the discrepancy between gateway gross amount (" + gwAmt + ") and bank settled amount (" + bankAmt + ") is fully explained by gateway fee (" + fee + ").",
                            evidence,
                            "Auto-accept reconciliation under fee adjustment rule."
                    );
                }
            }
        }

        // Case 4: Duplicates
        if (!request.relatedBankTxns().isEmpty() && request.relatedBankTxns().size() > 1) {
            evidence.add("Multiple bank settlement entries (" + request.relatedBankTxns().size() + ") match gateway amount/time window.");
            return new AiReasoningResponse(
                    MatchStatus.EXCEPTION,
                    0.85,
                    ExceptionCategory.DUPLICATE_DETECTED,
                    "AI identified multiple bank settlement candidate records for gateway reference " + (gw != null ? gw.getExternalRef() : "N/A") + ". High risk of duplicate settlement.",
                    evidence,
                    "Require human analyst review to resolve duplicate bank deposit records."
            );
        }

        // Case 5: Unexplained discrepancy -> REVIEW_REQUIRED
        evidence.add("Deterministic score: " + request.deterministicConfidence());
        evidence.add("Initial status: " + request.initialStatus());
        evidence.add("Evidence is suggestive but insufficient to rule out financial discrepancy.");

        return new AiReasoningResponse(
                MatchStatus.REVIEW_REQUIRED,
                0.65,
                ExceptionCategory.NONE,
                "Insufficient corroborating evidence across gateway, bank, and ledger records to auto-reconcile confidently.",
                evidence,
                "Escalate to human finance controller for review."
        );
    }

    @Override
    public String getProviderName() {
        return "MockLlmClient (Deterministic Rule Evaluator)";
    }
}
