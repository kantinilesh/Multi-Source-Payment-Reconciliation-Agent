package com.razorpay.buildathon.recon.evaluation.data;

import com.opencsv.CSVReader;
import com.razorpay.buildathon.recon.model.ExceptionCategory;
import com.razorpay.buildathon.recon.model.MatchStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStreamReader;
import java.io.Reader;
import java.nio.charset.StandardCharsets;
import java.util.*;

/**
 * Loads synthetic benchmark CSV files and ground truth expectations from classpath resources.
 */
@Component
public class BenchmarkDataLoader {

    private static final Logger log = LoggerFactory.getLogger(BenchmarkDataLoader.class);

    private static final String DEFAULT_GROUND_TRUTH_PATH = "benchmark/ground_truth_benchmark.csv";
    private static final String DEFAULT_GATEWAY_PATH = "benchmark/gateway_benchmark.csv";
    private static final String DEFAULT_BANK_PATH = "benchmark/bank_benchmark.csv";
    private static final String DEFAULT_LEDGER_PATH = "benchmark/ledger_benchmark.csv";

    /**
     * Loads ground truth expectations indexed by gateway transaction reference.
     */
    public Map<String, GroundTruthRecord> loadGroundTruth() {
        return loadGroundTruth(DEFAULT_GROUND_TRUTH_PATH);
    }

    public Map<String, GroundTruthRecord> loadGroundTruth(String resourcePath) {
        Map<String, GroundTruthRecord> map = new LinkedHashMap<>();
        try {
            ClassPathResource resource = new ClassPathResource(resourcePath);
            try (Reader reader = new InputStreamReader(resource.getInputStream(), StandardCharsets.UTF_8);
                 CSVReader csvReader = new CSVReader(reader)) {

                String[] header = csvReader.readNext(); // skip header
                String[] row;
                while ((row = csvReader.readNext()) != null) {
                    if (row.length < 5) continue;

                    String ref = row[0].trim();
                    MatchStatus status = MatchStatus.valueOf(row[1].trim().toUpperCase());
                    ExceptionCategory category = ExceptionCategory.valueOf(row[2].trim().toUpperCase());
                    String scenario = row[3].trim();
                    String desc = row[4].trim();

                    map.put(ref, new GroundTruthRecord(ref, status, category, scenario, desc));
                }
            }
            log.info("Loaded {} ground truth records from {}", map.size(), resourcePath);
        } catch (Exception e) {
            log.error("Failed to load ground truth CSV from {}: {}", resourcePath, e.getMessage(), e);
            throw new RuntimeException("Could not load ground truth data: " + e.getMessage(), e);
        }
        return map;
    }

    public ClassPathResource getGatewayResource() {
        return new ClassPathResource(DEFAULT_GATEWAY_PATH);
    }

    public ClassPathResource getBankResource() {
        return new ClassPathResource(DEFAULT_BANK_PATH);
    }

    public ClassPathResource getLedgerResource() {
        return new ClassPathResource(DEFAULT_LEDGER_PATH);
    }
}
