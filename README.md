# recon-backend — Multi-Source Payment Reconciliation Agent

**Razorpay AI Buildathon, Track 04**

An enterprise-grade, hybrid payment reconciliation system combining a high-precision **Deterministic Rules Engine (Phase 3)** with a **Guarded AI Exception Reasoning Agent (Phase 4)** and an objective **Ground Truth Evaluation Layer (Phase 5)** across Payment Gateway, Bank Settlement, and Internal ERP Ledger records.

---

## Architecture Overview

```
                                  [ Upload Files (Phase 2) ]
                                              │
                                              ▼
                             [ Normalization Pipeline ]
                                              │
                                              ▼
                             [ Phase 3: Deterministic Engine ]
                                              │
                      ┌───────────────────────┴───────────────────────┐
                      ▼                                               ▼
            [ RECONCILED ] (Score >= 70)                     [ REVIEW_REQUIRED / EXCEPTION ]
            Rule Exact / Fee Adjusted / Ref Variant          Ambiguous / Missing / Discrepancy
            (No LLM Invocation)                                       │
                                                                      ▼
                                                       [ Phase 4: AI Exception Reasoning Agent ]
                                                                      │
                                                       ├─ Controlled Toolset (ReconciliationAgentTools)
                                                       ├─ Prompt Injection Sanitizer
                                                       ├─ Provider Abstraction (Anthropic / OpenAI / Mock)
                                                       └─ Security & Hallucination Guardrail
                                                                      │
                                              ┌───────────────────────┴───────────────────────┐
                                              ▼                                               ▼
                            [ Validated AI Decision ]                               [ Failed / Low Confidence ]
                      Confidence >= 0.85 & Valid Evidence                             Safe Fallback to REVIEW_REQUIRED
                              │                                                               │
                              ▼                                                               ▼
                      [ Status Updated ]                                               [ Remains REVIEW_REQUIRED ]
                  (RECONCILED / EXCEPTION)                                                (Method: AI_ASSISTED)
                    Method: AI_ASSISTED
                                              │
                                              ▼
                             [ Phase 5: Evaluation Benchmark Engine ]
                        (Compares Predicted Output vs Ground Truth Dataset)
```

---

## Core Features & System Phases

### Phase 2: Data Ingestion & Normalization
- Accepts **Gateway Export**, **Bank Settlement**, and **Internal Ledger** files in CSV format.
- Parses and normalizes all transactions into a unified schema while preserving original raw JSON rows for full auditability.

### Phase 3: Deterministic Reconciliation Engine
- **Two-Pass Candidate Generator**: Groups related records by numeric reference cores, settlement timeframe, and amount compatibility.
- **6-Signal Scorer**: Evaluates Exact ID match, Reference Similarity, Exact Amount, Gateway Fee Adjustment, Timestamp Window, and Status Compatibility.
- **Ordered Rule Strategy**:
  1. `MissingRecordRule`: Flags missing bank/ledger counterpart.
  2. `AmountMismatchRule`: Detects unexplained amount discrepancies beyond fee tolerance.
  3. `DuplicateDetectionRule`: Catches duplicate candidate records.
  4. `ExactMatchRule`: Auto-reconciles 3-way exact matches.
  5. `FeeAdjustedRule`: Auto-reconciles gateway gross vs net bank settlement.
  6. `ReferenceVariantRule`: Handles prefix variants (e.g. `GW-83921`, `SET-83921`, `PAY-83921`).
  7. `TimestampWindowRule`: Handles settlement lag window differences.
  8. `RefundRule`: Reconciles symmetric refunds and flags asymmetric refund mismatches.

### Phase 4: AI Exception Reasoning Agent
- **Controlled Toolset (`ReconciliationAgentTools`)**: Exposes focused, read-only query capabilities (`getTransaction`, `getRelatedTransactions`, `compareCandidates`, `calculateExpectedSettlement`, `getFeeInformation`, `getRunContext`).
- **Structured AI Output (`AiReasoningResponse`)**: Forces strict schema responses (`decision`, `confidence`, `exceptionCategory`, `probableReason`, `evidence`, `recommendedAction`).
- **Security & Hallucination Guardrails (`AiResponseGuardrail`)**:
  - **Prompt Injection Defense**: Sanitizes untrusted transaction text and wraps input in XML `<untrusted_data>` boundaries.
  - **Hallucination Prevention**: Verifies every transaction ID and reference cited in AI output against actual DB context.
  - **Confidence Gate**: Enforces a minimum confidence threshold (`0.85`) to auto-accept AI recommendations. Low-confidence outputs safely default to `REVIEW_REQUIRED`.

### Phase 5: Evaluation, Ground Truth & Finance Controller Metrics
- **Controlled 60-Transaction Ground Truth Benchmark**: Contains controlled test cases covering exact matches, fee adjustments, reference variants, settlement lag, missing bank, missing ledger, amount gaps, duplicates, and refunds.
- **Precision Metrics Engine (`EvaluationMetricsEngine`)**: Computes True Positives, False Positives, False Negatives, True Negatives, Match Rate %, Exception Rate %, Human Review Rate %, Automation Rate %, Exception Category Accuracy %, and Processing Throughput (txns/sec).
- **Comparative Baseline Engine**: Computes exact performance deltas comparing Phase 3 Baseline (Rules Only) against Phase 4 Hybrid (Rules + AI).
- **Machine & Human Output**: Generates machine-readable JSON evaluation payloads and human-readable Markdown benchmark summary reports.

---

## API Endpoints

| Method | Endpoint | Description |
|---|---|---|
| `POST` | `/api/runs` | Upload gateway, bank, and ledger CSV files |
| `GET` | `/api/runs/{id}` | Retrieve run status and file metadata |
| `GET` | `/api/runs/{id}/transactions` | Retrieve all normalized transactions for a run |
| `POST` | `/api/runs/{id}/reconcile` | Trigger full Phase 3 + Phase 4 reconciliation pipeline |
| `GET` | `/api/runs/{id}/matches` | Retrieve all match results |
| `GET` | `/api/runs/{id}/exceptions` | Retrieve EXCEPTION match results |
| `GET` | `/api/runs/{id}/summary` | Retrieve aggregate run metrics |
| `GET` | `/api/runs/{id}/audit` | Retrieve full immutable audit trail |
| `POST` | `/api/runs/{id}/matches/{matchId}/ai-explain` | Trigger on-demand AI reasoning for a single match |
| `GET` | `/api/runs/{id}/evaluation` | Get comparative evaluation metrics (Baseline vs AI-Enhanced) |
| `POST` | `/api/evaluation/run-benchmark` | Run full 60-case Ground Truth Benchmark pipeline |
| `GET` | `/api/evaluation/benchmark-report` | Fetch Markdown report for latest benchmark run |

---

## Running the Application

### Prerequisites
- Java 17+
- Maven 3.9+
- PostgreSQL (or in-memory H2 for testing)

### Quickstart

```bash
# Build and run tests
mvn clean test

# Run application locally
mvn spring-boot:run
```

App runs on `http://localhost:8080`.

### Running Ground Truth Benchmark via API

```bash
# 1. Trigger full 60-case Ground Truth Benchmark evaluation
curl -X POST http://localhost:8080/api/evaluation/run-benchmark

# 2. Fetch formatted Markdown evaluation report
curl http://localhost:8080/api/evaluation/benchmark-report
```
