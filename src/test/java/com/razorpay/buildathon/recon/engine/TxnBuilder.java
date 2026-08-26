package com.razorpay.buildathon.recon.engine;

import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import com.razorpay.buildathon.recon.model.ReconciliationRun;
import com.razorpay.buildathon.recon.model.SourceType;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Test-only builder that creates {@link NormalizedTransaction} instances
 * without needing a database or Spring context. Used by all engine unit tests.
 *
 * IDs are assigned from a static counter so tests don't need to manage them.
 */
public final class TxnBuilder {

    private static final AtomicLong ID_SEQ = new AtomicLong(1);
    private static final ReconciliationRun DUMMY_RUN;

    static {
        DUMMY_RUN = new ReconciliationRun();
        // Don't set an ID — run is not persisted in unit tests
    }

    private SourceType sourceType;
    private String externalRef;
    private BigDecimal amount = new BigDecimal("1000.00");
    private Instant timestamp = Instant.parse("2024-01-15T10:00:00Z");
    private String paymentMethod;
    private String rawJson = "{}";
    private Long id;

    private TxnBuilder(SourceType type, String ref) {
        this.sourceType = type;
        this.externalRef = ref;
        this.id = ID_SEQ.getAndIncrement();
    }

    public static TxnBuilder gateway(String ref) { return new TxnBuilder(SourceType.GATEWAY, ref); }
    public static TxnBuilder bank(String ref)    { return new TxnBuilder(SourceType.BANK, ref); }
    public static TxnBuilder ledger(String ref)  { return new TxnBuilder(SourceType.LEDGER, ref); }

    public TxnBuilder amount(String a)       { this.amount = new BigDecimal(a); return this; }
    public TxnBuilder amount(BigDecimal a)   { this.amount = a; return this; }
    public TxnBuilder timestamp(String ts)   { this.timestamp = Instant.parse(ts); return this; }
    public TxnBuilder timestamp(Instant ts)  { this.timestamp = ts; return this; }
    public TxnBuilder paymentMethod(String m){ this.paymentMethod = m; return this; }
    public TxnBuilder rawJson(String j)      { this.rawJson = j; return this; }
    public TxnBuilder id(Long id)            { this.id = id; return this; }

    /** Convenience: set rawJson with fee and status for GATEWAY transactions. */
    public TxnBuilder withFeeAndStatus(String fee, String status) {
        this.rawJson = String.format(
                "{\"order_id\":\"%s\",\"amount\":\"%s\",\"fee\":\"%s\",\"status\":\"%s\"}",
                externalRef, amount.toPlainString(), fee, status);
        return this;
    }

    /** Convenience: set rawJson with status for LEDGER transactions. */
    public TxnBuilder withStatus(String status) {
        this.rawJson = String.format(
                "{\"order_ref\":\"%s\",\"amount\":\"%s\",\"status\":\"%s\"}",
                externalRef, amount.toPlainString(), status);
        return this;
    }

    public NormalizedTransaction build() {
        NormalizedTransaction t = new NormalizedTransaction();
        // Use reflection or direct field access via setters
        t.setRun(DUMMY_RUN);
        t.setSourceType(sourceType);
        t.setExternalRef(externalRef);
        t.setAmount(amount);
        t.setTimestamp(timestamp);
        t.setPaymentMethod(paymentMethod);
        t.setRawRowJson(rawJson != null ? rawJson : "{}");
        // Simulate a persisted ID for assertions
        setId(t, id);
        return t;
    }

    /** Reflectively sets the @Id field since it has no public setter. */
    private static void setId(NormalizedTransaction t, Long id) {
        try {
            var field = NormalizedTransaction.class.getDeclaredField("id");
            field.setAccessible(true);
            field.set(t, id);
        } catch (Exception e) {
            throw new RuntimeException("Could not set id on NormalizedTransaction", e);
        }
    }
}
