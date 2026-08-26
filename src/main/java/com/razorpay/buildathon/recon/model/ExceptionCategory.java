package com.razorpay.buildathon.recon.model;

/**
 * Small, scannable taxonomy for exceptions - Section 3.9 of the blueprint.
 * Every EXCEPTION-status match must carry one of these so the report is
 * readable at a glance, not just a dump of "unresolved".
 */
public enum ExceptionCategory {
    MISSING_IN_BANK_FILE,
    MISSING_IN_LEDGER,
    MISSING_IN_GATEWAY,
    AMOUNT_MISMATCH_BEYOND_TOLERANCE,
    /** Two or more records from the same source appear to be the same transaction. */
    DUPLICATE_CANDIDATE,
    AMBIGUOUS_MULTI_MATCH,
    /**
     * A refund record is present but the corresponding original payment record
     * is missing or shows an incompatible status.
     */
    REFUND_MISMATCH,
    /**
     * Multiple candidates exist from at least one source with strong but
     * conflicting evidence — cannot deterministically pick one.
     */
    DUPLICATE_DETECTED,
    NONE // used when status != EXCEPTION
}
