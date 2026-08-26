package com.razorpay.buildathon.recon.ai.provider;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.buildathon.recon.ai.config.ReconAiConfig;
import com.razorpay.buildathon.recon.ai.dto.AiReasoningRequest;
import com.razorpay.buildathon.recon.ai.dto.AiReasoningResponse;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Duration;
import java.util.*;

/**
 * Production LLM Provider using Spring {@link RestClient} to invoke Anthropic / Claude API
 * (or compatible endpoint) with structured JSON prompt formatting.
 * Safe fallback to MockLlmClient if API key is not configured or in mock mode.
 */
@Component
@Primary
public class AnthropicLlmClient implements LlmClient {

    private static final Logger log = LoggerFactory.getLogger(AnthropicLlmClient.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final ReconAiConfig aiConfig;
    private final MockLlmClient mockLlmClient;

    public AnthropicLlmClient(ReconAiConfig aiConfig, MockLlmClient mockLlmClient) {
        this.aiConfig = aiConfig;
        this.mockLlmClient = mockLlmClient;
    }

    @Override
    public AiReasoningResponse analyze(AiReasoningRequest request) {
        // Fallback to mock mode if explicitly enabled or if API key is blank
        if (aiConfig.isMockMode() || aiConfig.getApiKey() == null || aiConfig.getApiKey().isBlank()) {
            log.info("API key absent or mock mode enabled — delegating to MockLlmClient.");
            return mockLlmClient.analyze(request);
        }

        try {
            String systemPrompt = buildSystemPrompt();
            String userPrompt = buildUserPrompt(request);

            Map<String, Object> body = Map.of(
                    "model", aiConfig.getModel(),
                    "max_tokens", aiConfig.getMaxTokens(),
                    "system", systemPrompt,
                    "messages", List.of(
                            Map.of("role", "user", "content", userPrompt)
                    )
            );

            RestClient restClient = RestClient.builder()
                    .baseUrl(aiConfig.getProviderBaseUrl())
                    .build();

            String responseJson = restClient.post()
                    .uri("/v1/messages")
                    .header("x-api-key", aiConfig.getApiKey())
                    .header("anthropic-version", "2023-06-01")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(body)
                    .retrieve()
                    .body(String.class);

            return parseLlmResponse(responseJson);

        } catch (Exception e) {
            log.error("LLM Provider API call failed for matchResultId={}: {}",
                    request.matchResultId(), e.getMessage(), e);
            return AiReasoningResponse.fallbackReviewRequired(
                    "LLM provider service call failed or timed out: " + e.getMessage()
            );
        }
    }

    @Override
    public String getProviderName() {
        return "AnthropicLlmClient (" + aiConfig.getModel() + ")";
    }

    // -------------------------------------------------------------------------
    // Private Helpers
    // -------------------------------------------------------------------------

    private String buildSystemPrompt() {
        return """
            You are an expert AI Finance Controller specializing in multi-source payment reconciliation across Payment Gateway, Bank Settlement, and ERP Internal Ledger.
            
            YOUR TASK:
            Analyze ambiguous or unresolved transaction matches.
            
            CRITICAL RULES & GUARDRAILS:
            1. Rely STRICTLY on the system evidence provided in the request context.
            2. Never fabricate transaction IDs, amounts, or references.
            3. Do not override deterministic rules when evidence is absent.
            4. If evidence is ambiguous or incomplete, output decision = "REVIEW_REQUIRED".
            5. Output MUST be valid JSON conforming strictly to the requested schema.
            
            JSON OUTPUT SCHEMA:
            {
              "decision": "RECONCILED" | "REVIEW_REQUIRED" | "EXCEPTION",
              "confidence": 0.88,
              "exceptionCategory": "NONE" | "MISSING_IN_BANK_FILE" | "MISSING_IN_LEDGER" | "MISSING_IN_GATEWAY" | "AMOUNT_MISMATCH_BEYOND_TOLERANCE" | "REFUND_MISMATCH" | "DUPLICATE_DETECTED" | "AMBIGUOUS_MULTI_MATCH",
              "probableReason": "<Detailed natural language explanation>",
              "evidence": ["<Fact 1>", "<Fact 2>"],
              "recommendedAction": "<Actionable guidance for analyst>"
            }
            """;
    }

    private String buildUserPrompt(AiReasoningRequest req) {
        StringBuilder sb = new StringBuilder();
        sb.append("<untrusted_data>\n");
        sb.append("MatchResult ID: ").append(req.matchResultId()).append("\n");
        sb.append("Initial Status: ").append(req.initialStatus()).append("\n");
        sb.append("Deterministic Confidence: ").append(req.deterministicConfidence()).append("\n");
        sb.append("Deterministic Reasoning: ").append(req.deterministicReasoning()).append("\n\n");

        if (req.gatewayTxn() != null) {
            sb.append("GATEWAY RECORD: ref=").append(req.gatewayTxn().getExternalRef())
              .append(", amount=").append(req.gatewayTxn().getAmount())
              .append(", timestamp=").append(req.gatewayTxn().getTimestamp())
              .append(", feeDetails=").append(req.feeDetailsJson()).append("\n");
        }
        if (req.bankTxn() != null) {
            sb.append("BANK RECORD: ref=").append(req.bankTxn().getExternalRef())
              .append(", amount=").append(req.bankTxn().getAmount())
              .append(", timestamp=").append(req.bankTxn().getTimestamp()).append("\n");
        }
        if (req.ledgerTxn() != null) {
            sb.append("LEDGER RECORD: ref=").append(req.ledgerTxn().getExternalRef())
              .append(", amount=").append(req.ledgerTxn().getAmount())
              .append(", timestamp=").append(req.ledgerTxn().getTimestamp()).append("\n");
        }

        if (!req.relatedBankTxns().isEmpty()) {
            sb.append("RELATED BANK CANDIDATES: ").append(req.relatedBankTxns().size()).append(" candidates\n");
        }
        if (!req.relatedLedgerTxns().isEmpty()) {
            sb.append("RELATED LEDGER CANDIDATES: ").append(req.relatedLedgerTxns().size()).append(" candidates\n");
        }

        sb.append("</untrusted_data>\n\n");
        sb.append("Produce the structured JSON analysis:");
        return sb.toString();
    }

    private AiReasoningResponse parseLlmResponse(String responseJson) {
        try {
            JsonNode root = MAPPER.readTree(responseJson);
            String contentText = "";
            if (root.has("content") && root.get("content").isArray() && root.get("content").size() > 0) {
                contentText = root.get("content").get(0).path("text").asText("");
            } else {
                contentText = responseJson;
            }

            // Extract JSON object substring if wrapped in markdown code blocks ```json ... ```
            int start = contentText.indexOf('{');
            int end = contentText.lastIndexOf('}');
            if (start >= 0 && end > start) {
                contentText = contentText.substring(start, end + 1);
            }

            JsonNode jsonNode = MAPPER.readTree(contentText);

            String decisionStr = jsonNode.path("decision").asText("REVIEW_REQUIRED");
            MatchStatus status;
            try {
                status = MatchStatus.valueOf(decisionStr.toUpperCase());
            } catch (Exception e) {
                status = MatchStatus.REVIEW_REQUIRED;
            }

            double confidence = jsonNode.path("confidence").asDouble(0.0);

            String categoryStr = jsonNode.path("exceptionCategory").asText("NONE");
            ExceptionCategory category;
            try {
                category = ExceptionCategory.valueOf(categoryStr.toUpperCase());
            } catch (Exception e) {
                category = ExceptionCategory.NONE;
            }

            String probableReason = jsonNode.path("probableReason").asText("AI analysis completed.");
            String recommendedAction = jsonNode.path("recommendedAction").asText("Review match result.");

            List<String> evidence = new ArrayList<>();
            if (jsonNode.has("evidence") && jsonNode.get("evidence").isArray()) {
                for (JsonNode ev : jsonNode.get("evidence")) {
                    evidence.add(ev.asText());
                }
            }

            return new AiReasoningResponse(status, confidence, category, probableReason, evidence, recommendedAction);

        } catch (Exception e) {
            log.error("Failed to parse JSON response from LLM: {}", e.getMessage(), e);
            return AiReasoningResponse.fallbackReviewRequired("JSON parsing failed: " + e.getMessage());
        }
    }
}
