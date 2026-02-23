-- ============================================================
-- minibank Bank - Database Schema
-- ============================================================

-- Accounts table
CREATE TABLE IF NOT EXISTS accounts (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    account_number  VARCHAR(20) NOT NULL UNIQUE,
    holder_name     VARCHAR(100) NOT NULL,
    email           VARCHAR(100),
    balance         DECIMAL(19,2) NOT NULL DEFAULT 0.00,
    daily_transfer_limit DECIMAL(19,2) NOT NULL DEFAULT 50000000.00,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Transactions table
CREATE TABLE IF NOT EXISTS transactions (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reference_no    VARCHAR(30) NOT NULL UNIQUE,
    from_account_id UUID NOT NULL REFERENCES accounts(id),
    to_account_id   UUID NOT NULL REFERENCES accounts(id),
    amount          DECIMAL(19,2) NOT NULL,
    type            VARCHAR(20) NOT NULL DEFAULT 'TRANSFER',
    status          VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    description     VARCHAR(255),
    fraud_check_status VARCHAR(20) DEFAULT 'PENDING',
    created_at      TIMESTAMP NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMP NOT NULL DEFAULT NOW()
);

-- Indexes for performance
CREATE INDEX idx_transactions_from_account ON transactions(from_account_id);
CREATE INDEX idx_transactions_to_account ON transactions(to_account_id);
CREATE INDEX idx_transactions_status ON transactions(status);
CREATE INDEX idx_transactions_created_at ON transactions(created_at);
CREATE INDEX idx_transactions_reference_no ON transactions(reference_no);
CREATE INDEX idx_accounts_account_number ON accounts(account_number);
