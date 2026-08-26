package com.razorpay.buildathon.recon.evaluation.data;

import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchStatus;

/**
 * Record representing a single ground truth expected outcome for evaluation.
 */
public record GroundTruthRecord(
        String gatewayRef,
        MatchStatus expectedStatus,
        ExceptionCategory expectedExceptionCategory,
        String scenarioCategory,
        String description
) {}
