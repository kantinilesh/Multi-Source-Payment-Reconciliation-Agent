package com.razorpay.buildathon.recon.ai;

import com.razorpay.buildathon.recon.ai.tools.ReconciliationAgentTools;
import com.razorpay.buildathon.recon.engine.TxnBuilder;
import com.razorpay.buildathon.recon.model.MatchResult;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import com.razorpay.buildathon.recon.model.ReconciliationRun;
import com.razorpay.buildathon.recon.repository.MatchResultRepository;
import com.razorpay.buildathon.recon.repository.NormalizedTransactionRepository;
import com.razorpay.buildathon.recon.repository.ReconciliationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReconciliationAgentToolsTest {

    @Mock private NormalizedTransactionRepository transactionRepo;
    @Mock private MatchResultRepository matchResultRepo;
    @Mock private ReconciliationRunRepository runRepo;

    private ReconciliationAgentTools tools;

    @BeforeEach
    void setup() {
        tools = new ReconciliationAgentTools(transactionRepo, matchResultRepo, runRepo);
    }

    @Test
    void getTransaction_returnsTransactionWhenFound() {
        NormalizedTransaction txn = TxnBuilder.gateway("GW-101").build();
        when(transactionRepo.findById(101L)).thenReturn(Optional.of(txn));

        Optional<NormalizedTransaction> result = tools.getTransaction(101L);
        assertThat(result).isPresent();
        assertThat(result.get().getExternalRef()).isEqualTo("GW-101");
    }

    @Test
    void getTransaction_nullId_returnsEmpty() {
        assertThat(tools.getTransaction(null)).isEmpty();
    }

    @Test
    void getRelatedTransactions_findsMatchingCoreInRun() {
        NormalizedTransaction gw = TxnBuilder.gateway("GW-83921").build();
        NormalizedTransaction bk = TxnBuilder.bank("SET-83921").build();
        NormalizedTransaction other = TxnBuilder.bank("SET-99999").build();

        when(transactionRepo.findByRunId(1L)).thenReturn(List.of(gw, bk, other));

        List<NormalizedTransaction> matches = tools.getRelatedTransactions(1L, "GW-83921");
        assertThat(matches).hasSize(2);
        assertThat(matches).extracting(NormalizedTransaction::getExternalRef)
                .containsExactlyInAnyOrder("GW-83921", "SET-83921");
    }

    @Test
    void compareCandidates_buildsComparisonMap() {
        NormalizedTransaction gw = TxnBuilder.gateway("GW-101").amount("1000.00").withFeeAndStatus("30.00", "SUCCESS").build();
        NormalizedTransaction bk = TxnBuilder.bank("SET-101").amount("970.00").build();

        MatchResult mr = new MatchResult();
        mr.setId(10L);
        mr.setGatewayTxn(gw);
        mr.setBankTxn(bk);

        Map<String, Object> comp = tools.compareCandidates(mr);
        assertThat(comp).containsKey("gateway");
        assertThat(comp).containsKey("bank");
        assertThat(comp).doesNotContainKey("ledger");
    }

    @Test
    void calculateExpectedSettlement_computesNetAndPercentage() {
        Map<String, Object> res = tools.calculateExpectedSettlement(new BigDecimal("1000.00"), new BigDecimal("30.00"));
        assertThat(res.get("expectedNetSettlement")).isEqualTo(new BigDecimal("970.00"));
        assertThat(res.get("feePercentage")).isEqualTo(new BigDecimal("3.00"));
    }

    @Test
    void getFeeInformation_extractsFeeAndStatus() {
        NormalizedTransaction gw = TxnBuilder.gateway("GW-101").amount("1000.00").withFeeAndStatus("25.00", "SUCCESS").build();
        Map<String, Object> info = tools.getFeeInformation(gw);
        assertThat(info.get("fee")).isEqualTo(new BigDecimal("25.00"));
        assertThat(info.get("status")).isEqualTo("SUCCESS");
    }

    @Test
    void getRunContext_returnsRunSummary() {
        ReconciliationRun run = new ReconciliationRun();
        run.setId(1L);
        run.setGatewayFileName("gateway.csv");

        when(runRepo.findById(1L)).thenReturn(Optional.of(run));
        when(transactionRepo.findByRunId(1L)).thenReturn(List.of(
                TxnBuilder.gateway("GW-1").build(),
                TxnBuilder.bank("SET-1").build()
        ));

        Map<String, Object> ctx = tools.getRunContext(1L);
        assertThat(ctx.get("totalNormalizedTransactions")).isEqualTo(2);
        assertThat(ctx.get("gatewayFileName")).isEqualTo("gateway.csv");
    }
}
