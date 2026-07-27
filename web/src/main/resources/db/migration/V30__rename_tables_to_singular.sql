-- Rename all tables from plural to singular naming convention.
-- "user" must be quoted because it is a reserved word in PostgreSQL.
ALTER TABLE accounts                          RENAME TO account;
ALTER TABLE budgets                           RENAME TO budget;
ALTER TABLE budget_transactions               RENAME TO budget_transaction;
ALTER TABLE categories                        RENAME TO category;
ALTER TABLE collections                       RENAME TO collection;
ALTER TABLE collection_transactions           RENAME TO collection_transaction;
ALTER TABLE csv_profiles                      RENAME TO csv_profile;
ALTER TABLE ignored_transactions              RENAME TO ignored_transaction;
ALTER TABLE organization_invitations          RENAME TO organization_invitation;
ALTER TABLE organization_members              RENAME TO organization_member;
ALTER TABLE organizations                     RENAME TO organization;
ALTER TABLE recurring_classifier_class_counts RENAME TO recurring_classifier_class_count;
ALTER TABLE recurring_classifier_token_counts RENAME TO recurring_classifier_token_count;
ALTER TABLE thresholds                        RENAME TO threshold;
ALTER TABLE transaction_group_members         RENAME TO transaction_group_member;
ALTER TABLE transaction_groups                RENAME TO transaction_group;
ALTER TABLE transaction_offsets               RENAME TO transaction_offset;
ALTER TABLE transactions                      RENAME TO transaction;
ALTER TABLE users                             RENAME TO "user";
