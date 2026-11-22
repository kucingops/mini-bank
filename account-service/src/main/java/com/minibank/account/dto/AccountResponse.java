package com.minibank.account.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Schema(description = "Bank account. Non-owners only receive id, accountNumber, holderName and status")
public class AccountResponse {
    @Schema(description = "Account ID", example = "3f1c2a9e-8b7d-4c3a-9e2f-1a2b3c4d5e6f")
    private UUID id;
    @Schema(description = "10-digit account number", example = "1234567890")
    private String accountNumber;
    @Schema(description = "Account holder's name", example = "Ahmad Syarif")
    private String holderName;
    @Schema(description = "Contact email", example = "syarif@minibank.com")
    private String email;
    @Schema(description = "Balance in IDR", example = "100000000.00")
    private BigDecimal balance;
    @Schema(description = "Daily outgoing transfer limit in IDR", example = "50000000.00")
    private BigDecimal dailyTransferLimit;
    @Schema(description = "Account status (only ACTIVE accounts can send or receive)", example = "ACTIVE")
    private String status;
    @Schema(description = "Creation time", example = "2026-02-25T08:00:00")
    private LocalDateTime createdAt;
    @Schema(description = "Last update time", example = "2026-02-25T08:00:00")
    private LocalDateTime updatedAt;

    /**
     * Only the fields another user needs to address a transfer — no balance, email or limits.
     */
    public static AccountResponse publicView(AccountResponse full) {
        return AccountResponse.builder()
                .id(full.getId())
                .accountNumber(full.getAccountNumber())
                .holderName(full.getHolderName())
                .status(full.getStatus())
                .build();
    }
}
