package com.razorpay.buildathon.recon.ai.provider;

import com.razorpay.buildathon.recon.ai.dto.AiReasoningRequest;
import com.razorpay.buildathon.recon.ai.dto.AiReasoningResponse;

/**
 * Abstraction interface for LLM provider clients (e.g. Anthropic, OpenAI, or Mock).
 */
public interface LlmClient {

    /**
     * Send an ambiguous match reasoning request to the LLM and return structured analysis.
     * Must never throw uncaught exceptions — on failure or timeout, return a safe fallback.
     */
    AiReasoningResponse analyze(AiReasoningRequest request);

    /**
     * Human-readable provider identifier for audit logging.
     */
    String getProviderName();
}
