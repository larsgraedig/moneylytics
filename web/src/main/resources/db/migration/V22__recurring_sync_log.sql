CREATE TABLE recurring_sync_log (
    id                        BIGSERIAL PRIMARY KEY,
    user_id                   BIGINT NOT NULL REFERENCES users(id),
    ran_at                    TIMESTAMP NOT NULL,
    series_updated_count      INT NOT NULL DEFAULT 0,
    transactions_linked_count INT NOT NULL DEFAULT 0
);

CREATE TABLE recurring_sync_log_entry (
    id                BIGSERIAL PRIMARY KEY,
    log_id            BIGINT NOT NULL REFERENCES recurring_sync_log(id),
    series_id         BIGINT,
    series_label      VARCHAR(255) NOT NULL,
    transaction_id    BIGINT NOT NULL REFERENCES transactions(id),
    booking_date      DATE NOT NULL,
    amount            DECIMAL(19,4) NOT NULL,
    counterparty_name VARCHAR(255)
);
