package com.minibank.account.dto;

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
public class AccountResponse {
    private UUID id;
    private String accountNumber;
    private String holderName;
    private String email;
    private BigDecimal balance;
    private BigDecimal dailyTransferLimit;
    private String status;
    private LocalDateTime createdAt;
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
