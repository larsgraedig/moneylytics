CREATE TABLE transaction_offset_group_meta (
    id         BIGSERIAL PRIMARY KEY,
    user_id    BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    group_key  BIGINT NOT NULL,
    name       VARCHAR(255),
    comment    TEXT,
    UNIQUE (user_id, group_key)
);
