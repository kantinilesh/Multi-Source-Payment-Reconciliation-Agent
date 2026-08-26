package com.razorpay.buildathon.recon.ai.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.buildathon.recon.ai.dto.AiReasoningRequest;
import com.razorpay.buildathon.recon.ai.dto.AiReasoningResponse;
import com.razorpay.buildathon.recon.ai.guardrail.AiResponseGuardrail;
import com.razorpay.buildathon.recon.ai.provider.LlmClient;
import com.razorpay.buildathon.recon.ai.tools.ReconciliationAgentTools;
import com.razorpay.buildathon.recon.engine.RawFieldExtractor;
import com.razorpay.buildathon.recon.model.*;
import com.razorpay.buildathon.recon.repository.AuditLogEntryRepository;
import com.razorpay.buildathon.recon.repository.MatchResultRepository;
import com.razorpay.buildathon.recon.repository.ReconciliationRunRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Orchestrates Phase 4 AI Exception Reasoning.
 * Invoked on ambiguous or unresolved match results (REVIEW_REQUIRED or EXCEPTION).
 * High-confidence deterministic matches (RECONCILED) are NEVER passed to the LLM.
 */
@Service
public class AiExceptionReasoningService {

    private static final Logger log = LoggerFactory.getLogger(AiExceptionReasoningService.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private final MatchResultRepository matchResultRepository;
    private final AuditLogEntryRepository auditLogEntryRepository;
    private final ReconciliationRunRepository runRepository;
    private final ReconciliationAgentTools agentTools;
    private final LlmClient llmClient;
    private final AiResponseGuardrail guardrail;

    public AiExceptionReasoningService(MatchResultRepository matchResultRepository,
                                       AuditLogEntryRepository auditLogEntryRepository,
                                       ReconciliationRunRepository runRepository,
                                       ReconciliationAgentTools agentTools,
                                       LlmClient llmClient,
                                       AiResponseGuardrail guardrail) {
        this.matchResultRepository = matchResultRepository;
        this.auditLogEntryRepository = auditLogEntryRepository;
        this.runRepository = runRepository;
        this.agentTools = agentTools;
        this.llmClient = llmClient;
        this.guardrail = guardrail;
    }

    /**
     * Executes AI reasoning on all ambiguous/unresolved match results for a run.
     */
    @Transactional
    public ReconciliationRun processRun(Long runId) {
        ReconciliationRun run = runRepository.findById(runId)
                .orElseThrow(() -> new IllegalArgumentException("No such run: " + runId));

        List<MatchResult> allMatches = matchResultRepository.findByRunIdWithTxns(runId);
        log.info("AiExceptionReasoningService starting for run={}: total matches={}", runId, allMatches.size());

        int aiProcessed = 0;
        List<AuditLogEntry> newAuditEntries = new ArrayList<>();

        for (MatchResult mr : allMatches) {
            // High-confidence deterministic RECONCILED matches and conclusive EXCEPTIONs are preserved
            if (mr.getStatus() != MatchStatus.REVIEW_REQUIRED) {
                continue;
            }

            // Execute AI reasoning on ambiguous REVIEW_REQUIRED items
            MatchResult updated = analyzeSingleMatch(mr, newAuditEntries);
            if (updated != null) {
                aiProcessed++;
            }
        }

        matchResultRepository.saveAll(allMatches);
        if (!newAuditEntries.isEmpty()) {
            auditLogEntryRepository.saveAll(newAuditEntries);
        }

        // Re-calculate run summary metrics
        Map<MatchStatus, Long> counts = allMatches.stream()
                .collect(Collectors.groupingBy(MatchResult::getStatus, Collectors.counting()));

        long reconciledCount = counts.getOrDefault(MatchStatus.RECONCILED, 0L);
        long reviewCount = counts.getOrDefault(MatchStatus.REVIEW_REQUIRED, 0L);
        long exceptionCount = counts.getOrDefault(MatchStatus.EXCEPTION, 0L);
        long total = allMatches.size();

        run.setMatchedCount((int) reconciledCount);
        run.setPartiallyMatchedCount((int) reviewCount);
        run.setExceptionCount((int) exceptionCount);
        run.setAiAssistedCount(aiProcessed);
        run.setMatchRatePct(total > 0 ? (reconciledCount * 100.0 / total) : 0.0);
        run.setAutomationRatePct(total > 0 ? ((reconciledCount + reviewCount) * 100.0 / total) : 0.0);
        run.setCompletedAt(Instant.now());
        run = runRepository.save(run);

        log.info("AiExceptionReasoningService complete for run={}: processed {} items with AI. Reconciled={}, Review={}, Exceptions={}",
                runId, aiProcessed, reconciledCount, reviewCount, exceptionCount);

        return run;
    }

    /**
     * Executes AI reasoning on a single MatchResult (for on-demand / ai-explain API calls).
     */
    @Transactional
    public MatchResult analyzeSingleMatch(MatchResult mr, List<AuditLogEntry> auditSink) {
        log.info("AI reasoning invoked for matchResultId={}", mr.getId());

        NormalizedTransaction gw = mr.getGatewayTxn();
        String refToSearch = gw != null ? gw.getExternalRef() :
                (mr.getBankTxn() != null ? mr.getBankTxn().getExternalRef() : "");

        // 1. Gather context via Controlled Agent Tools
        List<NormalizedTransaction> related = agentTools.getRelatedTransactions(
                mr.getRun() != null ? mr.getRun().getId() : null, refToSearch);

        List<NormalizedTransaction> relBank = related.stream()
                .filter(t -> t.getSourceType() == SourceType.BANK).toList();
        List<NormalizedTransaction> relLedger = related.stream()
                .filter(t -> t.getSourceType() == SourceType.LEDGER).toList();
        List<NormalizedTransaction> relGw = related.stream()
                .filter(t -> t.getSourceType() == SourceType.GATEWAY).toList();

        String feeJson = "{}";
        if (gw != null) {
            try {
                feeJson = MAPPER.writeValueAsString(agentTools.getFeeInformation(gw));
            } catch (Exception e) {
                feeJson = "{}";
            }
        }

        String runSummary = "";
        if (mr.getRun() != null) {
            try {
                runSummary = MAPPER.writeValueAsString(agentTools.getRunContext(mr.getRun().getId()));
            } catch (Exception e) {
                runSummary = "";
            }
        }

        // 2. Build sanitized AI Reasoning Request
        AiReasoningRequest request = AiReasoningRequest.from(
                mr, relGw, relBank, relLedger, feeJson, runSummary);

        // 3. Invoke LLM Provider
        AiReasoningResponse rawResponse = llmClient.analyze(request);

        // 4. Validate through Security & Hallucination Guardrails
        AiReasoningResponse validatedResponse = guardrail.validate(request, rawResponse);

        // 5. Update MatchResult
        mr.setStatus(validatedResponse.decision());
        mr.setMethod(MatchMethod.AI_ASSISTED);
        mr.setConfidence(validatedResponse.confidence());
        if (validatedResponse.exceptionCategory() != null && validatedResponse.exceptionCategory() != ExceptionCategory.NONE) {
            mr.setExceptionCategory(validatedResponse.exceptionCategory());
        }
        mr.setReasoning(validatedResponse.probableReason() + " Evidence: " +
                String.join(" | ", validatedResponse.evidence()) +
                " Recommended Action: " + validatedResponse.recommendedAction());

        // 6. Write Audit Log Entry
        AuditLogEntry audit = new AuditLogEntry();
        audit.setRun(mr.getRun());
        audit.setMatchResult(mr);
        audit.setMethod(MatchMethod.AI_ASSISTED);
        audit.setConfidence(validatedResponse.confidence());
        audit.setInputsConsidered("Provider: " + llmClient.getProviderName() +
                "; Tools Used: getTransaction, getRelatedTransactions, compareCandidates, getFeeInformation; " +
                "Input Ref: " + guardrail.sanitizeInput(refToSearch) +
                "; Related Candidates Count: bank=" + relBank.size() + ", ledger=" + relLedger.size());
        audit.setReasoning("AI Decision: " + validatedResponse.decision() +
                " (Confidence: " + String.format("%.2f", validatedResponse.confidence()) + ")" +
                " Reason: " + validatedResponse.probableReason() +
                " Action: " + validatedResponse.recommendedAction());

        if (auditSink != null) {
            auditSink.add(audit);
        } else {
            auditLogEntryRepository.save(audit);
        }

        return mr;
    }
}
