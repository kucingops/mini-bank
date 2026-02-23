package com.minibank.transaction.entity;

import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Mapped entity for accounts table — read-only from Transaction Service perspective.
 * Account mutations (debit/credit) are done via native queries.
 */
@Entity
@Table(name = "accounts")
@Getter
@Setter
@NoArgsConstructor
public class Account {

    @Id
    private UUID id;

    @Column(name = "account_number")
    private String accountNumber;

    @Column(name = "holder_name")
    private String holderName;

    @Column(precision = 19, scale = 2)
    private BigDecimal balance;

    @Column(name = "daily_transfer_limit", precision = 19, scale = 2)
    private BigDecimal dailyTransferLimit;

    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;
}
