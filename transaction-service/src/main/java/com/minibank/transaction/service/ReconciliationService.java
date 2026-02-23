package com.minibank.transaction.service;

import com.minibank.transaction.dto.ReconciliationReport;
import com.minibank.transaction.entity.Transaction;
import com.minibank.transaction.repository.TransactionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Reconciliation Service — EOD (End of Day) reporting.
 * 
 * Demonstrates:
 * - Advance Native SQL Query (Window Functions, CTE)
 * - Java Stream API (aggregation, filtering, grouping)
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ReconciliationService {

    private final TransactionRepository transactionRepository;

    /**
     * Generate daily reconciliation report.
     * Uses Java Stream for aggregation and Native SQL for balance calculations.
     */
    public ReconciliationReport generateDailyReport(LocalDate date) {
        LocalDateTime startOfDay = LocalDateTime.of(date, LocalTime.MIN);
        LocalDateTime endOfDay = LocalDateTime.of(date, LocalTime.MAX);

        List<Transaction> transactions = transactionRepository.findByDateRange(startOfDay, endOfDay);

        // Java Stream: aggregate transaction statistics
        Map<String, Long> statusCounts = transactions.stream()
                .collect(Collectors.groupingBy(Transaction::getStatus, Collectors.counting()));

        BigDecimal totalAmount = transactions.stream()
                .filter(t -> "COMPLETED".equals(t.getStatus()))
                .map(Transaction::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        // Native SQL: get average daily balances using Window Functions
        List<Object[]> avgBalances = transactionRepository.findAverageDailyBalances(date.toString());

        List<ReconciliationReport.DailyAverageBalance> balanceDetails = avgBalances.stream()
                .map(row -> ReconciliationReport.DailyAverageBalance.builder()
                        .accountId(UUID.fromString(row[0].toString()))
                        .accountNumber(row[1].toString())
                        .holderName(row[2].toString())
                        .averageDailyBalance(row[8] != null ? new BigDecimal(row[8].toString()) : BigDecimal.ZERO)
                        .openingBalance(row[4] != null ? new BigDecimal(row[4].toString()) : BigDecimal.ZERO)
                        .closingBalance(row[5] != null ? new BigDecimal(row[5].toString()) : BigDecimal.ZERO)
                        .build())
                .collect(Collectors.toList());

        return ReconciliationReport.builder()
                .reportDate(date)
                .totalTransactions(transactions.size())
                .completedTransactions(statusCounts.getOrDefault("COMPLETED", 0L).intValue())
                .rejectedTransactions(statusCounts.getOrDefault("REJECTED", 0L).intValue())
                .pendingTransactions(statusCounts.getOrDefault("PENDING", 0L).intValue())
                .totalAmountTransferred(totalAmount)
                .averageBalances(balanceDetails)
                .build();
    }
}
