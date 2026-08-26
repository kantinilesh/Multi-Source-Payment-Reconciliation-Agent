package com.razorpay.buildathon.recon.dto;

import com.razorpay.buildathon.recon.model.*;

import java.time.Instant;

public record MatchResultResponse(
        Long id,
        Long runId,
        MatchStatus status,
        MatchMethod method,
        ExceptionCategory exceptionCategory,
        Double confidence,
        String reasoning,

        // Nullable transaction summaries for each source
        TxnSummary gatewayTxn,
        TxnSummary bankTxn,
        TxnSummary ledgerTxn,

        Instant createdAt
) {
    public static MatchResultResponse from(MatchResult mr) {
        return new MatchResultResponse(
                mr.getId(),
                mr.getRun() != null ? mr.getRun().getId() : null,
                mr.getStatus(),
                mr.getMethod(),
                mr.getExceptionCategory(),
                mr.getConfidence(),
                mr.getReasoning(),
                mr.getGatewayTxn() != null ? TxnSummary.from(mr.getGatewayTxn()) : null,
                mr.getBankTxn()    != null ? TxnSummary.from(mr.getBankTxn())    : null,
                mr.getLedgerTxn()  != null ? TxnSummary.from(mr.getLedgerTxn())  : null,
                mr.getCreatedAt()
        );
    }

    /** Lightweight summary of a NormalizedTransaction for embedding in a MatchResultResponse. */
    public record TxnSummary(
            Long id,
            SourceType sourceType,
            String externalRef,
            java.math.BigDecimal amount,
            Instant timestamp
    ) {
        public static TxnSummary from(NormalizedTransaction t) {
            return new TxnSummary(t.getId(), t.getSourceType(),
                    t.getExternalRef(), t.getAmount(), t.getTimestamp());
        }
    }
}
