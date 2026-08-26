CREATE TABLE import_file (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    import_id BIGINT NOT NULL REFERENCES transaction_import(id) ON DELETE CASCADE,
    filename VARCHAR(500) NOT NULL,
    checksum VARCHAR(64) NOT NULL,
    file_type VARCHAR(10) NOT NULL,
    transaction_count INT NOT NULL DEFAULT 0,
    status VARCHAR(10) NOT NULL DEFAULT 'ACTIVE'
);

CREATE INDEX idx_import_file_import_id ON import_file(import_id);

INSERT INTO import_file (import_id, filename, checksum, file_type, transaction_count, status)
SELECT id, filename, checksum, file_type, transaction_count, status
FROM transaction_import;

ALTER TABLE transaction ADD COLUMN import_file_id BIGINT REFERENCES import_file(id) ON DELETE SET NULL;

CREATE INDEX idx_transaction_import_file_id ON transaction(import_file_id);

UPDATE transaction t
SET import_file_id = f.id
FROM import_file f
WHERE f.import_id = t.import_id;

ALTER TABLE transaction_import
    DROP COLUMN filename,
    DROP COLUMN checksum,
    DROP COLUMN file_type,
    DROP COLUMN transaction_count;
