package com.razorpay.buildathon.recon.service;

import com.opencsv.CSVReaderHeaderAware;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import com.razorpay.buildathon.recon.model.ReconciliationRun;
import com.razorpay.buildathon.recon.model.SourceType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Service that ingests custom invoices, vendor bills, and receipt documents
 * (CSV, Text, or PDF bill exports) and maps them into {@link NormalizedTransaction} records.
 */
@Service
public class BillNormalizationService {

    private static final Logger log = LoggerFactory.getLogger(BillNormalizationService.class);

    private static final Pattern INVOICE_REF_PATTERN = Pattern.compile("(?i)(INV|BILL|REC|REF|VCH|SET|order|pay|UTR)[-:\\s#]*([A-Z0-9_-]+)");
    private static final Pattern AMOUNT_PATTERN = Pattern.compile("(?i)(AMOUNT|TOTAL|NET|GROSS|SETTLED|RS|₹)[-:\\s]*([0-9,]+\\.[0-9]{2}|[0-9,]+)");

    /**
     * Parses custom invoice/bill files (CSV or unstructured bill text).
     */
    public List<NormalizedTransaction> parseBillDocument(MultipartFile file, ReconciliationRun run) {
        List<NormalizedTransaction> results = new ArrayList<>();
        String filename = file.getOriginalFilename() != null ? file.getOriginalFilename().toLowerCase() : "";

        if (filename.endsWith(".csv")) {
            return parseBillCsv(file, run);
        } else {
            return parseBillText(file, run);
        }
    }

    private List<NormalizedTransaction> parseBillCsv(MultipartFile file, ReconciliationRun run) {
        List<NormalizedTransaction> results = new ArrayList<>();
        try (CSVReaderHeaderAware reader = new CSVReaderHeaderAware(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            Map<String, String> row;
            while ((row = reader.readMap()) != null) {
                String ref = extractKey(row, "invoice_no", "bill_no", "receipt_id", "reference", "order_ref");
                String amtStr = extractKey(row, "amount", "total", "bill_amount", "gross_amount");

                if (ref == null || amtStr == null) continue;

                NormalizedTransaction txn = new NormalizedTransaction();
                txn.setRun(run);
                txn.setSourceType(SourceType.LEDGER);
                txn.setExternalRef(ref.trim());
                txn.setAmount(new BigDecimal(amtStr.replaceAll("[^0-9.]", "")));
                txn.setTimestamp(Instant.now());
                txn.setPaymentMethod("BILL/INVOICE");
                txn.setRawRowJson("{\"invoice_ref\":\"" + ref + "\",\"source\":\"bill_upload\"}");
                results.add(txn);
            }
        } catch (Exception e) {
            log.warn("Falling back to text bill extraction for {}: {}", file.getOriginalFilename(), e.getMessage());
            return parseBillText(file, run);
        }
        return results;
    }

    private List<NormalizedTransaction> parseBillText(MultipartFile file, ReconciliationRun run) {
        List<NormalizedTransaction> results = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            String foundRef = null;
            BigDecimal foundAmount = null;

            while ((line = reader.readLine()) != null) {
                if (foundRef == null) {
                    Matcher refMatch = INVOICE_REF_PATTERN.matcher(line);
                    if (refMatch.find()) {
                        foundRef = refMatch.group(1) + "-" + refMatch.group(2);
                    }
                }
                if (foundAmount == null) {
                    Matcher amtMatch = AMOUNT_PATTERN.matcher(line);
                    if (amtMatch.find()) {
                        try {
                            foundAmount = new BigDecimal(amtMatch.group(2).replaceAll(",", ""));
                        } catch (Exception ignored) {}
                    }
                }
            }

            if (foundRef != null && foundAmount != null) {
                NormalizedTransaction txn = new NormalizedTransaction();
                txn.setRun(run);
                txn.setSourceType(SourceType.LEDGER);
                txn.setExternalRef(foundRef);
                txn.setAmount(foundAmount);
                txn.setTimestamp(Instant.now());
                txn.setPaymentMethod("INVOICE_OCR");
                txn.setRawRowJson("{\"filename\":\"" + file.getOriginalFilename() + "\"}");
                results.add(txn);
            }
        } catch (Exception e) {
            log.error("Failed to parse bill document {}: {}", file.getOriginalFilename(), e.getMessage());
        }
        return results;
    }

    private String extractKey(Map<String, String> row, String... keys) {
        for (Map.Entry<String, String> entry : row.entrySet()) {
            String k = entry.getKey().toLowerCase().replace("_", "").replace(" ", "");
            for (String key : keys) {
                if (k.contains(key.replace("_", ""))) {
                    return entry.getValue();
                }
            }
        }
        return null;
    }
}
