CREATE TABLE stripe_customer (
    id                      BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id                 BIGINT NOT NULL UNIQUE REFERENCES "user"(id),
    stripe_customer_id      VARCHAR(255) NOT NULL UNIQUE,
    stripe_subscription_id  VARCHAR(255),
    subscription_status     VARCHAR(50),
    current_period_end      BIGINT,
    price_id                VARCHAR(255)
);

CREATE TABLE invoice (
    id                BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id           BIGINT NOT NULL REFERENCES "user"(id),
    stripe_invoice_id VARCHAR(255) UNIQUE,
    amount_cents      INTEGER NOT NULL,
    currency          VARCHAR(3) NOT NULL DEFAULT 'eur',
    status            VARCHAR(50) NOT NULL,
    period_start      TIMESTAMP NOT NULL,
    period_end        TIMESTAMP NOT NULL,
    pdf_data          BYTEA,
    issued_at         TIMESTAMP NOT NULL
);
