package com.razorpay.buildathon.recon.repository;

import com.razorpay.buildathon.recon.model.AuditLogEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface AuditLogEntryRepository extends JpaRepository<AuditLogEntry, Long> {

    List<AuditLogEntry> findByRunIdOrderByCreatedAtAsc(Long runId);
}
