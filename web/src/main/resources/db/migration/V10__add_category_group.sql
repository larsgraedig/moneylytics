ALTER TABLE transactions ADD COLUMN category_group VARCHAR(255);
ALTER TABLE categories ADD COLUMN category_group VARCHAR(255);
ALTER TABLE thresholds ADD COLUMN category_group VARCHAR(255);

ALTER TABLE categories DROP CONSTRAINT IF EXISTS categories_name_subcategory_user_id_key;
CREATE UNIQUE INDEX IF NOT EXISTS categories_unique_idx
    ON categories (name, COALESCE(category_group, ''), subcategory, user_id);
