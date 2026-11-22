package com.minibank.transaction.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Transfer and its processing status")
public class TransferResponse {
    @Schema(description = "Transaction ID", example = "7c9e6679-7425-40de-944b-e07fc1f90ae7")
    private UUID transactionId;
    @Schema(description = "Human-readable reference number", example = "TXN17953207695290297")
    private String referenceNo;
    @Schema(description = "Source account", example = "3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
    private UUID fromAccountId;
    @Schema(description = "Destination account", example = "9b2e4f6a-1c3d-4e5f-8a7b-6c5d4e3f2a1b")
    private UUID toAccountId;
    @Schema(description = "Amount in IDR", example = "1000000.00")
    private BigDecimal amount;
    @Schema(description = "PENDING until the fraud check finishes; then COMPLETED, REJECTED (fraud) or FAILED (insufficient funds at debit time)", example = "PENDING", allowableValues = {"PENDING", "COMPLETED", "REJECTED", "FAILED"})
    private String status;
    @Schema(description = "Fraud check outcome", example = "PENDING", allowableValues = {"PENDING", "PASSED", "FLAGGED"})
    private String fraudCheckStatus;
    @Schema(description = "Free-text note", example = "Bayar makan siang")
    private String description;
    @Schema(description = "Creation time", example = "2026-02-25T10:15:30")
    private LocalDateTime createdAt;
}
