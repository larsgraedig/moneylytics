-- Rename columns in category table to reflect new hierarchy:
-- Old: subcategory (level 3 leaf, NOT NULL), category_group (level 2 middle, nullable)
-- New: group_name  (level 3 leaf, NOT NULL), subcategory   (level 2 middle, nullable)
ALTER TABLE category RENAME COLUMN subcategory TO group_name;
ALTER TABLE category RENAME COLUMN category_group TO subcategory;

-- Rename columns in threshold table the same way
ALTER TABLE threshold RENAME COLUMN subcategory TO group_name;
ALTER TABLE threshold RENAME COLUMN category_group TO subcategory;

-- Recreate the unique index with updated column names
DROP INDEX IF EXISTS categories_unique_idx;
CREATE UNIQUE INDEX categories_unique_idx
    ON category (name, COALESCE(subcategory, ''), group_name, organization_id);

-- Clear existing category records; they will be rebuilt from transaction data below
DELETE FROM category;

-- Rebuild category records from distinct (category, category_group, subcategory) combos in transaction.
-- After the renames above: new category.subcategory ← old transaction.category_group (level 2 middle)
--                          new category.group_name  ← old transaction.subcategory    (level 3 leaf)
INSERT INTO category (name, subcategory, group_name, organization_id)
SELECT DISTINCT
    t.category,
    t.category_group,
    t.subcategory,
    t.organization_id
FROM "transaction" t
WHERE t.category IS NOT NULL
  AND t.subcategory IS NOT NULL;

-- Add category_id FK column to transaction (nullable: uncategorized transactions stay null)
ALTER TABLE "transaction" ADD COLUMN category_id BIGINT REFERENCES category(id);

-- Link each transaction to its new category record
UPDATE "transaction" t
SET category_id = c.id
FROM category c
WHERE t.organization_id = c.organization_id
  AND t.category = c.name
  AND COALESCE(t.category_group, '') = COALESCE(c.subcategory, '')
  AND t.subcategory = c.group_name;

-- Drop the now-redundant string columns from transaction
ALTER TABLE "transaction" DROP COLUMN category;
ALTER TABLE "transaction" DROP COLUMN subcategory;
ALTER TABLE "transaction" DROP COLUMN category_group;
