package com.razorpay.buildathon.recon.dto;

import com.razorpay.buildathon.recon.model.AuditLogEntry;
import com.razorpay.buildathon.recon.model.MatchMethod;

import java.time.Instant;

public record AuditLogEntryResponse(
        Long id,
        Long runId,
        Long matchResultId,
        MatchMethod method,
        Double confidence,
        String inputsConsidered,
        String reasoning,
        Instant createdAt
) {
    public static AuditLogEntryResponse from(AuditLogEntry e) {
        return new AuditLogEntryResponse(
                e.getId(),
                e.getRun() != null ? e.getRun().getId() : null,
                e.getMatchResult() != null ? e.getMatchResult().getId() : null,
                e.getMethod(),
                e.getConfidence(),
                e.getInputsConsidered(),
                e.getReasoning(),
                e.getCreatedAt()
        );
    }
}
