ALTER TABLE transactions
    ADD COLUMN IF NOT EXISTS accounting_date DATE;

UPDATE transactions
    SET accounting_date = booking_date
    WHERE accounting_date IS NULL;

ALTER TABLE transactions
    ALTER COLUMN accounting_date SET NOT NULL;
