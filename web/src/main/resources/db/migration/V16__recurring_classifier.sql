CREATE TABLE recurring_classifier_token_counts
(
    id      BIGSERIAL    NOT NULL PRIMARY KEY,
    user_id BIGINT       NOT NULL REFERENCES users (id),
    type    VARCHAR(50)  NOT NULL,
    token   VARCHAR(100) NOT NULL,
    count   INT          NOT NULL DEFAULT 0,
    UNIQUE (user_id, type, token)
);

CREATE TABLE recurring_classifier_class_counts
(
    id      BIGSERIAL   NOT NULL PRIMARY KEY,
    user_id BIGINT      NOT NULL REFERENCES users (id),
    type    VARCHAR(50) NOT NULL,
    count   INT         NOT NULL DEFAULT 0,
    UNIQUE (user_id, type)
);
