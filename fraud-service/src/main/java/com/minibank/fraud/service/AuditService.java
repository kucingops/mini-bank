package com.minibank.fraud.service;

import com.minibank.fraud.document.AuditLog;
import com.minibank.fraud.dto.AuditLogResponse;
import com.minibank.fraud.dto.PageResponse;
import com.minibank.fraud.repository.AuditLogRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

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
     * Results are ordered by relevance unless a sort is requested.
     */
    public PageResponse<AuditLogResponse> search(String keyword, Pageable pageable) {
        log.info("Searching audit logs for: {}", keyword);
        return toPage(auditLogRepository.searchByKeyword(keyword, pageable));
    }

    /**
     * Get audit trail for a specific account (as sender or recipient).
     */
    public PageResponse<AuditLogResponse> getAccountAuditTrail(UUID accountId, Pageable pageable) {
        String id = accountId.toString();
        return toPage(auditLogRepository.findByFromAccountIdOrToAccountId(id, id, pageable));
    }

    /**
     * Get flagged (suspicious) transactions.
     */
    public PageResponse<AuditLogResponse> getFlaggedTransactions(Pageable pageable) {
        return toPage(auditLogRepository.findByFraudCheckResult("FLAGGED", pageable));
    }

    /**
     * Get all audit logs.
     */
    public PageResponse<AuditLogResponse> getAllLogs(Pageable pageable) {
        return toPage(auditLogRepository.findAll(pageable));
    }

    /**
     * Java Stream: map Elasticsearch documents to API DTOs.
     */
    private PageResponse<AuditLogResponse> toPage(Page<AuditLog> page) {
        List<AuditLogResponse> content = page.getContent().stream()
                .map(AuditLogResponse::from)
                .collect(Collectors.toList());
        return PageResponse.of(new PageImpl<>(content, page.getPageable(), page.getTotalElements()));
    }
}
