CREATE TABLE tier (
    id          BIGINT       GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name        VARCHAR(100) NOT NULL UNIQUE,
    description VARCHAR(500),
    active      BOOLEAN      NOT NULL DEFAULT TRUE,
    is_default  BOOLEAN      NOT NULL DEFAULT FALSE
);

CREATE UNIQUE INDEX uq_tier_is_default ON tier (is_default) WHERE is_default = TRUE;

INSERT INTO tier (name, description, active, is_default) VALUES ('Standard', 'Standard Tier', TRUE, TRUE);
INSERT INTO tier (name, description, active, is_default) VALUES ('Pro', 'Pro Tier', TRUE, FALSE);

ALTER TABLE "user" ADD COLUMN tier_id BIGINT REFERENCES tier (id);
UPDATE "user" SET tier_id = (SELECT id FROM tier WHERE is_default = TRUE);
ALTER TABLE "user" ALTER COLUMN tier_id SET NOT NULL;
