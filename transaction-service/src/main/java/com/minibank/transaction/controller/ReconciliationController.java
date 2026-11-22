package com.minibank.transaction.controller;

import com.minibank.transaction.dto.ReconciliationReport;
import com.minibank.transaction.service.ReconciliationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/reconciliation")
@RequiredArgsConstructor
@Tag(name = "Reconciliation", description = "End-of-day reconciliation reports using advanced SQL with window functions")
public class ReconciliationController {

    private final ReconciliationService reconciliationService;

    /**
     * Generate daily reconciliation report.
     * Uses Advanced Native SQL with Window Functions (SUM OVER, LAG, LEAD).
     */
    @Operation(summary = "Generate daily reconciliation report (ADMIN)", description = "Produces an EOD report using advanced native SQL with SUM() OVER, LAG, LEAD window functions and CTEs")
    @GetMapping("/daily")
    public ResponseEntity<ReconciliationReport> getDailyReport(
            @Parameter(description = "Report date (ISO yyyy-MM-dd)", example = "2026-02-25")
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
        return ResponseEntity.ok(reconciliationService.generateDailyReport(date));
    }
}
