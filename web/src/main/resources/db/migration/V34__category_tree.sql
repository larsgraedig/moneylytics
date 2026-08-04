-- Migrates 3-level flat category model (name, subcategory, group_name) to a
-- self-referential adjacency list tree. Existing row IDs are preserved so that
-- transaction.category_id foreign keys remain valid.

-- 1. Drop old unique index (columns will change)
DROP INDEX IF EXISTS categories_unique_idx;

-- 2. Make subcategory nullable so we can INSERT root-only rows
ALTER TABLE category ALTER COLUMN subcategory DROP NOT NULL;

-- 3. Add parent_id without FK yet (allows forward-reference-free inserts within this script)
ALTER TABLE category ADD COLUMN parent_id BIGINT;

-- 4. Insert one root node per distinct (name, organization_id).
--    Store root_id → root_name mapping for subsequent steps.
CREATE TEMP TABLE _roots AS
WITH ins AS (
    INSERT INTO category (name, parent_id, organization_id)
    SELECT DISTINCT c.name, NULL::BIGINT, c.organization_id
    FROM category c
    RETURNING id, name, organization_id
)
SELECT id AS root_id, name AS root_name, organization_id FROM ins;

-- 5. Promote rows with group_name IS NULL to level-2 nodes:
--    name ← subcategory, parent_id ← root_id
UPDATE category c
SET
    name      = c.subcategory,
    parent_id = r.root_id
FROM _roots r
WHERE c.name            = r.root_name
  AND c.organization_id = r.organization_id
  AND c.group_name IS NULL
  AND c.parent_id IS NULL
  AND c.subcategory IS NOT NULL;

-- 6. Build a (root_name, sub_name, org) → level2_id lookup table.

CREATE TEMP TABLE _level2 (
    organization_id BIGINT NOT NULL,
    root_name       TEXT   NOT NULL,
    sub_name        TEXT   NOT NULL,
    level2_id       BIGINT NOT NULL
);

-- 6a. Level-2 nodes that now exist (updated in step 5).
INSERT INTO _level2 (organization_id, root_name, sub_name, level2_id)
SELECT c.organization_id, r.root_name, c.name, c.id
FROM category c
JOIN _roots r ON r.root_id = c.parent_id AND r.organization_id = c.organization_id
WHERE c.group_name IS NULL AND c.parent_id IS NOT NULL;

-- 6b. Some subcategories only appear under rows with group_name ≠ NULL
--     and therefore have no level-2 node yet — insert them now.
WITH missing AS (
    SELECT DISTINCT c.name AS root_name, c.subcategory AS sub_name, c.organization_id
    FROM category c
    WHERE c.group_name IS NOT NULL
      AND c.parent_id  IS NULL
      AND NOT EXISTS (
          SELECT 1 FROM _level2 l
          WHERE l.root_name       = c.name
            AND l.sub_name        = c.subcategory
            AND l.organization_id = c.organization_id
      )
),
ins AS (
    INSERT INTO category (name, parent_id, organization_id)
    SELECT m.sub_name, r.root_id, m.organization_id
    FROM missing m
    JOIN _roots r ON r.root_name = m.root_name AND r.organization_id = m.organization_id
    RETURNING id, name, organization_id, parent_id
)
INSERT INTO _level2 (organization_id, root_name, sub_name, level2_id)
SELECT ins.organization_id, r.root_name, ins.name, ins.id
FROM ins
JOIN _roots r ON r.root_id = ins.parent_id;

-- 7. Promote rows with group_name IS NOT NULL to level-3 nodes:
--    name ← group_name, parent_id ← matching level-2 node
UPDATE category c
SET
    name      = c.group_name,
    parent_id = l.level2_id
FROM _level2 l
WHERE c.group_name IS NOT NULL
  AND c.parent_id  IS NULL
  AND c.name            = l.root_name
  AND c.subcategory     = l.sub_name
  AND c.organization_id = l.organization_id;

-- 8. Drop columns that are now redundant
ALTER TABLE category DROP COLUMN subcategory;
ALTER TABLE category DROP COLUMN group_name;

-- 9. Add FK constraint now that all parent_id values reference valid rows
ALTER TABLE category
    ADD CONSTRAINT fk_category_parent
    FOREIGN KEY (parent_id) REFERENCES category(id);

-- 10. New unique index: no two siblings share the same name under the same parent
CREATE UNIQUE INDEX categories_unique_idx
    ON category (organization_id, COALESCE(parent_id, -1), name);

DROP TABLE _roots;
DROP TABLE _level2;
