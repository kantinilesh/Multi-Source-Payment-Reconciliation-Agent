package com.razorpay.buildathon.recon.engine;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.math.BigDecimal;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Utility that parses the {@code rawRowJson} blob stored on a
 * {@link NormalizedTransaction} back into a field map so that the matching
 * engine can inspect source-specific fields (gateway fee, payment status, etc.)
 * without introducing separate columns for every field from every source.
 *
 * This is intentionally a stateless utility class — no Spring bean needed.
 * The ObjectMapper is shared (thread-safe) across all calls.
 */
public final class RawFieldExtractor {

    private static final Logger log = LoggerFactory.getLogger(RawFieldExtractor.class);
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private RawFieldExtractor() {}

    /**
     * Returns all raw key-value pairs from a transaction's {@code rawRowJson}.
     * Returns an empty map if parsing fails (fail-soft: the engine can still
     * attempt a match with fewer signals rather than crashing the entire run).
     */
    public static Map<String, String> extractAll(NormalizedTransaction txn) {
        if (txn == null || txn.getRawRowJson() == null || txn.getRawRowJson().isBlank()) {
            return Collections.emptyMap();
        }
        try {
            return MAPPER.readValue(txn.getRawRowJson(),
                    new TypeReference<HashMap<String, String>>() {});
        } catch (Exception e) {
            log.warn("Could not parse rawRowJson for txn id={}: {}", txn.getId(), e.getMessage());
            return Collections.emptyMap();
        }
    }

    /**
     * Extracts the gateway fee from a GATEWAY transaction's rawRowJson.
     * Returns {@link BigDecimal#ZERO} if the field is missing or unparseable.
     * Gateway CSV column: {@code fee}.
     */
    public static BigDecimal extractGatewayFee(NormalizedTransaction txn) {
        String raw = extractAll(txn).getOrDefault("fee", null);
        if (raw == null || raw.isBlank()) return BigDecimal.ZERO;
        try {
            return new BigDecimal(raw.trim());
        } catch (NumberFormatException e) {
            log.warn("Unparseable fee '{}' on txn id={}", raw, txn.getId());
            return BigDecimal.ZERO;
        }
    }

    /**
     * Extracts the payment status string from either a GATEWAY or LEDGER transaction.
     * Gateway CSV column: {@code status}. Ledger CSV column: {@code status}.
     * Returns an empty string if absent.
     */
    public static String extractStatus(NormalizedTransaction txn) {
        Map<String, String> fields = extractAll(txn);
        // Try 'status' first (gateway, ledger); fall back to 'transaction_status' if present
        String status = fields.getOrDefault("status", fields.getOrDefault("transaction_status", ""));
        return status == null ? "" : status.trim().toUpperCase();
    }

    /**
     * Extracts the source-system-specific transaction ID when available.
     * Gateway: {@code transaction_id}. Bank: {@code transaction_id}.
     * Returns empty string if absent.
     */
    public static String extractTransactionId(NormalizedTransaction txn) {
        String val = extractAll(txn).getOrDefault("transaction_id", "");
        return val == null ? "" : val.trim();
    }

    /**
     * Strips common source-system prefixes and returns only the numeric/alphanumeric
     * core of a reference string so that GW-83921, SET-83921, PAY-83921 all yield "83921".
     *
     * Rules (applied in order):
     *   1. If the ref contains a dash, take everything after the last dash.
     *   2. Strip leading non-alphanumeric characters.
     *   3. Return the remainder in upper-case.
     *
     * Returns the original (trimmed, upper-case) string if no prefix can be stripped.
     */
    public static String extractNumericCore(String ref) {
        if (ref == null || ref.isBlank()) return "";
        String trimmed = ref.trim().toUpperCase();
        int dashIdx = trimmed.lastIndexOf('-');
        if (dashIdx >= 0 && dashIdx < trimmed.length() - 1) {
            return trimmed.substring(dashIdx + 1);
        }
        // Strip leading alpha prefix (e.g. "ORDER7821" → "7821")
        int i = 0;
        while (i < trimmed.length() && Character.isLetter(trimmed.charAt(i))) i++;
        return i > 0 && i < trimmed.length() ? trimmed.substring(i) : trimmed;
    }
}
