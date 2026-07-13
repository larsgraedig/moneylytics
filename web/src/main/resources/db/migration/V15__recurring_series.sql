CREATE TABLE recurring_series (
    id              BIGSERIAL PRIMARY KEY,
    user_id         BIGINT        NOT NULL REFERENCES users(id),
    label           VARCHAR(255)  NOT NULL,
    type            VARCHAR(50)   NOT NULL,
    direction       VARCHAR(10)   NOT NULL,
    cadence         VARCHAR(20)   NOT NULL,
    interval_days   INT           NOT NULL,
    expected_amount DECIMAL(19,4) NOT NULL,
    amount_variable BOOLEAN       NOT NULL,
    currency        VARCHAR(3)    NOT NULL,
    account_iban    VARCHAR(34)   NOT NULL,
    first_seen      DATE          NOT NULL,
    last_seen       DATE          NOT NULL,
    occurrence_count INT          NOT NULL,
    next_expected_date DATE       NOT NULL,
    status          VARCHAR(20)   NOT NULL
);

CREATE TABLE recurring_series_member (
    id             BIGSERIAL PRIMARY KEY,
    series_id      BIGINT        NOT NULL REFERENCES recurring_series(id),
    transaction_id BIGINT        NOT NULL REFERENCES transactions(id),
    occurred_on    DATE          NOT NULL,
    amount         DECIMAL(19,4) NOT NULL
);
