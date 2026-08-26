package com.razorpay.buildathon.recon.repository;

import com.razorpay.buildathon.recon.model.NormalizedTransaction;
import com.razorpay.buildathon.recon.model.SourceType;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface NormalizedTransactionRepository extends JpaRepository<NormalizedTransaction, Long> {

    List<NormalizedTransaction> findByRunIdAndSourceType(Long runId, SourceType sourceType);

    List<NormalizedTransaction> findByRunId(Long runId);
}
