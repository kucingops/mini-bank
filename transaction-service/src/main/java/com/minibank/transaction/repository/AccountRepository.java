package com.minibank.transaction.repository;

import com.minibank.transaction.entity.Account;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.UUID;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    /**
     * Atomic debit operation with balance check — prevents negative balance.
     * Returns number of rows affected (1 = success, 0 = insufficient balance).
     */
    @Modifying
    @Query(value = "UPDATE accounts SET balance = balance - :amount, updated_at = NOW() " +
            "WHERE id = :accountId AND balance >= :amount", nativeQuery = true)
    int debitBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);

    /**
     * Atomic credit operation.
     */
    @Modifying
    @Query(value = "UPDATE accounts SET balance = balance + :amount, updated_at = NOW() " +
            "WHERE id = :accountId", nativeQuery = true)
    int creditBalance(@Param("accountId") UUID accountId, @Param("amount") BigDecimal amount);
}
