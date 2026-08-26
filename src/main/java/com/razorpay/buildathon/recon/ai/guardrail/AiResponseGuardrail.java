package com.razorpay.buildathon.recon.ai.guardrail;

import com.razorpay.buildathon.recon.ai.config.ReconAiConfig;
import com.razorpay.buildathon.recon.ai.dto.AiReasoningRequest;
import com.razorpay.buildathon.recon.ai.dto.AiReasoningResponse;
import com.razorpay.buildathon.recon.model.MatchStatus;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Security & Guardrails validator for AI Exception Reasoning outputs.
 * Enforces:
 *   1. Prompt Injection Defense (sanitizes untrusted input strings)
 *   2. Hallucination Prevention (validates cited transaction IDs/refs exist in DB)
 *   3. Confidence Gating (forces REVIEW_REQUIRED if AI confidence < threshold)
 *   4. Schema & Data Sanity Checks
 */
@Component
public class AiResponseGuardrail {

    private static final Logger log = LoggerFactory.getLogger(AiResponseGuardrail.class);
    private static final Pattern PROMPT_INJECTION_PATTERN = Pattern.compile(
            "(?i)(ignore (all )?previous instruction(s)?|system prompt|override rules|mark (all )?as reconciled|delete database|forget rules|act as admin)"
    );

    private final ReconAiConfig aiConfig;

    public AiResponseGuardrail(ReconAiConfig aiConfig) {
        this.aiConfig = aiConfig;
    }

    /**
     * Sanitizes untrusted user/file inputs (e.g. external references, raw JSON notes)
     * before injecting into prompt strings to prevent prompt injection attacks.
     */
    public String sanitizeInput(String rawInput) {
        if (rawInput == null || rawInput.isBlank()) return "";
        // Strip dangerous injection attempts
        String clean = PROMPT_INJECTION_PATTERN.matcher(rawInput).replaceAll("[SANITIZED_INSTRUCTION]");
        // Escape XML tag boundaries to prevent breaking out of <untrusted_data> block
        return clean.replace("<", "&lt;").replace(">", "&gt;");
    }

    /**
     * Validates an AI reasoning response against system context and guardrail rules.
     * Returns either the original response (if fully valid and confident) or a safe
     * {@link MatchStatus#REVIEW_REQUIRED} fallback response.
     */
    public AiReasoningResponse validate(AiReasoningRequest request, AiReasoningResponse rawAiResponse) {
        if (rawAiResponse == null) {
            log.warn("Guardrail: raw AI response was null for matchResultId={}", request.matchResultId());
            return AiReasoningResponse.fallbackReviewRequired("Null response from AI provider.");
        }

        // 1. Confidence Gating
        double threshold = aiConfig.getConfidenceAutoAcceptThreshold();
        if (rawAiResponse.decision() == MatchStatus.RECONCILED && rawAiResponse.confidence() < threshold) {
            log.warn("Guardrail: AI decision was RECONCILED but confidence ({}) < threshold ({}) for matchResultId={}",
                    rawAiResponse.confidence(), threshold, request.matchResultId());
            return new AiReasoningResponse(
                    MatchStatus.REVIEW_REQUIRED,
                    rawAiResponse.confidence(),
                    rawAiResponse.exceptionCategory(),
                    "AI recommended RECONCILED but confidence (" + String.format("%.2f", rawAiResponse.confidence()) +
                    ") is below the auto-accept threshold (" + threshold + "). Routed to analyst review.",
                    rawAiResponse.evidence(),
                    "Analyst review required to confirm AI recommendation."
            );
        }

        // 2. Hallucination Prevention Check
        Set<String> validIdentifiers = collectValidIdentifiers(request);
        boolean hallucinationDetected = checkForHallucinations(rawAiResponse, validIdentifiers);

        if (hallucinationDetected) {
            log.warn("Guardrail: Hallucination detected in AI output for matchResultId={}", request.matchResultId());
            return new AiReasoningResponse(
                    MatchStatus.REVIEW_REQUIRED,
                    0.0,
                    rawAiResponse.exceptionCategory(),
                    "Guardrail rejected AI response: output cited non-existent transaction references or IDs.",
                    List.of("System guardrail flagged unsupported transaction claims in AI response."),
                    "Escalate to human analyst due to hallucination guardrail trigger."
            );
        }

        // 3. Evidence Check
        if (rawAiResponse.evidence() == null || rawAiResponse.evidence().isEmpty()) {
            log.warn("Guardrail: Empty evidence list in AI output for matchResultId={}", request.matchResultId());
            return AiReasoningResponse.fallbackReviewRequired("AI response lacked supporting evidence lines.");
        }

        log.info("Guardrail: AI response validated successfully for matchResultId={}: decision={}, confidence={}",
                request.matchResultId(), rawAiResponse.decision(), rawAiResponse.confidence());
        return rawAiResponse;
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private Set<String> collectValidIdentifiers(AiReasoningRequest req) {
        Set<String> ids = new HashSet<>();
        addTxn(ids, req.gatewayTxn());
        addTxn(ids, req.bankTxn());
        addTxn(ids, req.ledgerTxn());

        if (req.relatedGatewayTxns() != null) req.relatedGatewayTxns().forEach(t -> addTxn(ids, t));
        if (req.relatedBankTxns() != null) req.relatedBankTxns().forEach(t -> addTxn(ids, t));
        if (req.relatedLedgerTxns() != null) req.relatedLedgerTxns().forEach(t -> addTxn(ids, t));
        return ids;
    }

    private void addTxn(Set<String> ids, NormalizedTransaction t) {
        if (t == null) return;
        if (t.getId() != null) ids.add(String.valueOf(t.getId()));
        if (t.getExternalRef() != null && !t.getExternalRef().isBlank()) {
            ids.add(t.getExternalRef().toUpperCase());
        }
    }

    private boolean checkForHallucinations(AiReasoningResponse response, Set<String> validIdentifiers) {
        // Pattern looking for transaction ID / Ref citations like "txn_id=999" or "ref=TX-9999"
        Pattern pattern = Pattern.compile("(?i)(txn_id=|ref=|transaction\\s+)([A-Z0-9_-]{5,})");

        String textToInspect = (response.probableReason() + " " + String.join(" ", response.evidence())).toUpperCase();
        Matcher matcher = pattern.matcher(textToInspect);

        while (matcher.find()) {
            String citedRef = matcher.group(2).toUpperCase();
            // If cited reference looks like a specific transaction code but isn't in valid identifiers:
            if ((citedRef.startsWith("GW-") || citedRef.startsWith("SET-") || citedRef.startsWith("PAY-") || citedRef.startsWith("TX"))
                    && !validIdentifiers.contains(citedRef)) {
                log.warn("Guardrail flagged unknown cited reference '{}' not present in valid IDs: {}", citedRef, validIdentifiers);
                return true;
            }
        }
        return false;
    }
}
