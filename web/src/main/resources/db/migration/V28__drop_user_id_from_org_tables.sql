-- V25 introduced organization_id as the data-ownership column on all user-data tables,
-- but did not remove the now-redundant user_id FK. Entities no longer map user_id, so
-- any INSERT fails with a NOT NULL violation. Drop it from every affected table.
ALTER TABLE accounts                           DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE transactions                       DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE budgets                            DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE thresholds                         DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE collections                        DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE categories                         DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE transaction_groups                 DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE ignored_transactions               DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE recurring_series                   DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE recurring_false_positive           DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE recurring_sync_log                 DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE csv_profiles                       DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE recurring_classifier_token_counts  DROP COLUMN IF EXISTS user_id CASCADE;
ALTER TABLE recurring_classifier_class_counts  DROP COLUMN IF EXISTS user_id CASCADE;
