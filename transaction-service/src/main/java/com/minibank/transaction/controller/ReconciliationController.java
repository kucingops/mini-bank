package com.minibank.transaction.controller;

import com.minibank.transaction.dto.ReconciliationReport;
import com.minibank.transaction.service.ReconciliationService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/reconciliation")
@RequiredArgsConstructor
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /**
     * Generate daily reconciliation report.
     * Uses Advanced Native SQL with Window Functions (SUM OVER, LAG, LEAD).
     */
    @GetMapping("/daily")
    public ResponseEntity<ReconciliationReport> getDailyReport(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reconciliationService.generateDailyReport(date));
    }
}
