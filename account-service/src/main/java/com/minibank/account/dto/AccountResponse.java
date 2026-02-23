package com.minibank.account.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
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
}
