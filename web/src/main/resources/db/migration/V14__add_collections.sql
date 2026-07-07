CREATE TABLE IF NOT EXISTS collections (
    id      BIGSERIAL    PRIMARY KEY,
    user_id BIGINT       NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name    VARCHAR(255) NOT NULL,
    note    TEXT
);

CREATE TABLE IF NOT EXISTS collection_transactions (
    collection_id  BIGINT NOT NULL REFERENCES collections(id) ON DELETE CASCADE,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
    PRIMARY KEY (collection_id, transaction_id)
);
