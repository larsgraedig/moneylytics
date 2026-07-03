-- Replaces the old group-tracking approaches with explicit
-- transaction_group_members and transaction_offsets.group_id.
--
-- Two possible starting states are handled:
--
--   (A) Local-dev / intermediate state (what ddl.sql shows):
--       transactions.group_id  FK → transaction_groups   (one group per tx row)
--       transaction_offsets    no group_id column
--       transaction_group_members  does not exist
--
--   (B) Production state after V9:
--       transaction_offset_group_meta  (user_id, group_key, name, comment)
--       groups inferred at runtime via connected-component computation
--
-- Result after this migration (both paths):
--   transaction_group_members  (group_id, transaction_id) — composite PK
--   transaction_offsets.group_id  FK → transaction_groups
--   transactions.group_id      dropped
--   transaction_offset_group_meta  dropped

-- 1. Groups table (created by Hibernate on local-dev; must be created explicitly in prod)
CREATE TABLE IF NOT EXISTS transaction_groups (
    id      BIGSERIAL PRIMARY KEY,
    user_id BIGINT    NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name    VARCHAR(255),
    comment TEXT
);

-- 2. Junction table for group membership
CREATE TABLE IF NOT EXISTS transaction_group_members (
    group_id       BIGINT NOT NULL REFERENCES transaction_groups(id) ON DELETE CASCADE,
    transaction_id BIGINT NOT NULL REFERENCES transactions(id)       ON DELETE CASCADE,
    PRIMARY KEY (group_id, transaction_id)
);

-- 3. group_id on offsets
ALTER TABLE transaction_offsets
    ADD COLUMN IF NOT EXISTS group_id BIGINT REFERENCES transaction_groups(id) ON DELETE SET NULL;

-- 4. Data migration
DO $$
DECLARE
    v_rec          RECORD;
    v_group_id     BIGINT;
    v_tx_id        BIGINT;
    v_name         VARCHAR(255);
    v_comment      TEXT;
BEGIN

    -- -----------------------------------------------------------------------
    -- Path A: transactions.group_id exists (DDL / local-dev state)
    -- -----------------------------------------------------------------------
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE  table_name = 'transactions' AND column_name = 'group_id'
    ) THEN

        -- Populate junction table from the denormalised column
        INSERT INTO transaction_group_members (group_id, transaction_id)
        SELECT group_id, id
        FROM   transactions
        WHERE  group_id IS NOT NULL
        ON CONFLICT DO NOTHING;

        -- Set group_id on offsets where both sides share a group
        UPDATE transaction_offsets o
        SET    group_id = (
            SELECT m1.group_id
            FROM   transaction_group_members m1
            JOIN   transaction_group_members m2
                   ON  m2.group_id       = m1.group_id
                   AND m2.transaction_id = o.transaction_b_id
            WHERE  m1.transaction_id = o.transaction_a_id
            LIMIT  1
        )
        WHERE  o.group_id IS NULL
          AND  EXISTS (
            SELECT 1
            FROM   transaction_group_members m1
            JOIN   transaction_group_members m2
                   ON  m2.group_id       = m1.group_id
                   AND m2.transaction_id = o.transaction_b_id
            WHERE  m1.transaction_id = o.transaction_a_id
        );

    END IF;

    -- -----------------------------------------------------------------------
    -- Path B: transaction_offset_group_meta exists (V9 production state)
    --
    -- Groups were never stored explicitly; they are implied by the connected
    -- components of offset pairs.  group_key = MIN(tx_id) in each component.
    -- -----------------------------------------------------------------------
    IF EXISTS (
        SELECT 1 FROM information_schema.tables
        WHERE  table_name = 'transaction_offset_group_meta'
    ) THEN

        -- Temp table: one row per transaction-in-offset, tracking component root
        CREATE TEMP TABLE _tx_components (
            tx_id          BIGINT PRIMARY KEY,
            user_id        BIGINT NOT NULL,
            component_root BIGINT NOT NULL
        ) ON COMMIT DROP;

        INSERT INTO _tx_components (tx_id, user_id, component_root)
        SELECT DISTINCT t.id, t.user_id, t.id
        FROM   transactions t
        WHERE  t.id IN (
            SELECT transaction_a_id FROM transaction_offsets
            UNION
            SELECT transaction_b_id FROM transaction_offsets
        );

        -- Iterative union-find: propagate MIN(component_root) across edges
        LOOP
            UPDATE _tx_components tc
            SET    component_root = sub.new_root
            FROM (
                SELECT tc2.tx_id,
                       MIN(LEAST(tc2.component_root, tc3.component_root)) AS new_root
                FROM   _tx_components tc2
                JOIN   transaction_offsets o
                       ON  o.transaction_a_id = tc2.tx_id
                       OR  o.transaction_b_id = tc2.tx_id
                JOIN   _tx_components tc3
                       ON  tc3.tx_id = CASE
                               WHEN o.transaction_a_id = tc2.tx_id
                               THEN o.transaction_b_id
                               ELSE o.transaction_a_id
                           END
                GROUP  BY tc2.tx_id
            ) sub
            WHERE  tc.tx_id          = sub.tx_id
              AND  tc.component_root > sub.new_root;

            EXIT WHEN NOT FOUND;
        END LOOP;

        -- For each component (≥ 2 members) create a transaction_group
        FOR v_rec IN (
            SELECT   component_root,
                     user_id,
                     ARRAY_AGG(tx_id) AS member_ids
            FROM     _tx_components
            GROUP BY component_root, user_id
            HAVING   COUNT(*) >= 2
            ORDER BY component_root
        ) LOOP

            -- Carry over name/comment if the old meta row exists (NULL otherwise)
            SELECT m.name, m.comment
            INTO   v_name, v_comment
            FROM   transaction_offset_group_meta m
            WHERE  m.user_id   = v_rec.user_id
              AND  m.group_key = v_rec.component_root;

            INSERT INTO transaction_groups (user_id, name, comment)
            VALUES (v_rec.user_id, v_name, v_comment)
            RETURNING id INTO v_group_id;

            FOREACH v_tx_id IN ARRAY v_rec.member_ids LOOP
                INSERT INTO transaction_group_members (group_id, transaction_id)
                VALUES (v_group_id, v_tx_id)
                ON CONFLICT DO NOTHING;
            END LOOP;

            UPDATE transaction_offsets
            SET    group_id = v_group_id
            WHERE  transaction_a_id = ANY(v_rec.member_ids)
              AND  transaction_b_id = ANY(v_rec.member_ids);

        END LOOP;

    END IF;

END;
$$;

-- 5. Drop denormalised membership column (superseded by transaction_group_members)
ALTER TABLE transactions DROP COLUMN IF EXISTS group_id;

-- 6. Drop V9 meta table (superseded by transaction_groups + transaction_group_members)
DROP TABLE IF EXISTS transaction_offset_group_meta;
