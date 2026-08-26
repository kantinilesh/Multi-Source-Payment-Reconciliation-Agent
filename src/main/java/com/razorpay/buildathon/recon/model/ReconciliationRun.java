package com.razorpay.buildathon.recon.model;

import jakarta.persistence.*;
import java.time.Instant;

/**
 * One end-to-end reconciliation run: 3 files in, a scored/matched result out.
 */
@Entity
@Table(name = "reconciliation_run")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RunStatus status = RunStatus.UPLOADED;

    @Column(nullable = false)
    private Instant createdAt = Instant.now();

    private Instant completedAt;

    private String gatewayFileName;
    private String bankFileName;
    private String ledgerFileName;

    private Integer totalGroundTruthCount;
    private Integer matchedCount;
    private Integer partiallyMatchedCount;
    private Integer exceptionCount;
    private Integer aiAssistedCount;
    private Double matchRatePct;
    private Double automationRatePct;
    private Long processingTimeMs;

    public ReconciliationRun() {}

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public RunStatus getStatus() { return status; }
    public void setStatus(RunStatus status) { this.status = status; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getCompletedAt() { return completedAt; }
    public void setCompletedAt(Instant completedAt) { this.completedAt = completedAt; }

    public String getGatewayFileName() { return gatewayFileName; }
    public void setGatewayFileName(String gatewayFileName) { this.gatewayFileName = gatewayFileName; }

    public String getBankFileName() { return bankFileName; }
    public void setBankFileName(String bankFileName) { this.bankFileName = bankFileName; }

    public String getLedgerFileName() { return ledgerFileName; }
    public void setLedgerFileName(String ledgerFileName) { this.ledgerFileName = ledgerFileName; }

    public Integer getTotalGroundTruthCount() { return totalGroundTruthCount; }
    public void setTotalGroundTruthCount(Integer v) { this.totalGroundTruthCount = v; }

    public Integer getMatchedCount() { return matchedCount; }
    public void setMatchedCount(Integer matchedCount) { this.matchedCount = matchedCount; }

    public Integer getPartiallyMatchedCount() { return partiallyMatchedCount; }
    public void setPartiallyMatchedCount(Integer v) { this.partiallyMatchedCount = v; }

    public Integer getExceptionCount() { return exceptionCount; }
    public void setExceptionCount(Integer exceptionCount) { this.exceptionCount = exceptionCount; }

    public Integer getAiAssistedCount() { return aiAssistedCount; }
    public void setAiAssistedCount(Integer aiAssistedCount) { this.aiAssistedCount = aiAssistedCount; }

    public Double getMatchRatePct() { return matchRatePct; }
    public void setMatchRatePct(Double matchRatePct) { this.matchRatePct = matchRatePct; }

    public Double getAutomationRatePct() { return automationRatePct; }
    public void setAutomationRatePct(Double v) { this.automationRatePct = v; }

    public Long getProcessingTimeMs() { return processingTimeMs; }
    public void setProcessingTimeMs(Long processingTimeMs) { this.processingTimeMs = processingTimeMs; }
}
