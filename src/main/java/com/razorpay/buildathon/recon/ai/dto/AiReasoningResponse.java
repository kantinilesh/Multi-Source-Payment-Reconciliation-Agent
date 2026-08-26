package com.razorpay.buildathon.recon.ai.dto;

import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchStatus;

import java.util.List;

/**
 * Structured response produced by the AI Agent / LLM Provider.
 * Must be validated against application evidence before affecting financial state.
 */
public record AiReasoningResponse(
        MatchStatus decision,
        Double confidence,
        ExceptionCategory exceptionCategory,
        String probableReason,
        List<String> evidence,
        String recommendedAction
) {
    public static AiReasoningResponse fallbackReviewRequired(String reason) {
        return new AiReasoningResponse(
                MatchStatus.REVIEW_REQUIRED,
                0.0,
                ExceptionCategory.NONE,
                reason,
                List.of("System fallback due to insufficient confidence, validation error, or service unavailability."),
                "Route to human finance analyst for manual inspection."
        );
    }
}
