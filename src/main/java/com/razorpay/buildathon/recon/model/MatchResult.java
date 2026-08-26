package com.razorpay.buildathon.recon.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * The outcome of trying to reconcile one transaction across up to 3 sources.
 */
@Entity
@Table(name = "match_result", indexes = {
        @Index(name = "idx_match_run_status", columnList = "run_id, status")
})
public class MatchResult {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchMethod method;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ExceptionCategory exceptionCategory = ExceptionCategory.NONE;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "gateway_txn_id")
    private NormalizedTransaction gatewayTxn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "bank_txn_id")
    private NormalizedTransaction bankTxn;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ledger_txn_id")
    private NormalizedTransaction ledgerTxn;

    @Column(nullable = false)
    private Double confidence;

    @Lob
    private String reasoning;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public MatchResult() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ReconciliationRun getRun() { return run; }
    public void setRun(ReconciliationRun run) { this.run = run; }

    public MatchStatus getStatus() { return status; }
    public void setStatus(MatchStatus status) { this.status = status; }

    public MatchMethod getMethod() { return method; }
    public void setMethod(MatchMethod method) { this.method = method; }

    public ExceptionCategory getExceptionCategory() { return exceptionCategory; }
    public void setExceptionCategory(ExceptionCategory exceptionCategory) {
        this.exceptionCategory = exceptionCategory;
    }

    public NormalizedTransaction getGatewayTxn() { return gatewayTxn; }
    public void setGatewayTxn(NormalizedTransaction gatewayTxn) { this.gatewayTxn = gatewayTxn; }

    public NormalizedTransaction getBankTxn() { return bankTxn; }
    public void setBankTxn(NormalizedTransaction bankTxn) { this.bankTxn = bankTxn; }

    public NormalizedTransaction getLedgerTxn() { return ledgerTxn; }
    public void setLedgerTxn(NormalizedTransaction ledgerTxn) { this.ledgerTxn = ledgerTxn; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
