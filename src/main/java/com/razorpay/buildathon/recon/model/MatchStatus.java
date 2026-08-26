package com.razorpay.buildathon.recon.model;

/**
 * Per-transaction outcome of the matching pipeline. See blueprint Section 1.5.
 *
 * Phase 3 uses RECONCILED, REVIEW_REQUIRED, and EXCEPTION.
 * MATCHED / PARTIALLY_MATCHED are kept for backward-compatibility.
 */
public enum MatchStatus {
    /** High-confidence deterministic match across all available sources. */
    RECONCILED,
    /**
     * Evidence is suggestive but insufficient for certainty. Will be routed to
     * the AI agent in Phase 4 for further resolution.
     */
    REVIEW_REQUIRED,
    /**
     * Strong deterministic evidence of a genuine problem: missing record,
     * amount mismatch, duplicate, or other financial inconsistency.
     */
    EXCEPTION,

    // Legacy values from Phase 2 schema foundation — kept for compatibility.
    MATCHED,
    PARTIALLY_MATCHED
}
