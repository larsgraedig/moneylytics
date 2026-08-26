CREATE TABLE csv_preview_session (
    id         UUID      PRIMARY KEY,
    rows_json  TEXT      NOT NULL,
    expires_at TIMESTAMP NOT NULL
);
