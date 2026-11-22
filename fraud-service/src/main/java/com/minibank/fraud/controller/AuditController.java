package com.minibank.fraud.controller;

import com.minibank.fraud.dto.AuditLogResponse;
import com.minibank.fraud.dto.PageResponse;
import com.minibank.fraud.service.AuditService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit")
@RequiredArgsConstructor
@Tag(name = "Audit & Fraud", description = "Audit trail and fraud detection — search logs, view flagged transactions, and account trails (Elasticsearch-backed). ADMIN only")
public class AuditController {

    private final AuditService auditService;

    /**
     * Full-text search audit logs.
     * Enables "Pencarian Mutasi Tingkat Lanjut" — search by description, reference number, etc.
     */
    @Operation(summary = "Full-text search audit logs",
            description = "Searches description, details, reference number and transaction ID using Elasticsearch "
                    + "multi_match with fuzzy matching. Ordered by relevance unless `sort` is given")
    @GetMapping("/search")
    public ResponseEntity<PageResponse<AuditLogResponse>> search(
            @Parameter(description = "Search text (typos tolerated)", example = "makan siang")
            @RequestParam String q,
            @ParameterObject @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(auditService.search(q, pageable));
    }

    @Operation(summary = "Get account audit trail",
            description = "Audit entries where the account is sender or recipient. Default sort: newest first")
    @GetMapping("/account/{accountId}/trail")
    public ResponseEntity<PageResponse<AuditLogResponse>> getAccountTrail(
            @Parameter(description = "Account ID", example = "3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
            @PathVariable UUID accountId,
            @ParameterObject @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(auditService.getAccountAuditTrail(accountId, pageable));
    }

    @Operation(summary = "Get flagged transactions",
            description = "Transfers flagged as suspicious by the fraud engine (risk score >= 40). Default sort: newest first")
    @GetMapping("/flagged")
    public ResponseEntity<PageResponse<AuditLogResponse>> getFlagged(
            @ParameterObject @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(auditService.getFlaggedTransactions(pageable));
    }

    @Operation(summary = "Get all audit logs", description = "All audit entries. Default sort: newest first")
    @GetMapping
    public ResponseEntity<PageResponse<AuditLogResponse>> getAll(
            @ParameterObject @PageableDefault(size = 20, sort = "timestamp", direction = Sort.Direction.DESC)
            Pageable pageable) {
        return ResponseEntity.ok(auditService.getAllLogs(pageable));
    }
}
