package com.razorpay.buildathon.recon.engine;

/**
 * The individual evidence signals that the deterministic scoring model
 * considers independently. Each signal maps to a configurable weight in
 * {@link com.razorpay.buildathon.recon.config.ReconMatchingConfig}.
 *
 * Having an explicit enum (rather than free-text strings) means:
 *   - rules can reference signals by name, not magic strings
 *   - the audit trail records exactly which signals fired
 *   - tests can assert on specific signals without string-matching
 */
public enum ScoreSignal {

    /**
     * All three sources share the same numeric reference core after stripping
     * source-specific prefixes (GW-, SET-, PAY-, ORD-, etc.).
     * Example: GW-83921 == SET-83921 == PAY-83921 → core = "83921"
     */
    EXACT_ID,

    /**
     * At least two of the three sources share the same numeric core, or the
     * references are similar enough to be considered variants of the same ID.
     * Fires when EXACT_ID does not.
     */
    REF_SIMILARITY,

    /**
     * All amounts across all present sources are exactly equal (ignoring fee
     * accounting).
     */
    AMOUNT_EXACT,

    /**
     * Bank settlement amount matches gateway amount minus the recorded gateway
     * fee, within the configured tolerance band.
     */
    FEE_ADJUSTED,

    /**
     * All timestamps fall within the configured settlement-lag window
     * (gateway timestamp → bank settlement date within settlementLagDaysMax).
     */
    TIMESTAMP_WINDOW,

    /**
     * Payment status strings across sources are compatible. For example:
     * Gateway=SUCCESS + Ledger=PAID + Bank=CREDITED is compatible.
     * Gateway=SUCCESS + Ledger=REFUNDED is not.
     */
    STATUS_COMPAT
}
