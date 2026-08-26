package com.razorpay.buildathon.recon.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * Immutable log of every matching decision the engine makes.
 * Written to, never updated or deleted from.
 */
@Entity
@Table(name = "audit_log_entry", indexes = {
        @Index(name = "idx_audit_run", columnList = "run_id")
})
public class AuditLogEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    private ReconciliationRun run;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "match_result_id", nullable = false)
    private MatchResult matchResult;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private MatchMethod method;

    @Column(nullable = false)
    private Double confidence;

    @Lob
    @Column(nullable = false)
    private String inputsConsidered;

    @Lob
    @Column(nullable = false)
    private String reasoning;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    public AuditLogEntry() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public ReconciliationRun getRun() { return run; }
    public void setRun(ReconciliationRun run) { this.run = run; }

    public MatchResult getMatchResult() { return matchResult; }
    public void setMatchResult(MatchResult matchResult) { this.matchResult = matchResult; }

    public MatchMethod getMethod() { return method; }
    public void setMethod(MatchMethod method) { this.method = method; }

    public Double getConfidence() { return confidence; }
    public void setConfidence(Double confidence) { this.confidence = confidence; }

    public String getInputsConsidered() { return inputsConsidered; }
    public void setInputsConsidered(String inputsConsidered) { this.inputsConsidered = inputsConsidered; }

    public String getReasoning() { return reasoning; }
    public void setReasoning(String reasoning) { this.reasoning = reasoning; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
