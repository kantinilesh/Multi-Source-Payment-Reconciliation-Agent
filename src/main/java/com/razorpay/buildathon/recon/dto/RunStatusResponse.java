package com.razorpay.buildathon.recon.dto;

import com.razorpay.buildathon.recon.model.ReconciliationRun;
import com.razorpay.buildathon.recon.model.RunStatus;

import java.time.Instant;

public record RunStatusResponse(
        Long id,
        RunStatus status,
        Instant createdAt,
        Instant completedAt,
        String gatewayFileName,
        String bankFileName,
        String ledgerFileName,
        Integer matchedCount,
        Integer partiallyMatchedCount,
        Integer exceptionCount,
        Integer aiAssistedCount,
        Double matchRatePct,
        Double automationRatePct,
        Long processingTimeMs
) {
    public static RunStatusResponse from(ReconciliationRun run) {
        return new RunStatusResponse(
                run.getId(),
                run.getStatus(),
                run.getCreatedAt(),
                run.getCompletedAt(),
                run.getGatewayFileName(),
                run.getBankFileName(),
                run.getLedgerFileName(),
                run.getMatchedCount(),
                run.getPartiallyMatchedCount(),
                run.getExceptionCount(),
                run.getAiAssistedCount(),
                run.getMatchRatePct(),
                run.getAutomationRatePct(),
                run.getProcessingTimeMs()
        );
    }
}
