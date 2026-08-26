package com.razorpay.buildathon.recon.evaluation;

import com.razorpay.buildathon.recon.engine.TxnBuilder;
import com.razorpay.buildathon.recon.evaluation.data.GroundTruthRecord;
import com.razorpay.buildathon.recon.evaluation.dto.BenchmarkEvaluationResponse;
import com.razorpay.buildathon.recon.evaluation.dto.CaseEvaluationResult;
import com.razorpay.buildathon.recon.evaluation.service.EvaluationMetricsEngine;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchResult;
import com.razorpay.buildathon.recon.model.MatchStatus;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EvaluationMetricsEngineTest {

    private EvaluationMetricsEngine metricsEngine;

    @BeforeEach
    void setup() {
        metricsEngine = new EvaluationMetricsEngine();
    }

    @Test
    void computeMetrics_calculatesPrecisionMetricsCorrectly() {
        NormalizedTransaction gw1 = TxnBuilder.gateway("GW-1").build();
        NormalizedTransaction gw2 = TxnBuilder.gateway("GW-2").build();

        MatchResult mr1 = new MatchResult();
        mr1.setGatewayTxn(gw1);
        mr1.setStatus(MatchStatus.RECONCILED);
        mr1.setExceptionCategory(ExceptionCategory.NONE);

        MatchResult mr2 = new MatchResult();
        mr2.setGatewayTxn(gw2);
        mr2.setStatus(MatchStatus.EXCEPTION);
        mr2.setExceptionCategory(ExceptionCategory.MISSING_IN_BANK_FILE);

        Map<String, GroundTruthRecord> gtMap = Map.of(
                "GW-1", new GroundTruthRecord("GW-1", MatchStatus.RECONCILED, ExceptionCategory.NONE, "EXACT_MATCH", "Exact match"),
                "GW-2", new GroundTruthRecord("GW-2", MatchStatus.EXCEPTION, ExceptionCategory.MISSING_IN_BANK_FILE, "MISSING_BANK", "Missing bank")
        );

        List<CaseEvaluationResult> caseResults = new ArrayList<>();
        BenchmarkEvaluationResponse.AggregateMetrics metrics =
                metricsEngine.computeMetrics(List.of(mr1, mr2), gtMap, 100L, caseResults);

        assertThat(metrics.totalTransactions()).isEqualTo(2);
        assertThat(metrics.correctlyReconciled()).isEqualTo(1);
        assertThat(metrics.correctExceptions()).isEqualTo(1);
        assertThat(metrics.falsePositives()).isEqualTo(0);
        assertThat(metrics.falseNegatives()).isEqualTo(0);
        assertThat(metrics.matchRatePct()).isEqualTo(50.0);
        assertThat(caseResults).hasSize(2);
    }
}
