package com.razorpay.buildathon.recon.model;

import jakarta.persistence.*;
import java.math.BigDecimal;
import java.time.Instant;

/**
 * One record, from ONE source system, after parsing/normalization.
 * Raw values are preserved alongside normalized ones so the audit trail
 * can always show exactly what was originally on the file.
 */
@Entity
@Table(name = "normalized_transaction", indexes = {
        @Index(name = "idx_txn_run_source", columnList = "run_id, sourceType"),
        @Index(name = "idx_txn_external_ref", columnList = "externalRef")
})
public class NormalizedTransaction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SourceType sourceType;

    /**
     * Best-effort normalized reference id. For GATEWAY this is order_id verbatim.
     * For BANK this is the (possibly mangled) reference_note. For LEDGER this is
     * order_ref. The matching engine treats this as a hint, not ground truth,
     * because bank references are frequently truncated/reformatted.
     */
    @Column(nullable = false)
    private String externalRef;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal amount;

    @Column(nullable = false)
    private Instant timestamp;

    private String paymentMethod;

    /** Original, unparsed row - kept verbatim for the audit trail. */
    @Lob
    @Column(nullable = false)
    private String rawRowJson;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public NormalizedTransaction() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ReconciliationRun getRun() { return run; }
    public void setRun(ReconciliationRun run) { this.run = run; }

    public SourceType getSourceType() { return sourceType; }
    public void setSourceType(SourceType sourceType) { this.sourceType = sourceType; }

    public String getExternalRef() { return externalRef; }
    public void setExternalRef(String externalRef) { this.externalRef = externalRef; }

    public BigDecimal getAmount() { return amount; }
    public void setAmount(BigDecimal amount) { this.amount = amount; }

    public Instant getTimestamp() { return timestamp; }
    public void setTimestamp(Instant timestamp) { this.timestamp = timestamp; }

    public String getPaymentMethod() { return paymentMethod; }
    public void setPaymentMethod(String paymentMethod) { this.paymentMethod = paymentMethod; }

    public String getRawRowJson() { return rawRowJson; }
    public void setRawRowJson(String rawRowJson) { this.rawRowJson = rawRowJson; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
