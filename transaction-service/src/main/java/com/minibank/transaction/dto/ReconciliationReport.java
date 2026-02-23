package com.minibank.transaction.dto;

import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ReconciliationReport {
    private LocalDate reportDate;
    private int totalTransactions;
    private int completedTransactions;
    private int rejectedTransactions;
    private int pendingTransactions;
    private BigDecimal totalAmountTransferred;
    private List<DailyAverageBalance> averageBalances;

    @Data
    @NoArgsConstructor
    @AllArgsConstructor
    @Builder
    public static class DailyAverageBalance {
        private UUID accountId;
        private String accountNumber;
        private String holderName;
        private BigDecimal averageDailyBalance;
        private BigDecimal openingBalance;
        private BigDecimal closingBalance;
    }
}
