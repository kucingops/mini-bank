package com.minibank.fraud.dto;

import com.minibank.fraud.document.AuditLog;
import io.swagger.v3.oas.annotations.media.Schema;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * API view of an audit log entry, decoupled from the Elasticsearch document.
 */
@Schema(description = "Fraud check audit entry for one transfer")
public record AuditLogResponse(
        @Schema(description = "Audit entry ID", example = "b5c1d2e3-f4a5-4b6c-8d7e-9f0a1b2c3d4e")
        String id,

        @Schema(description = "Transaction ID", format = "uuid", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
        String transactionId,

        @Schema(description = "Transaction reference number", example = "TXN17953207695290297")
        String referenceNo,

        @Schema(description = "Source account", format = "uuid", example = "3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
        String fromAccountId,

        @Schema(description = "Destination account", format = "uuid", example = "9b2e4f6a-1c3d-4e5f-8a7b-6c5d4e3f2a1b")
        String toAccountId,

        @Schema(description = "Amount in IDR", example = "75000000")
        BigDecimal amount,

        @Schema(description = "Transfer free-text note", example = "Transfer besar")
        String description,

        @Schema(description = "Event type", example = "TRANSFER_FRAUD_CHECK")
        String eventType,

        @Schema(description = "Outcome sent back to the transaction service", example = "REJECTED",
                allowableValues = {"VALIDATED", "REJECTED"})
        String status,

        @Schema(description = "Fraud check result", example = "FLAGGED", allowableValues = {"PASSED", "FLAGGED"})
        String fraudCheckResult,

        @Schema(description = "Total risk score (>= 40 is blocked)", example = "50")
        int riskScore,

        @Schema(description = "Risk level", example = "MEDIUM", allowableValues = {"LOW", "MEDIUM", "HIGH"})
        String riskLevel,

        @Schema(description = "Which fraud rules fired",
                example = "LARGE_AMOUNT: Transfer Rp 75000000 exceeds threshold Rp 50000000. SUSPICIOUS_HOUR: Transfer at 2:00. ")
        String details,

        @Schema(description = "When the check ran", example = "2026-02-25T02:10:00")
        LocalDateTime timestamp
) {
    public static AuditLogResponse from(AuditLog log) {
        return new AuditLogResponse(log.getId(), log.getTransactionId(), log.getReferenceNo(),
                log.getFromAccountId(), log.getToAccountId(), log.getAmount(), log.getDescription(),
                log.getEventType(), log.getStatus(), log.getFraudCheckResult(), log.getRiskScore(),
                log.getRiskLevel(), log.getDetails(), log.getTimestamp());
    }
}
