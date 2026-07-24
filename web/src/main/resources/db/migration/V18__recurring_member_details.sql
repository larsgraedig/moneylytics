ALTER TABLE recurring_series_member
    ADD COLUMN purpose           TEXT,
    ADD COLUMN counterparty_name VARCHAR(255),
    ADD COLUMN counterparty_iban VARCHAR(34);
