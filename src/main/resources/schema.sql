CREATE TABLE IF NOT EXISTS accounts (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    name VARCHAR(100) COLLATE NOCASE NOT NULL UNIQUE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('BANK_ACCOUNT', 'CREDIT_CARD', 'PAYLATER')),
    opening_balance_sen BIGINT NOT NULL,
    opening_date DATE NOT NULL,
    active BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL
);

CREATE TABLE IF NOT EXISTS transactions (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    kind VARCHAR(10) NOT NULL CHECK (kind IN ('INCOME', 'EXPENSE', 'TRANSFER')),
    amount_sen BIGINT NOT NULL CHECK (amount_sen > 0),
    transaction_date DATE NOT NULL,
    description VARCHAR(250) NOT NULL,
    category VARCHAR(20) CHECK (category IN (
        'DINING', 'GROCERIES', 'FUEL', 'TRANSPORT', 'GRAB', 'SHOPPING', 'BILLS', 'FEES', 'OTHER'
    )),
    source_account_id INTEGER REFERENCES accounts(id) ON DELETE RESTRICT,
    destination_account_id INTEGER REFERENCES accounts(id) ON DELETE RESTRICT,
    source_message_key VARCHAR(200) UNIQUE,
    created_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    CHECK (
        (kind = 'EXPENSE' AND source_account_id IS NOT NULL
            AND destination_account_id IS NULL AND category IS NOT NULL)
        OR (kind = 'INCOME' AND source_account_id IS NULL
            AND destination_account_id IS NOT NULL AND category IS NULL)
        OR (kind = 'TRANSFER' AND source_account_id IS NOT NULL
            AND destination_account_id IS NOT NULL
            AND source_account_id <> destination_account_id AND category IS NULL)
    )
);

CREATE INDEX IF NOT EXISTS idx_transactions_date ON transactions(transaction_date);
CREATE INDEX IF NOT EXISTS idx_transactions_source ON transactions(source_account_id);
CREATE INDEX IF NOT EXISTS idx_transactions_destination ON transactions(destination_account_id);
