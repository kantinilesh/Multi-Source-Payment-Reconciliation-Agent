package com.razorpay.buildathon.recon.engine;

import com.razorpay.buildathon.recon.config.ReconMatchingConfig;
import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.Set;

/**
 * Computes a deterministic {@link MatchScore} for one {@link CandidateSet} by
 * evaluating each {@link ScoreSignal} independently and summing the points.
 *
 * The scorer is intentionally separated from the rules (which apply hard
 * pass/fail logic) so that:
 *   - Score and rules can be tested in isolation.
 *   - Future Phase 4 AI layer can receive the numeric score as context.
 *   - The audit trail always contains both the score and which signals fired.
 */
@Component
public class MatchScorer {

    /**
     * Status values that indicate a successful, settled payment on the
     * gateway or ledger side. Used for STATUS_COMPAT signal.
     */
    private static final Set<String> SUCCESS_STATUSES = Set.of(
            "SUCCESS", "PAID", "CREDITED", "SETTLED", "COMPLETE", "COMPLETED");

    /** Status values that indicate a refund. */
    public static final Set<String> REFUND_STATUSES = Set.of(
            "REFUNDED", "REFUND", "REVERSED", "REVERSAL");

    private final ReconMatchingConfig config;

    public MatchScorer(ReconMatchingConfig config) {
        this.config = config;
    }

    /**
     * Score the primary match within a CandidateSet:
     * gateway + primaryBank + primaryLedger.
     *
     * @param cs the candidate set to score
     * @return a MatchScore with per-signal breakdown
     */
    public MatchScore score(CandidateSet cs) {
        NormalizedTransaction gw   = cs.gatewayTxn();
        NormalizedTransaction bank = cs.primaryBankTxn().orElse(null);
        NormalizedTransaction ledg = cs.primaryLedgerTxn().orElse(null);

        MatchScore.Builder builder = MatchScore.builder();
        ReconMatchingConfig.Score w = config.getScore();

        // ---- 1. EXACT_ID ------------------------------------------------
        scoreExactId(builder, gw, bank, ledg, w.getExactIdWeight());

        // ---- 2. REF_SIMILARITY ------------------------------------------
        scoreRefSimilarity(builder, gw, bank, ledg, w.getRefSimilarityWeight());

        // ---- 3. AMOUNT_EXACT --------------------------------------------
        scoreAmountExact(builder, gw, bank, ledg, w.getAmountExactWeight());

        // ---- 4. FEE_ADJUSTED --------------------------------------------
        scoreFeeAdjusted(builder, gw, bank, w.getFeeAdjustedWeight());

        // ---- 5. TIMESTAMP_WINDOW ----------------------------------------
        scoreTimestampWindow(builder, gw, bank, ledg, w.getTimestampWeight());

        // ---- 6. STATUS_COMPAT -------------------------------------------
        scoreStatusCompat(builder, gw, ledg, w.getStatusCompatWeight());

        return builder.build();
    }

    // -------------------------------------------------------------------------
    // Per-signal scoring methods (package-private for unit testing)
    // -------------------------------------------------------------------------

    void scoreExactId(MatchScore.Builder b, NormalizedTransaction gw,
                      NormalizedTransaction bank, NormalizedTransaction ledg, int weight) {
        if (gw == null) {
            b.addSignal(ScoreSignal.EXACT_ID, 0, "no gateway record");
            return;
        }
        String gwCore   = RawFieldExtractor.extractNumericCore(gw.getExternalRef());
        String bnkCore  = bank  != null ? RawFieldExtractor.extractNumericCore(bank.getExternalRef())  : null;
        String ledgCore = ledg  != null ? RawFieldExtractor.extractNumericCore(ledg.getExternalRef()) : null;

        boolean bnkMatch  = bnkCore  != null && !bnkCore.isBlank()  && gwCore.equals(bnkCore);
        boolean ledgMatch = ledgCore != null && !ledgCore.isBlank() && gwCore.equals(ledgCore);

        if (bnkMatch && ledgMatch) {
            b.addSignal(ScoreSignal.EXACT_ID, weight, "all 3 cores match: " + gwCore);
        } else if (bnkMatch || ledgMatch) {
            // Partial exact — give half weight; REF_SIMILARITY may also fire
            b.addSignal(ScoreSignal.EXACT_ID, weight / 2,
                    "2/3 cores match: gw=" + gwCore
                    + " bank=" + (bnkCore != null ? bnkCore : "N/A")
                    + " ledger=" + (ledgCore != null ? ledgCore : "N/A"));
        } else {
            b.addSignal(ScoreSignal.EXACT_ID, 0,
                    "no core match: gw=" + gwCore
                    + " bank=" + (bnkCore != null ? bnkCore : "N/A")
                    + " ledger=" + (ledgCore != null ? ledgCore : "N/A"));
        }
    }

    void scoreRefSimilarity(MatchScore.Builder b, NormalizedTransaction gw,
                            NormalizedTransaction bank, NormalizedTransaction ledg, int weight) {
        if (gw == null) {
            b.addSignal(ScoreSignal.REF_SIMILARITY, 0, "no gateway record");
            return;
        }
        String gwCore  = RawFieldExtractor.extractNumericCore(gw.getExternalRef());
        boolean anyMatch = false;

        if (bank != null) {
            String bnkCore = RawFieldExtractor.extractNumericCore(bank.getExternalRef());
            if (!bnkCore.isBlank() && gwCore.equals(bnkCore)) anyMatch = true;
        }
        if (ledg != null) {
            String ledgCore = RawFieldExtractor.extractNumericCore(ledg.getExternalRef());
            if (!ledgCore.isBlank() && gwCore.equals(ledgCore)) anyMatch = true;
        }

        // Only award REF_SIMILARITY points when EXACT_ID did NOT already award full weight,
        // to avoid double-counting for a perfect match.
        // Award partial similarity points for partial matches.
        if (anyMatch) {
            b.addSignal(ScoreSignal.REF_SIMILARITY, weight,
                    "numeric core appears in >=2 sources: " + gwCore);
        } else {
            b.addSignal(ScoreSignal.REF_SIMILARITY, 0, "cores differ across sources");
        }
    }

    void scoreAmountExact(MatchScore.Builder b, NormalizedTransaction gw,
                          NormalizedTransaction bank, NormalizedTransaction ledg, int weight) {
        if (gw == null) {
            b.addSignal(ScoreSignal.AMOUNT_EXACT, 0, "no gateway record");
            return;
        }
        BigDecimal gwAmt = gw.getAmount();
        boolean ledgExact = ledg != null && gwAmt.compareTo(ledg.getAmount()) == 0;
        // Bank amount is settled_amount (net of fee) so we don't expect exact equality
        // to gateway gross — that's handled by FEE_ADJUSTED signal.
        // We DO award AMOUNT_EXACT if bank gross matches gateway gross (no-fee scenario).
        boolean bankExact = bank != null && gwAmt.compareTo(bank.getAmount()) == 0;

        if (ledgExact && bankExact) {
            b.addSignal(ScoreSignal.AMOUNT_EXACT, weight, "all amounts exactly equal: " + gwAmt);
        } else if (ledgExact || bankExact) {
            b.addSignal(ScoreSignal.AMOUNT_EXACT, weight / 2,
                    "amounts match in 2/3 sources (gw=" + gwAmt
                    + " bank=" + (bank != null ? bank.getAmount() : "N/A")
                    + " ledger=" + (ledg != null ? ledg.getAmount() : "N/A") + ")");
        } else {
            b.addSignal(ScoreSignal.AMOUNT_EXACT, 0,
                    "amounts differ gw=" + gwAmt
                    + " bank=" + (bank != null ? bank.getAmount() : "N/A")
                    + " ledger=" + (ledg != null ? ledg.getAmount() : "N/A"));
        }
    }

    void scoreFeeAdjusted(MatchScore.Builder b, NormalizedTransaction gw,
                          NormalizedTransaction bank, int weight) {
        if (gw == null || bank == null) {
            b.addSignal(ScoreSignal.FEE_ADJUSTED, 0,
                    gw == null ? "no gateway record" : "no bank record");
            return;
        }
        BigDecimal gwAmt = gw.getAmount();
        BigDecimal fee   = RawFieldExtractor.extractGatewayFee(gw);
        BigDecimal bankAmt = bank.getAmount();

        if (fee.compareTo(BigDecimal.ZERO) == 0) {
            b.addSignal(ScoreSignal.FEE_ADJUSTED, 0, "gateway fee=0; not applicable");
            return;
        }

        BigDecimal feePct    = fee.divide(gwAmt, 6, java.math.RoundingMode.HALF_UP)
                                  .multiply(BigDecimal.valueOf(100));
        BigDecimal feeMin    = BigDecimal.valueOf(config.getFeeTolerancePctMin());
        BigDecimal feeMax    = BigDecimal.valueOf(config.getFeeTolerancePctMax());
        BigDecimal expected  = gwAmt.subtract(fee);
        boolean withinBand   = feePct.compareTo(feeMin) >= 0 && feePct.compareTo(feeMax) <= 0;
        boolean bankMatchesNet = bankAmt.compareTo(expected) == 0;

        if (withinBand && bankMatchesNet) {
            b.addSignal(ScoreSignal.FEE_ADJUSTED, weight,
                    "bank=" + bankAmt + " == gw(" + gwAmt + ") - fee(" + fee + ")");
        } else {
            b.addSignal(ScoreSignal.FEE_ADJUSTED, 0,
                    "fee-adjusted mismatch: expected net=" + expected
                    + " bank=" + bankAmt + " feePct=" + feePct + "%");
        }
    }

    void scoreTimestampWindow(MatchScore.Builder b, NormalizedTransaction gw,
                              NormalizedTransaction bank, NormalizedTransaction ledg, int weight) {
        if (gw == null) {
            b.addSignal(ScoreSignal.TIMESTAMP_WINDOW, 0, "no gateway record");
            return;
        }
        long lagSeconds = (long) config.getSettlementLagDaysMax() * 86_400L;
        boolean bankOk  = bank == null || withinWindow(gw, bank, lagSeconds);
        boolean ledgOk  = ledg == null || withinWindow(gw, ledg, lagSeconds);

        if (bankOk && ledgOk) {
            b.addSignal(ScoreSignal.TIMESTAMP_WINDOW, weight,
                    "all timestamps within " + config.getSettlementLagDaysMax() + " day(s)");
        } else {
            b.addSignal(ScoreSignal.TIMESTAMP_WINDOW, 0,
                    "timestamp outside " + config.getSettlementLagDaysMax() + " day window: "
                    + "gw=" + gw.getTimestamp()
                    + " bank=" + (bank != null ? bank.getTimestamp() : "N/A")
                    + " ledger=" + (ledg != null ? ledg.getTimestamp() : "N/A"));
        }
    }

    void scoreStatusCompat(MatchScore.Builder b, NormalizedTransaction gw,
                           NormalizedTransaction ledg, int weight) {
        if (gw == null) {
            b.addSignal(ScoreSignal.STATUS_COMPAT, 0, "no gateway record");
            return;
        }
        String gwStatus   = RawFieldExtractor.extractStatus(gw);
        String ledgStatus = ledg != null ? RawFieldExtractor.extractStatus(ledg) : "";

        boolean gwSuccess   = SUCCESS_STATUSES.contains(gwStatus);
        boolean ledgSuccess = ledgStatus.isBlank() || SUCCESS_STATUSES.contains(ledgStatus);
        // Refund + SUCCESS is a mismatch that warrants attention — score 0 here,
        // the RefundRule will classify it appropriately.
        boolean gwRefund    = REFUND_STATUSES.contains(gwStatus);
        boolean ledgRefund  = !ledgStatus.isBlank() && REFUND_STATUSES.contains(ledgStatus);

        if (gwRefund && ledgRefund) {
            b.addSignal(ScoreSignal.STATUS_COMPAT, weight,
                    "both gateway and ledger show refund status");
        } else if (gwSuccess && ledgSuccess) {
            b.addSignal(ScoreSignal.STATUS_COMPAT, weight,
                    "statuses compatible: gw=" + gwStatus + " ledger=" + (ledgStatus.isBlank() ? "N/A" : ledgStatus));
        } else {
            b.addSignal(ScoreSignal.STATUS_COMPAT, 0,
                    "status mismatch: gw=" + gwStatus + " ledger=" + (ledgStatus.isBlank() ? "N/A" : ledgStatus));
        }
    }

    // -------------------------------------------------------------------------

    private boolean withinWindow(NormalizedTransaction anchor, NormalizedTransaction other,
                                 long lagSeconds) {
        long delta = Math.abs(Duration.between(anchor.getTimestamp(), other.getTimestamp()).getSeconds());
        return delta <= lagSeconds;
    }
}
