package com.razorpay.buildathon.recon.dto;

import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import com.razorpay.buildathon.recon.model.SourceType;

import java.math.BigDecimal;
import java.time.Instant;

public record NormalizedTransactionResponse(
        Long id,
        SourceType sourceType,
        String externalRef,
        BigDecimal amount,
        Instant timestamp,
        String paymentMethod
) {
    public static NormalizedTransactionResponse from(NormalizedTransaction txn) {
        return new NormalizedTransactionResponse(
                txn.getId(),
                txn.getSourceType(),
                txn.getExternalRef(),
                txn.getAmount(),
                txn.getTimestamp(),
                txn.getPaymentMethod()
        );
    }
}
