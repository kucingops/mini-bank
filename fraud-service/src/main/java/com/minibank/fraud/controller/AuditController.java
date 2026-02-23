package com.minibank.fraud.controller;

import com.minibank.fraud.document.AuditLog;
import com.minibank.fraud.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
@Tag(name = "Audit & Fraud", description = "Audit trail and fraud detection — search logs, view flagged transactions, and account trails (Elasticsearch-backed)")
public class AuditController {

    private final AuditService auditService;

    /**
     * Full-text search audit logs.
     * Enables "Pencarian Mutasi Tingkat Lanjut" — search by description, reference number, etc.
     */
    @Operation(summary = "Full-text search audit logs", description = "Search audit logs by description, reference number, etc. using Elasticsearch multi_match with fuzzy matching")
    @GetMapping("/search")
    public ResponseEntity<List<AuditLog>> search(@RequestParam String q) {
        return ResponseEntity.ok(auditService.search(q));
    }

    /**
     * Get audit trail for a specific account.
     */
    @Operation(summary = "Get account audit trail", description = "Retrieves the complete audit trail for a specific account, ordered chronologically")
    @GetMapping("/account/{accountId}/trail")
    public ResponseEntity<List<AuditLog>> getAccountTrail(@PathVariable String accountId) {
        return ResponseEntity.ok(auditService.getAccountAuditTrail(accountId));
    }

    /**
     * Get all flagged (suspicious) transactions.
     */
    @Operation(summary = "Get flagged transactions", description = "Returns all transactions flagged as suspicious by the fraud detection engine (risk score ≥ 40)")
    @GetMapping("/flagged")
    public ResponseEntity<List<AuditLog>> getFlagged() {
        return ResponseEntity.ok(auditService.getFlaggedTransactions());
    }

    /**
     * Get all audit logs.
     */
    @Operation(summary = "Get all audit logs", description = "Retrieves all audit log entries from Elasticsearch")
    @GetMapping
    public ResponseEntity<List<AuditLog>> getAll() {
        return ResponseEntity.ok(auditService.getAllLogs());
    }
}
