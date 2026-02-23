package com.minibank.fraud.repository;

import com.minibank.fraud.document.AuditLog;
import org.springframework.data.elasticsearch.annotations.Query;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Elasticsearch repository for audit logs.
 * Provides both derived queries and custom Elasticsearch DSL queries.
 */
@Repository
public interface AuditLogRepository extends ElasticsearchRepository<AuditLog, String> {

    /**
     * Find audit logs by transaction ID.
     */
    List<AuditLog> findByTransactionIdOrderByTimestampDesc(String transactionId);

    /**
     * Find audit logs by account (source or destination).
     */
    List<AuditLog> findByFromAccountIdOrToAccountIdOrderByTimestampDesc(String fromId, String toId);

    /**
     * Full-text search across description and details fields.
     * This enables the "Pencarian Mutasi Tingkat Lanjut" feature.
     */
    @Query("""
            {
              "multi_match": {
                "query": "?0",
                "fields": ["description", "details", "referenceNo", "transactionId"],
                "type": "best_fields",
                "fuzziness": "AUTO"
              }
            }
            """)
    List<AuditLog> searchByKeyword(String keyword);

    /**
     * Find flagged/suspicious transactions.
     */
    List<AuditLog> findByFraudCheckResultOrderByTimestampDesc(String fraudCheckResult);

    /**
     * Find by risk level.
     */
    List<AuditLog> findByRiskLevelOrderByTimestampDesc(String riskLevel);
}
