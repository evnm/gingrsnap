# --- !Ups

CREATE TABLE Follow (
    id bigserial NOT NULL,
    followType int NOT NULL,
    subjectId bigInt NOT NULL,
    objectId bigInt NOT NULL,
    createdAt timestamp NOT NULL
);
INSERT INTO Feature (id, state) VALUES ('user-following', false);

# --- !Downs

DROP TABLE Follow CASCADE;
