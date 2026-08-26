package com.razorpay.buildathon.recon.evaluation.dto;

import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchStatus;

/**
 * Item-by-item comparison result comparing system prediction against ground truth for one transaction.
 */
public record CaseEvaluationResult(
        String gatewayRef,
        String scenarioCategory,
        MatchStatus expectedStatus,
        MatchStatus predictedStatus,
        ExceptionCategory expectedExceptionCategory,
        ExceptionCategory predictedExceptionCategory,
        boolean isStatusCorrect,
        boolean isCategoryCorrect,
        boolean isFalsePositive,
        boolean isFalseNegative,
        String reasoningSummary
) {}
