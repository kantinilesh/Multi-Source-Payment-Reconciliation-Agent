package com.razorpay.buildathon.recon.dto;

import com.razorpay.buildathon.recon.model.RunStatus;

public record RunUploadResponse(
        Long runId,
        RunStatus status,
        int gatewayRowCount,
        int bankRowCount,
        int ledgerRowCount,
        String message
) {
}
