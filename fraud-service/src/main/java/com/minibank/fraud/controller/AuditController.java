package com.minibank.fraud.controller;

import com.minibank.fraud.document.AuditLog;
import com.minibank.fraud.service.AuditService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/audit")
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;

    /**
     * Full-text search audit logs.
     * Enables "Pencarian Mutasi Tingkat Lanjut" — search by description, reference number, etc.
     */
    @GetMapping("/search")
    public ResponseEntity<List<AuditLog>> search(@RequestParam String q) {
        return ResponseEntity.ok(auditService.search(q));
    }

    /**
     * Get audit trail for a specific account.
     */
    @GetMapping("/account/{accountId}/trail")
    public ResponseEntity<List<AuditLog>> getAccountTrail(@PathVariable String accountId) {
        return ResponseEntity.ok(auditService.getAccountAuditTrail(accountId));
    }

    /**
     * Get all flagged (suspicious) transactions.
     */
    @GetMapping("/flagged")
    public ResponseEntity<List<AuditLog>> getFlagged() {
        return ResponseEntity.ok(auditService.getFlaggedTransactions());
    }

    /**
     * Get all audit logs.
     */
    @GetMapping
    public ResponseEntity<List<AuditLog>> getAll() {
        return ResponseEntity.ok(auditService.getAllLogs());
    }
}
