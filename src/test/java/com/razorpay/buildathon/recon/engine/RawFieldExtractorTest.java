package com.razorpay.buildathon.recon.engine;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for {@link RawFieldExtractor}.
 *
 * These tests verify the core string manipulation logic without any Spring context.
 * All test cases are deterministic: given an input, the output is always the same.
 */
class RawFieldExtractorTest {

    // -------------------------------------------------------------------------
    // extractNumericCore
    // -------------------------------------------------------------------------

    @ParameterizedTest(name = "ref=''{0}'' → core=''{1}''")
    @CsvSource({
            "GW-83921,  83921",
            "SET-83921, 83921",
            "PAY-83921, 83921",
            "ORD-7821,  7821",
            "ORDER-7821, 7821",
            "TX001,     001",    // no dash → strip leading alpha prefix TX → 001
            "83921,     83921",  // already plain numeric
            ",          ''",     // null / blank → empty
            "  ,        ''",
    })
    void extractNumericCore_stripsKnownPrefixes(String input, String expected) {
        String actual = RawFieldExtractor.extractNumericCore(
                input == null ? null : input.trim());
        assertThat(actual).isEqualTo(expected.trim());
    }

    @Test
    void extractNumericCore_alphaPrefix_stripped() {
        // "ORDER7821" → leading alpha "ORDER" stripped → "7821"
        assertThat(RawFieldExtractor.extractNumericCore("ORDER7821")).isEqualTo("7821");
    }

    @Test
    void extractNumericCore_null_returnsEmpty() {
        assertThat(RawFieldExtractor.extractNumericCore(null)).isEqualTo("");
    }

    // -------------------------------------------------------------------------
    // extractGatewayFee
    // -------------------------------------------------------------------------

    @Test
    void extractGatewayFee_parsesCorrectly() {
        var txn = TxnBuilder.gateway("GW-001")
                .rawJson("{\"order_id\":\"GW-001\",\"amount\":\"1000.00\",\"fee\":\"30.00\",\"status\":\"SUCCESS\"}")
                .build();
        assertThat(RawFieldExtractor.extractGatewayFee(txn))
                .isEqualByComparingTo("30.00");
    }

    @Test
    void extractGatewayFee_missingField_returnsZero() {
        var txn = TxnBuilder.gateway("GW-001")
                .rawJson("{\"order_id\":\"GW-001\",\"amount\":\"1000.00\"}")
                .build();
        assertThat(RawFieldExtractor.extractGatewayFee(txn))
                .isEqualByComparingTo("0");
    }

    @Test
    void extractGatewayFee_badValue_returnsZero() {
        var txn = TxnBuilder.gateway("GW-001")
                .rawJson("{\"order_id\":\"GW-001\",\"fee\":\"not-a-number\"}")
                .build();
        assertThat(RawFieldExtractor.extractGatewayFee(txn))
                .isEqualByComparingTo("0");
    }

    // -------------------------------------------------------------------------
    // extractStatus
    // -------------------------------------------------------------------------

    @Test
    void extractStatus_returnsUpperCase() {
        var txn = TxnBuilder.gateway("GW-001")
                .rawJson("{\"status\":\"success\"}")
                .build();
        assertThat(RawFieldExtractor.extractStatus(txn)).isEqualTo("SUCCESS");
    }

    @Test
    void extractStatus_missing_returnsEmpty() {
        var txn = TxnBuilder.gateway("GW-001")
                .rawJson("{\"order_id\":\"GW-001\"}")
                .build();
        assertThat(RawFieldExtractor.extractStatus(txn)).isEqualTo("");
    }

    // -------------------------------------------------------------------------
    // extractAll fail-soft
    // -------------------------------------------------------------------------

    @Test
    void extractAll_malformedJson_returnsEmpty() {
        var txn = TxnBuilder.gateway("GW-001")
                .rawJson("not-json-at-all")
                .build();
        assertThat(RawFieldExtractor.extractAll(txn)).isEmpty();
    }

    @Test
    void extractAll_nullRawJson_returnsEmpty() {
        var txn = TxnBuilder.gateway("GW-001")
                .rawJson(null)
                .build();
        assertThat(RawFieldExtractor.extractAll(txn)).isEmpty();
    }
}
