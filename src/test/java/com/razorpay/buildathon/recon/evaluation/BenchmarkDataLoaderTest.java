package com.razorpay.buildathon.recon.evaluation;

import com.razorpay.buildathon.recon.evaluation.data.BenchmarkDataLoader;
import com.razorpay.buildathon.recon.evaluation.data.GroundTruthRecord;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class BenchmarkDataLoaderTest {

    private BenchmarkDataLoader dataLoader;

    @BeforeEach
    void setup() {
        dataLoader = new BenchmarkDataLoader();
    }

    @Test
    void loadGroundTruth_loads60BenchmarkRecords() {
        Map<String, GroundTruthRecord> groundTruth = dataLoader.loadGroundTruth();

        assertThat(groundTruth).hasSize(60);

        GroundTruthRecord case1 = groundTruth.get("GW-BM-001");
        assertThat(case1).isNotNull();
        assertThat(case1.expectedStatus()).isEqualTo(MatchStatus.RECONCILED);
        assertThat(case1.expectedExceptionCategory()).isEqualTo(ExceptionCategory.NONE);
        assertThat(case1.scenarioCategory()).isEqualTo("EXACT_MATCH");

        GroundTruthRecord case35 = groundTruth.get("GW-BM-035");
        assertThat(case35).isNotNull();
        assertThat(case35.expectedStatus()).isEqualTo(MatchStatus.EXCEPTION);
        assertThat(case35.expectedExceptionCategory()).isEqualTo(ExceptionCategory.MISSING_IN_BANK_FILE);
    }
}
