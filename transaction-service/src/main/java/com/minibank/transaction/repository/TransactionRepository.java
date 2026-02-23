package com.minibank.transaction.repository;

import com.minibank.transaction.entity.Transaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
public interface TransactionRepository extends JpaRepository<Transaction, UUID> {

    List<Transaction> findByFromAccountIdOrToAccountIdOrderByCreatedAtDesc(UUID fromId, UUID toId);

    /**
     * Sum of completed transfers from an account today — for daily limit check.
     */
    @Query("SELECT COALESCE(SUM(t.amount), 0) FROM Transaction t " +
            "WHERE t.fromAccountId = :accountId " +
            "AND t.status = 'COMPLETED' " +
            "AND t.createdAt >= :startOfDay")
    BigDecimal sumDailyTransfers(@Param("accountId") UUID accountId,
                                 @Param("startOfDay") LocalDateTime startOfDay);

    /**
     * Count transactions in the last hour — for fraud rate limiting.
     */
    @Query("SELECT COUNT(t) FROM Transaction t " +
            "WHERE t.fromAccountId = :accountId " +
            "AND t.createdAt >= :oneHourAgo")
    long countRecentTransfers(@Param("accountId") UUID accountId,
                              @Param("oneHourAgo") LocalDateTime oneHourAgo);

    /**
     * Find transactions by date range — for reconciliation.
     */
    @Query("SELECT t FROM Transaction t WHERE t.createdAt BETWEEN :start AND :end ORDER BY t.createdAt")
    List<Transaction> findByDateRange(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /**
     * Advanced Native SQL: Daily reconciliation with running balance using Window Functions.
     * Demonstrates SUM() OVER (PARTITION BY ... ORDER BY ...) for running balance calculation.
     */
    @Query(value = """
            SELECT 
                t.from_account_id as account_id,
                a.account_number,
                a.holder_name,
                t.amount,
                t.status,
                t.created_at,
                SUM(CASE 
                    WHEN t.status = 'COMPLETED' THEN 
                        CASE WHEN t.from_account_id = a.id THEN -t.amount ELSE t.amount END
                    ELSE 0 
                END) OVER (PARTITION BY a.id ORDER BY t.created_at) as running_balance,
                LAG(t.amount) OVER (PARTITION BY a.id ORDER BY t.created_at) as prev_amount,
                LEAD(t.amount) OVER (PARTITION BY a.id ORDER BY t.created_at) as next_amount
            FROM transactions t
            JOIN accounts a ON (t.from_account_id = a.id OR t.to_account_id = a.id)
            WHERE DATE(t.created_at) = :date
            ORDER BY a.id, t.created_at
            """, nativeQuery = true)
    List<Object[]> findDailyReconciliation(@Param("date") String date);

    /**
     * Advanced Native SQL: Average daily balance using Window Functions and subquery.
     * Uses Recursive CTE concept for generating balance snapshots.
     */
    @Query(value = """
            WITH daily_movements AS (
                SELECT 
                    a.id as account_id,
                    a.account_number,
                    a.holder_name,
                    a.balance as current_balance,
                    COALESCE(SUM(CASE 
                        WHEN t.from_account_id = a.id AND t.status = 'COMPLETED' THEN -t.amount
                        WHEN t.to_account_id = a.id AND t.status = 'COMPLETED' THEN t.amount
                        ELSE 0
                    END), 0) as net_movement,
                    COUNT(t.id) as total_txns,
                    MIN(t.created_at) as first_txn_time,
                    MAX(t.created_at) as last_txn_time
                FROM accounts a
                LEFT JOIN transactions t ON (t.from_account_id = a.id OR t.to_account_id = a.id)
                    AND DATE(t.created_at) = :date
                WHERE a.status = 'ACTIVE'
                GROUP BY a.id, a.account_number, a.holder_name, a.balance
            )
            SELECT 
                account_id,
                account_number,
                holder_name,
                current_balance,
                (current_balance - net_movement) as opening_balance,
                current_balance as closing_balance,
                net_movement,
                total_txns,
                CASE WHEN total_txns > 0 
                    THEN ((current_balance - net_movement) + current_balance) / 2.0
                    ELSE current_balance 
                END as avg_daily_balance
            FROM daily_movements
            ORDER BY account_number
            """, nativeQuery = true)
    List<Object[]> findAverageDailyBalances(@Param("date") String date);

    /**
     * Update transaction status.
     */
    @Modifying
    @Query("UPDATE Transaction t SET t.status = :status, t.fraudCheckStatus = :fraudStatus, t.updatedAt = CURRENT_TIMESTAMP WHERE t.id = :id")
    void updateStatus(@Param("id") UUID id,
                      @Param("status") String status,
                      @Param("fraudStatus") String fraudStatus);
}
