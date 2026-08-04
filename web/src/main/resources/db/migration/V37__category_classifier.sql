CREATE TABLE category_classifier_class_count (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organization (id),
    category_id     BIGINT NOT NULL REFERENCES category (id) ON DELETE CASCADE,
    count           BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT uq_cat_clf_class UNIQUE (organization_id, category_id)
);

CREATE TABLE category_classifier_token_count (
    id              BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    organization_id BIGINT NOT NULL REFERENCES organization (id),
    category_id     BIGINT NOT NULL REFERENCES category (id) ON DELETE CASCADE,
    token           VARCHAR(255) NOT NULL,
    count           BIGINT NOT NULL DEFAULT 1,
    CONSTRAINT uq_cat_clf_token UNIQUE (organization_id, category_id, token)
);
