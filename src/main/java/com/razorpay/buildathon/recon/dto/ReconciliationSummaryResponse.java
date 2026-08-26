package com.razorpay.buildathon.recon.dto;

import com.razorpay.buildathon.recon.model.ReconciliationRun;
import com.razorpay.buildathon.recon.model.RunStatus;

import java.time.Instant;

public record ReconciliationSummaryResponse(
        Long runId,
        RunStatus status,
        int totalMatchResults,
        int reconciledCount,
        int reviewRequiredCount,
        int exceptionCount,
        int aiAssistedCount,
        Double matchRatePct,
        Double automationRatePct,
        Long processingTimeMs,
        Instant completedAt
) {
    public static ReconciliationSummaryResponse from(ReconciliationRun run, int total) {
        return new ReconciliationSummaryResponse(
                run.getId(),
                run.getStatus(),
                total,
                run.getMatchedCount()          != null ? run.getMatchedCount()          : 0,
                run.getPartiallyMatchedCount()  != null ? run.getPartiallyMatchedCount()  : 0,
                run.getExceptionCount()         != null ? run.getExceptionCount()         : 0,
                run.getAiAssistedCount()        != null ? run.getAiAssistedCount()        : 0,
                run.getMatchRatePct(),
                run.getAutomationRatePct(),
                run.getProcessingTimeMs(),
                run.getCompletedAt()
        );
    }
}
