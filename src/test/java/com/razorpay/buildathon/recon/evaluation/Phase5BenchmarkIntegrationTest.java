package com.razorpay.buildathon.recon.evaluation;

import com.razorpay.buildathon.recon.evaluation.dto.BenchmarkEvaluationResponse;
import com.razorpay.buildathon.recon.evaluation.service.BenchmarkEvaluationService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("test")
class Phase5BenchmarkIntegrationTest {

    @Autowired
    private BenchmarkEvaluationService benchmarkService;

    @Test
    void runBenchmark_evaluates60GroundTruthCasesSuccessfully() {
        BenchmarkEvaluationResponse response = benchmarkService.runBenchmark();

        assertThat(response).isNotNull();
        BenchmarkEvaluationResponse.AggregateMetrics sys = response.systemMetrics();
        assertThat(sys.totalTransactions()).isEqualTo(60);
        assertThat(sys.falsePositives()).isEqualTo(0); // Zero false reconciliations
        assertThat(sys.matchRatePct()).isGreaterThan(0.0);
        assertThat(sys.automationRatePct()).isGreaterThan(0.0);

        BenchmarkEvaluationResponse.AggregateMetrics base = response.baselineMetrics();
        assertThat(base.totalTransactions()).isEqualTo(60);

        BenchmarkEvaluationResponse.ComparisonDelta delta = response.comparisonDelta();
        assertThat(delta.executiveSummary()).isNotBlank();

        assertThat(response.caseBreakdown()).hasSize(60);
        assertThat(response.humanReadableReport()).contains("# AI Finance Controller — Benchmark Evaluation Report");
        assertThat(response.humanReadableReport()).contains("Comparative Benchmark Performance");
    }
}
