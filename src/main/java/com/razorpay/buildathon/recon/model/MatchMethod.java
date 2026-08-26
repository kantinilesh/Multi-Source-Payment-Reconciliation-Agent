package com.razorpay.buildathon.recon.model;

/**
 * Which layer of the matching engine (Section 3.5 of the blueprint) produced this
 * result. Kept as an explicit field everywhere so the UI/audit trail can always show
 * *how* a decision was reached - never a black box.
 */
public enum MatchMethod {
    RULE_EXACT,       // Layer 1 - exact key match
    RULE_HEURISTIC,   // Layer 2 - amount tolerance + timestamp window + ID similarity
    AI_ASSISTED,       // Layer 3 - LLM-resolved ambiguous cluster, confidence >= threshold
    UNRESOLVED        // no layer produced a confident match -> exception queue
}
