package com.razorpay.buildathon.recon.engine;

import com.razorpay.buildathon.recon.config.ReconMatchingConfig;
import com.razorpay.buildathon.recon.engine.rules.ReconciliationRule;
import com.razorpay.buildathon.recon.model.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * The Phase 3 deterministic reconciliation engine.
 *
 * Orchestration:
 *   1. CandidateGenerator groups normalized transactions into candidate sets.
 *   2. For each candidate set, MatchScorer computes a signal-level score.
 *   3. Rules are evaluated in priority order (via Spring @Order); the first
 *      rule that produces a conclusive outcome wins.
 *   4. If no rule fires, a fallback decision is made based on the raw score.
 *
 * This engine does NOT call any LLM or external service — every decision is
 * deterministic and reproducible given the same input data.
 */
@Component
public class DeterministicReconciliationEngine {

    private static final Logger log = LoggerFactory.getLogger(DeterministicReconciliationEngine.class);

    private final CandidateGenerator candidateGenerator;
    private final MatchScorer matchScorer;
    private final List<ReconciliationRule> rules; // injected ordered by @Order
    private final ReconMatchingConfig config;

    public DeterministicReconciliationEngine(
            CandidateGenerator candidateGenerator,
            MatchScorer matchScorer,
            List<ReconciliationRule> rules,
            ReconMatchingConfig config) {
        this.candidateGenerator = candidateGenerator;
        this.matchScorer = matchScorer;
        this.rules = rules;
        this.config = config;
    }

    /**
     * Run the full deterministic reconciliation for one run's transactions.
     *
     * @param all all {@link NormalizedTransaction} objects for the run
     * @return a list of {@link EngineResult} — one per candidate set
     */
    public List<EngineResult> reconcile(List<NormalizedTransaction> all) {
        log.info("DeterministicReconciliationEngine starting with {} transactions", all.size());

        List<CandidateSet> candidates = candidateGenerator.generate(all);
        List<EngineResult> results = new ArrayList<>(candidates.size());

        int reconciled = 0, reviewRequired = 0, exceptions = 0;
        for (CandidateSet cs : candidates) {
            EngineResult result = processCandidate(cs);
            results.add(result);
            switch (result.outcome().status()) {
                case RECONCILED     -> reconciled++;
                case REVIEW_REQUIRED -> reviewRequired++;
                default             -> exceptions++;
            }
        }

        log.info("DeterministicReconciliationEngine complete: {} candidates → " +
                "{} reconciled, {} review-required, {} exceptions",
                candidates.size(), reconciled, reviewRequired, exceptions);

        return results;
    }

    // -------------------------------------------------------------------------
    // Private
    // -------------------------------------------------------------------------

    private EngineResult processCandidate(CandidateSet cs) {
        MatchScore score = matchScorer.score(cs);

        // Try each rule in priority order
        for (ReconciliationRule rule : rules) {
            var maybeOutcome = rule.evaluate(cs, score);
            if (maybeOutcome.isPresent()) {
                ReconciliationRule.RuleOutcome outcome = maybeOutcome.get();
                log.debug("Rule {} matched for gwRef={}: status={}",
                        rule.getClass().getSimpleName(),
                        cs.gatewayTxn() != null ? cs.gatewayTxn().getExternalRef() : "N/A",
                        outcome.status());
                return new EngineResult(cs, score, outcome);
            }
        }

        // Fallback: no rule produced a conclusive result — use score thresholds
        ReconciliationRule.RuleOutcome fallback = fallbackDecision(cs, score);
        log.debug("No rule matched for gwRef={}; fallback: status={}",
                cs.gatewayTxn() != null ? cs.gatewayTxn().getExternalRef() : "N/A",
                fallback.status());
        return new EngineResult(cs, score, fallback);
    }

    private ReconciliationRule.RuleOutcome fallbackDecision(CandidateSet cs, MatchScore score) {
        int total = score.getTotalScore();
        if (total >= config.getReconciledThreshold()) {
            return new ReconciliationRule.RuleOutcome(
                    MatchStatus.RECONCILED,
                    MatchMethod.RULE_HEURISTIC,
                    ExceptionCategory.NONE,
                    "Fallback: score=" + total + " >= reconciled-threshold=" +
                    config.getReconciledThreshold() + ". Signals: " + score.getExplanation()
            );
        } else if (total >= config.getReviewThreshold()) {
            return new ReconciliationRule.RuleOutcome(
                    MatchStatus.REVIEW_REQUIRED,
                    MatchMethod.RULE_HEURISTIC,
                    ExceptionCategory.NONE,
                    "Fallback: score=" + total + " between review-threshold=" +
                    config.getReviewThreshold() + " and reconciled-threshold=" +
                    config.getReconciledThreshold() + ". Signals: " + score.getExplanation()
            );
        } else {
            return new ReconciliationRule.RuleOutcome(
                    MatchStatus.EXCEPTION,
                    MatchMethod.UNRESOLVED,
                    ExceptionCategory.AMBIGUOUS_MULTI_MATCH,
                    "Fallback: score=" + total + " below review-threshold=" +
                    config.getReviewThreshold() + ". No rule matched. Signals: " +
                    score.getExplanation()
            );
        }
    }

    // -------------------------------------------------------------------------

    /**
     * A fully-resolved result from the engine: the original candidate set,
     * the computed score, and the final rule outcome. This is what the
     * persistence layer converts into {@link MatchResult} and {@link AuditLogEntry}.
     */
    public record EngineResult(
            CandidateSet candidateSet,
            MatchScore score,
            ReconciliationRule.RuleOutcome outcome
    ) {}
}
