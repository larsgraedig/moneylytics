ALTER TABLE threshold ADD COLUMN category_id BIGINT REFERENCES category(id);
ALTER TABLE threshold ALTER COLUMN category DROP NOT NULL;
