package com.razorpay.buildathon.recon.repository;

import com.razorpay.buildathon.recon.model.ReconciliationRun;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReconciliationRunRepository extends JpaRepository<ReconciliationRun, Long> {
}
