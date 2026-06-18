-- Auth: password hash for username/password login (OAuth users have NULL)
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS password_hash VARCHAR(255);

-- CSV import profiles: remember column mappings per user and file format fingerprint
CREATE TABLE IF NOT EXISTS csv_profiles (
    id           BIGSERIAL PRIMARY KEY,
    user_id      BIGINT       NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    fingerprint  VARCHAR(500) NOT NULL,
    mapping_json TEXT         NOT NULL,
    CONSTRAINT uq_csv_profiles_user_fingerprint UNIQUE (user_id, fingerprint)
);
