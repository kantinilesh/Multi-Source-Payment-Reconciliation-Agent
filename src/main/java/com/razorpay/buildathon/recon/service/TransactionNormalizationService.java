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
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";
        if (filename.endsWith(".txt") || filename.endsWith(".text")) {
            return parseUnstructuredSlip(file, run);
        }

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
            // Fallback: if user uploaded an unstructured deposit slip or text file with a different name
            try {
                return parseUnstructuredSlip(file, run);
            } catch (Exception fallbackEx) {
                throw new UncheckedIOException("Failed to parse internal ledger file: " + e.getMessage(),
                        new IOException(e));
            }
        }
        return results;
    }

    /**
     * Resilient parser for unstructured bank deposit slips, receipts, and text invoices.
     * Extracts reference ID, settled amount, and settlement date using regex patterns.
     */
    public List<NormalizedTransaction> parseUnstructuredSlip(MultipartFile file, ReconciliationRun run) {
        List<NormalizedTransaction> results = new ArrayList<>();
        try {
            String content = new String(file.getBytes(), StandardCharsets.UTF_8);
            NormalizedTransaction txn = new NormalizedTransaction();
            txn.setRun(run);
            txn.setSourceType(SourceType.LEDGER);

            // Extract Reference (Source Reference, Reference Note, Order Ref, or UTR Number)
            String ref = extractPattern(content, "(?i)(?:Source Reference|Reference Note|order_ref|Order Ref|UTR Number|Reference)\\s*[:=-]\\s*([A-Za-z0-9_-]+)");
            txn.setExternalRef(ref != null ? ref.trim() : "SLIP-" + System.currentTimeMillis());

            // Extract Settled Amount (e.g. "Settled Amount: ₹ 1,464.60" or "Amount: 1500.00")
            BigDecimal amount = extractSlipAmount(content);
            txn.setAmount(amount != null ? amount : BigDecimal.ZERO);

            // Extract Settlement Date (e.g. "12-FEB-2024" or "2024-02-12")
            Instant timestamp = extractSlipDate(content);
            txn.setTimestamp(timestamp != null ? timestamp : Instant.now());

            txn.setPaymentMethod("DEPOSIT_SLIP");
            txn.setRawRowJson("{\"type\":\"UNSTRUCTURED_SLIP\",\"filename\":\"" + escape(safe(file.getOriginalFilename())) + "\",\"ref\":\"" + escape(txn.getExternalRef()) + "\",\"amount\":\"" + txn.getAmount() + "\"}");
            results.add(txn);
        } catch (Exception e) {
            throw new UncheckedIOException("Failed to parse unstructured deposit slip: " + e.getMessage(), new IOException(e));
        }
        return results;
    }

    private BigDecimal extractSlipAmount(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)(?:Settled Amount|Amount|Net Amount|Total)\\s*[:=-]\\s*[₹$€£]?\\s*([0-9,]+\\.[0-9]{2}|[0-9,]+)");
        java.util.regex.Matcher m = pattern.matcher(content);
        if (m.find()) {
            String amtStr = m.group(1).replace(",", "").trim();
            try {
                return new BigDecimal(amtStr);
            } catch (Exception ignored) {}
        }
        return BigDecimal.ZERO;
    }

    private Instant extractSlipDate(String content) {
        java.util.regex.Pattern pattern = java.util.regex.Pattern.compile("(?i)(?:Settlement Date|Date|Deposit Date)\\s*[:=-]\\s*([0-9]{1,4}[-/][A-Za-z0-9]{2,4}[-/][0-9]{1,4})");
        java.util.regex.Matcher m = pattern.matcher(content);
        if (m.find()) {
            String dateStr = m.group(1).trim();
            // Try ISO yyyy-MM-dd
            try {
                return LocalDate.parse(dateStr).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {}
            // Try d-MMM-yyyy (e.g., 12-FEB-2024)
            try {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("d-MMM-yyyy", java.util.Locale.ENGLISH);
                return LocalDate.parse(dateStr, dtf).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {}
            // Try d/M/yyyy
            try {
                DateTimeFormatter dtf = DateTimeFormatter.ofPattern("d/M/yyyy", java.util.Locale.ENGLISH);
                return LocalDate.parse(dateStr, dtf).atStartOfDay().toInstant(ZoneOffset.UTC);
            } catch (Exception ignored) {}
        }
        return Instant.now();
    }

    private String extractPattern(String content, String regex) {
        java.util.regex.Matcher m = java.util.regex.Pattern.compile(regex).matcher(content);
        if (m.find()) {
            return m.group(1).trim();
        }
        return null;
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
