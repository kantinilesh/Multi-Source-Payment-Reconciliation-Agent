package com.razorpay.buildathon.recon.ai.dto;

import com.razorpay.buildathon.recon.model.MatchResult;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;

import java.util.List;

/**
 * Request container passed to the LLM Client containing all context required
 * for an AI reasoning iteration on an ambiguous or unresolved match.
 */
public record AiReasoningRequest(
        Long matchResultId,
        Long runId,
        String initialStatus,
        String initialExceptionCategory,
        Double deterministicConfidence,
        String deterministicReasoning,

        // Referenced transactions
        NormalizedTransaction gatewayTxn,
        NormalizedTransaction bankTxn,
        NormalizedTransaction ledgerTxn,

        // Context gathered from tools
        List<NormalizedTransaction> relatedGatewayTxns,
        List<NormalizedTransaction> relatedBankTxns,
        List<NormalizedTransaction> relatedLedgerTxns,
        String feeDetailsJson,
        String runSummaryContext
) {
    public static AiReasoningRequest from(MatchResult matchResult,
                                          List<NormalizedTransaction> relatedGatewayTxns,
                                          List<NormalizedTransaction> relatedBankTxns,
                                          List<NormalizedTransaction> relatedLedgerTxns,
                                          String feeDetailsJson,
                                          String runSummaryContext) {
        return new AiReasoningRequest(
                matchResult.getId(),
                matchResult.getRun() != null ? matchResult.getRun().getId() : null,
                matchResult.getStatus() != null ? matchResult.getStatus().name() : "UNKNOWN",
                matchResult.getExceptionCategory() != null ? matchResult.getExceptionCategory().name() : "NONE",
                matchResult.getConfidence() != null ? matchResult.getConfidence() : 0.0,
                matchResult.getReasoning() != null ? matchResult.getReasoning() : "",
                matchResult.getGatewayTxn(),
                matchResult.getBankTxn(),
                matchResult.getLedgerTxn(),
                relatedGatewayTxns != null ? relatedGatewayTxns : List.of(),
                relatedBankTxns != null ? relatedBankTxns : List.of(),
                relatedLedgerTxns != null ? relatedLedgerTxns : List.of(),
                feeDetailsJson != null ? feeDetailsJson : "{}",
                runSummaryContext != null ? runSummaryContext : ""
        );
    }
}
