TRUNCATE TABLE recurring_false_positive;
TRUNCATE TABLE recurring_series CASCADE;

ALTER TABLE recurring_series_member
    DROP COLUMN IF EXISTS occurred_on,
    DROP COLUMN IF EXISTS amount,
    DROP COLUMN IF EXISTS purpose,
    DROP COLUMN IF EXISTS counterparty_name,
    DROP COLUMN IF EXISTS counterparty_iban;
