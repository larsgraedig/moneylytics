ALTER TABLE transaction ADD COLUMN parent_id  BIGINT  REFERENCES transaction(id) ON DELETE RESTRICT;
ALTER TABLE transaction ADD COLUMN is_virtual BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transaction ADD COLUMN excluded   BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE transaction ALTER COLUMN fingerprint DROP NOT NULL;

CREATE INDEX idx_transaction_parent_id ON transaction(parent_id);
