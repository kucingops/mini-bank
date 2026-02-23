package com.minibank.fraud.service;

import com.minibank.fraud.document.AuditLog;
import com.minibank.fraud.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Audit Service — indexes and searches audit trail in Elasticsearch.
 * 
 * Demonstrates:
 * - Elastic & Other Non-Relational DB
 * - Java Stream API for data transformation
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuditService {

    private final AuditLogRepository auditLogRepository;

    /**
     * Index an audit log entry to Elasticsearch.
     */
    public AuditLog indexAuditLog(AuditLog auditLog) {
        if (auditLog.getId() == null) {
            auditLog.setId(UUID.randomUUID().toString());
        }
        if (auditLog.getTimestamp() == null) {
            auditLog.setTimestamp(LocalDateTime.now());
        }

        AuditLog saved = auditLogRepository.save(auditLog);
        log.info("Audit log indexed: {} for transaction {}", saved.getId(), saved.getTransactionId());
        return saved;
    }

    /**
     * Full-text search across audit logs.
     * Enables "Pencarian Mutasi Tingkat Lanjut" — search by description, reference, etc.
     */
    public List<AuditLog> search(String keyword) {
        log.info("Searching audit logs for: {}", keyword);
        return auditLogRepository.searchByKeyword(keyword);
    }

    /**
     * Get audit trail for a specific account.
     * Uses Java Stream for processing results.
     */
    public List<AuditLog> getAccountAuditTrail(String accountId) {
        List<AuditLog> logs = auditLogRepository
                .findByFromAccountIdOrToAccountIdOrderByTimestampDesc(accountId, accountId);

        // Java Stream: filter and transform audit trail
        return logs.stream()
                .filter(log -> log.getTimestamp() != null)
                .sorted((a, b) -> b.getTimestamp().compareTo(a.getTimestamp()))
                .collect(Collectors.toList());
    }

    /**
     * Get all flagged (suspicious) transactions.
     */
    public List<AuditLog> getFlaggedTransactions() {
        return auditLogRepository.findByFraudCheckResultOrderByTimestampDesc("FLAGGED");
    }

    /**
     * Get all audit logs.
     * Demonstrates Java Stream with Iterable conversion.
     */
    public List<AuditLog> getAllLogs() {
        return StreamSupport.stream(auditLogRepository.findAll().spliterator(), false)
                .sorted((a, b) -> {
                    if (a.getTimestamp() == null) return 1;
                    if (b.getTimestamp() == null) return -1;
                    return b.getTimestamp().compareTo(a.getTimestamp());
                })
                .collect(Collectors.toList());
    }
}
