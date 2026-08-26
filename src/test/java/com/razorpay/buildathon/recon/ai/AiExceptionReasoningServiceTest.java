package com.razorpay.buildathon.recon.ai;

import com.razorpay.buildathon.recon.ai.config.ReconAiConfig;
import com.razorpay.buildathon.recon.ai.guardrail.AiResponseGuardrail;
import com.razorpay.buildathon.recon.ai.provider.MockLlmClient;
import com.razorpay.buildathon.recon.ai.service.AiExceptionReasoningService;
import com.razorpay.buildathon.recon.ai.tools.ReconciliationAgentTools;
import com.razorpay.buildathon.recon.engine.TxnBuilder;
import com.razorpay.buildathon.recon.model.*;
import com.razorpay.buildathon.recon.repository.AuditLogEntryRepository;
import com.razorpay.buildathon.recon.repository.MatchResultRepository;
import com.razorpay.buildathon.recon.repository.NormalizedTransactionRepository;
import com.razorpay.buildathon.recon.repository.ReconciliationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AiExceptionReasoningServiceTest {

    @Mock private MatchResultRepository matchResultRepo;
    @Mock private AuditLogEntryRepository auditRepo;
    @Mock private ReconciliationRunRepository runRepo;
    @Mock private NormalizedTransactionRepository transactionRepo;

    private ReconciliationAgentTools agentTools;
    private MockLlmClient mockLlmClient;
    private AiResponseGuardrail guardrail;
    private AiExceptionReasoningService service;

    @BeforeEach
    void setup() {
        ReconAiConfig config = new ReconAiConfig();
        mockLlmClient = new MockLlmClient();
        guardrail = new AiResponseGuardrail(config);
        agentTools = new ReconciliationAgentTools(transactionRepo, matchResultRepo, runRepo);

        service = new AiExceptionReasoningService(
                matchResultRepo, auditRepo, runRepo, agentTools, mockLlmClient, guardrail
        );
    }

    @Test
    void analyzeSingleMatch_missingBank_resolvesToExceptionMissingBank() {
        NormalizedTransaction gw = TxnBuilder.gateway("GW-105").amount("3200.00").build();
        NormalizedTransaction lg = TxnBuilder.ledger("PAY-105").amount("3200.00").build();

        MatchResult mr = new MatchResult();
        mr.setId(5L);
        mr.setStatus(MatchStatus.REVIEW_REQUIRED);
        mr.setGatewayTxn(gw);
        mr.setLedgerTxn(lg);

        lenient().when(transactionRepo.findByRunId(any())).thenReturn(List.of(gw, lg));

        List<AuditLogEntry> auditSink = new ArrayList<>();
        MatchResult updated = service.analyzeSingleMatch(mr, auditSink);

        assertThat(updated.getStatus()).isEqualTo(MatchStatus.EXCEPTION);
        assertThat(updated.getExceptionCategory()).isEqualTo(ExceptionCategory.MISSING_IN_BANK_FILE);
        assertThat(updated.getMethod()).isEqualTo(MatchMethod.AI_ASSISTED);
        assertThat(updated.getConfidence()).isEqualTo(0.90);
        assertThat(auditSink).hasSize(1);
        assertThat(auditSink.get(0).getMethod()).isEqualTo(MatchMethod.AI_ASSISTED);
    }

    @Test
    void processRun_preservesReconciledAndOnlyProcessesAmbiguous() {
        ReconciliationRun run = new ReconciliationRun();
        run.setId(1L);

        NormalizedTransaction gw1 = TxnBuilder.gateway("GW-101").build();
        MatchResult mr1 = new MatchResult();
        mr1.setId(1L);
        mr1.setRun(run);
        mr1.setStatus(MatchStatus.RECONCILED);
        mr1.setMethod(MatchMethod.RULE_EXACT); // Reconciled deterministically - should NOT be touched

        MatchResult mr2 = new MatchResult();
        mr2.setId(2L);
        mr2.setRun(run);
        mr2.setStatus(MatchStatus.REVIEW_REQUIRED);
        mr2.setGatewayTxn(gw1);

        when(runRepo.findById(1L)).thenReturn(Optional.of(run));
        when(matchResultRepo.findByRunIdWithTxns(1L)).thenReturn(List.of(mr1, mr2));
        when(runRepo.save(any())).thenReturn(run);

        ReconciliationRun resultRun = service.processRun(1L);

        assertThat(resultRun.getAiAssistedCount()).isEqualTo(1);
        verify(matchResultRepo).saveAll(any());
    }
}
