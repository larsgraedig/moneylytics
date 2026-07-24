ALTER TABLE accounts
    ADD COLUMN balance     DECIMAL(19, 4),
    ADD COLUMN balance_date DATE;
