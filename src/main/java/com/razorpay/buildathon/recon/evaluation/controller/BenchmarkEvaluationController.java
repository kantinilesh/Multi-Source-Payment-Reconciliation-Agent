package com.razorpay.buildathon.recon.evaluation.controller;

import com.razorpay.buildathon.recon.evaluation.dto.BenchmarkEvaluationResponse;
import com.razorpay.buildathon.recon.evaluation.service.BenchmarkEvaluationService;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * REST controller for triggering Ground Truth benchmark evaluation runs and fetching performance reports.
 */
@RestController
@RequestMapping("/api/evaluation")
public class BenchmarkEvaluationController {

    private final BenchmarkEvaluationService benchmarkService;

    public BenchmarkEvaluationController(BenchmarkEvaluationService benchmarkService) {
        this.benchmarkService = benchmarkService;
    }

    /**
     * Trigger execution of the 60-case synthetic Ground Truth Benchmark.
     * Evaluates Rules-Only Baseline vs Rules + AI Hybrid performance.
     *
     * POST /api/evaluation/run-benchmark
     */
    @PostMapping("/run-benchmark")
    public ResponseEntity<BenchmarkEvaluationResponse> runBenchmark() {
        BenchmarkEvaluationResponse response = benchmarkService.runBenchmark();
        return ResponseEntity.ok(response);
    }

    /**
     * Returns the formatted Markdown evaluation report for the latest benchmark run.
     *
     * GET /api/evaluation/benchmark-report
     */
    @GetMapping(value = "/benchmark-report", produces = MediaType.TEXT_MARKDOWN_VALUE)
    public ResponseEntity<String> getBenchmarkReport() {
        BenchmarkEvaluationResponse response = benchmarkService.runBenchmark();
        return ResponseEntity.ok(response.humanReadableReport());
    }
}
