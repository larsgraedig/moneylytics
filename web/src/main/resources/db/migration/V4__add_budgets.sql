CREATE TABLE IF NOT EXISTS budgets (
    id            BIGSERIAL     PRIMARY KEY,
    user_id       BIGINT        NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name          VARCHAR(255)  NOT NULL,
    target_amount DECIMAL(19,2),
    note          TEXT
);

CREATE TABLE IF NOT EXISTS budget_transactions (
    id             BIGSERIAL     PRIMARY KEY,
    budget_id      BIGINT        NOT NULL REFERENCES budgets(id) ON DELETE CASCADE,
    transaction_id BIGINT        NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    amount         DECIMAL(19,2),
    CONSTRAINT uq_budget_transaction UNIQUE (budget_id, transaction_id)
);
