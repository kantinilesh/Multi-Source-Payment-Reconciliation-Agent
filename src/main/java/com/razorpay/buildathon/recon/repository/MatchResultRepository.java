package com.razorpay.buildathon.recon.repository;

import com.razorpay.buildathon.recon.model.MatchResult;
import com.razorpay.buildathon.recon.model.MatchStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface MatchResultRepository extends JpaRepository<MatchResult, Long> {

    List<MatchResult> findByRunId(Long runId);

    List<MatchResult> findByRunIdAndStatus(Long runId, MatchStatus status);

    long countByRunIdAndStatus(Long runId, MatchStatus status);

    /**
     * Eagerly fetches all three source transaction associations in a single query.
     * Use this when working outside a JPA session (e.g. in tests or non-transactional
     * service methods) to avoid LazyInitializationExceptions.
     */
    @Query("""
            SELECT mr FROM MatchResult mr
            LEFT JOIN FETCH mr.gatewayTxn
            LEFT JOIN FETCH mr.bankTxn
            LEFT JOIN FETCH mr.ledgerTxn
            WHERE mr.run.id = :runId
            """)
    List<MatchResult> findByRunIdWithTxns(@Param("runId") Long runId);

    /**
     * Same as above but filtered by status.
     */
    @Query("""
            SELECT mr FROM MatchResult mr
            LEFT JOIN FETCH mr.gatewayTxn
            LEFT JOIN FETCH mr.bankTxn
            LEFT JOIN FETCH mr.ledgerTxn
            WHERE mr.run.id = :runId AND mr.status = :status
            """)
    List<MatchResult> findByRunIdAndStatusWithTxns(@Param("runId") Long runId,
                                                    @Param("status") MatchStatus status);
}
