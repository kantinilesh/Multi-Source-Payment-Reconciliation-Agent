package com.razorpay.buildathon.recon.engine;

import com.razorpay.buildathon.recon.model.NormalizedTransaction;

import java.util.List;
import java.util.Optional;

/**
 * A group of candidate records — one from each source — that the engine
 * believes may represent the same underlying financial transaction.
 *
 * Any slot may be absent:
 *  - {@code gatewayTxn} absent → the payment never reached the gateway or
 *    was filtered out of the export.
 *  - {@code bankTxn} absent → the settlement has not arrived yet, or the
 *    bank record is missing.
 *  - {@code ledgerTxn} absent → the internal ledger was not updated.
 *
 * A CandidateSet with multiple bank or ledger candidates signals a potential
 * duplicate that the DuplicateDetectionRule will flag.
 */
public record CandidateSet(
        NormalizedTransaction gatewayTxn,
        List<NormalizedTransaction> bankCandidates,
        List<NormalizedTransaction> ledgerCandidates
) {
    /** The best (highest-scoring) bank match, if any were found. */
    public Optional<NormalizedTransaction> primaryBankTxn() {
        return bankCandidates == null || bankCandidates.isEmpty()
                ? Optional.empty()
                : Optional.of(bankCandidates.get(0));
    }

    /** The best (highest-scoring) ledger match, if any were found. */
    public Optional<NormalizedTransaction> primaryLedgerTxn() {
        return ledgerCandidates == null || ledgerCandidates.isEmpty()
                ? Optional.empty()
                : Optional.of(ledgerCandidates.get(0));
    }

    public boolean hasBankDuplicates() {
        return bankCandidates != null && bankCandidates.size() > 1;
    }

    public boolean hasLedgerDuplicates() {
        return ledgerCandidates != null && ledgerCandidates.size() > 1;
    }

    public boolean isMissingBank() {
        return bankCandidates == null || bankCandidates.isEmpty();
    }

    public boolean isMissingLedger() {
        return ledgerCandidates == null || ledgerCandidates.isEmpty();
    }
}
