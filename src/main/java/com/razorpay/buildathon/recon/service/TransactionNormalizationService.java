package com.razorpay.buildathon.recon.service;

import com.opencsv.CSVReaderHeaderAware;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import com.razorpay.buildathon.recon.model.ReconciliationRun;
import com.razorpay.buildathon.recon.model.SourceType;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStreamReader;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Parses the 3 raw source files into the common {@link NormalizedTransaction} schema.
 * Section 3.3 "Data flow", step 1, and Section 5.1 of the blueprint.
 *
 * This is deliberately dumb, deterministic parsing - no AI here. Ambiguity resolution
 * happens later in the matching engine (Phase 3/4), not at ingestion time.
 */
@Service
public class TransactionNormalizationService {

    private static final DateTimeFormatter ISO_DATE_TIME = DateTimeFormatter.ISO_LOCAL_DATE_TIME;

    public List<NormalizedTransaction> parseGatewayExport(MultipartFile file, ReconciliationRun run) {
        List<NormalizedTransaction> results = new ArrayList<>();
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, String> row;
            while ((row = reader.readMap()) != null) {
                NormalizedTransaction txn = new NormalizedTransaction();
                txn.setRun(run);
                txn.setSourceType(SourceType.GATEWAY);
                txn.setExternalRef(safe(row.get("order_id")));
                txn.setAmount(parseAmount(row.get("amount")));
                txn.setTimestamp(parseTimestamp(row.get("timestamp")));
                txn.setPaymentMethod(safe(row.get("payment_method")));
                txn.setRawRowJson(rowToJson(row));
                results.add(txn);
            }
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to parse gateway export: " + e.getMessage(),
                    new IOException(e));
        }
        return results;
    }

    public List<NormalizedTransaction> parseBankSettlement(MultipartFile file, ReconciliationRun run) {
        List<NormalizedTransaction> results = new ArrayList<>();
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, String> row;
            while ((row = reader.readMap()) != null) {
                NormalizedTransaction txn = new NormalizedTransaction();
                txn.setRun(run);
                txn.setSourceType(SourceType.BANK);
                // Bank reference_note is a HINT, not a guaranteed match key (Section 5.3) -
                // it's frequently truncated/reformatted. The matching engine (Phase 3/4)
                // treats this accordingly; we just store it verbatim here.
                txn.setExternalRef(safe(row.get("reference_note")));
                txn.setAmount(parseAmount(row.get("settled_amount")));
                txn.setTimestamp(parseTimestamp(row.get("settlement_date")));
                txn.setPaymentMethod(null); // not present in bank file
                txn.setRawRowJson(rowToJson(row));
                results.add(txn);
            }
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to parse bank settlement file: " + e.getMessage(),
                    new IOException(e));
        }
        return results;
    }

    public List<NormalizedTransaction> parseInternalLedger(MultipartFile file, ReconciliationRun run) {
        List<NormalizedTransaction> results = new ArrayList<>();
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, String> row;
            while ((row = reader.readMap()) != null) {
                NormalizedTransaction txn = new NormalizedTransaction();
                txn.setRun(run);
                txn.setSourceType(SourceType.LEDGER);
                txn.setExternalRef(safe(row.get("order_ref")));
                txn.setAmount(parseAmount(row.get("amount")));
                // Ledger file only has a date, not a time - normalize to midnight UTC.
                txn.setTimestamp(parseDateOnly(row.get("date")));
                txn.setPaymentMethod(null);
                txn.setRawRowJson(rowToJson(row));
                results.add(txn);
            }
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to parse internal ledger file: " + e.getMessage(),
                    new IOException(e));
        }
        return results;
    }

    // ---- helpers ----

    private String safe(String s) {
        return s == null ? "" : s.trim();
    }

    private BigDecimal parseAmount(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing amount value in row");
        }
        return new BigDecimal(raw.trim());
    }

    private Instant parseTimestamp(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing timestamp value in row");
        }
        String s = raw.trim();
        try {
            return Instant.parse(s);
        } catch (Exception e) {
            if (s.endsWith("Z")) {
                s = s.substring(0, s.length() - 1);
            }
            LocalDateTime ldt = LocalDateTime.parse(s, ISO_DATE_TIME);
            return ldt.toInstant(ZoneOffset.UTC);
        }
    }

    private Instant parseDateOnly(String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("Missing date value in row");
        }
        LocalDate date = LocalDate.parse(raw.trim());
        return date.atStartOfDay().toInstant(ZoneOffset.UTC);
    }

    /** Minimal, dependency-free JSON serialization of a CSV row for audit storage. */
    private String rowToJson(Map<String, String> row) {
        StringBuilder sb = new StringBuilder("{");
        boolean first = true;
        for (Map.Entry<String, String> e : row.entrySet()) {
            if (!first) sb.append(",");
            first = false;
            sb.append("\"").append(escape(e.getKey())).append("\":\"")
                    .append(escape(e.getValue() == null ? "" : e.getValue())).append("\"");
        }
        sb.append("}");
        return sb.toString();
    }

    private String escape(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}
