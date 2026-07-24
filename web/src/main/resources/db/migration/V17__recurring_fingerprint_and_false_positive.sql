ALTER TABLE recurring_series ADD COLUMN fingerprint VARCHAR(512) NOT NULL DEFAULT '';

CREATE TABLE recurring_false_positive (
    id          BIGSERIAL PRIMARY KEY,
    user_id     BIGINT        NOT NULL REFERENCES users(id),
    fingerprint VARCHAR(512)  NOT NULL,
    created_at  DATE          NOT NULL,
    UNIQUE(user_id, fingerprint)
);
