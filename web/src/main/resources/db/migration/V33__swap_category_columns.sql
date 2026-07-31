-- Swap data between subcategory (was L2 middle, now L3 leaf) and group_name (was L3 leaf, now L2 middle)
ALTER TABLE category ADD COLUMN _swap TEXT;
UPDATE category SET _swap = subcategory;
drop index categories_unique_idx;
UPDATE category SET subcategory = group_name;
alter table category
    alter column name drop not null;

alter table category
    alter column group_name drop not null;

UPDATE category SET group_name = _swap;
ALTER TABLE category DROP COLUMN _swap;

-- subcategory is now required (was group_name which was NOT NULL)
ALTER TABLE category ALTER COLUMN subcategory SET NOT NULL;
ALTER TABLE category ALTER COLUMN name SET NOT NULL;
-- group_name is now optional (was subcategory which was nullable)
ALTER TABLE category ALTER COLUMN group_name DROP NOT NULL;

-- Recreate unique index with correct column roles
DROP INDEX IF EXISTS categories_unique_idx;
CREATE UNIQUE INDEX categories_unique_idx
    ON category (name, subcategory, COALESCE(group_name, ''), organization_id);

-- Also swap in threshold table if it has subcategory/group_name
DO $$
BEGIN
  IF EXISTS (SELECT 1 FROM information_schema.columns WHERE table_name='threshold' AND column_name='subcategory') THEN
    ALTER TABLE threshold ADD COLUMN _swap TEXT;
    UPDATE threshold SET _swap = subcategory;
    UPDATE threshold SET subcategory = group_name;
    UPDATE threshold SET group_name = _swap;
    ALTER TABLE threshold DROP COLUMN _swap;
  END IF;
END $$;
