-- Replaces the single amount column with side-specific amount_a / amount_b.
-- Migration option A: existing amount → amount_a, amount_b stays null.
ALTER TABLE transaction_offsets RENAME COLUMN amount TO amount_a;
ALTER TABLE transaction_offsets ADD COLUMN amount_b NUMERIC(19,4);
