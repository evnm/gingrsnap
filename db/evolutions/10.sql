# --- !Ups

CREATE TABLE Event (
    id bigserial NOT NULL,
    eventType int NOT NULL,
    subjectId bigInt NOT NULL,
    objectId bigInt NOT NULL,
    createdAt timestamp NOT NULL
);

# --- !Downs

DROP TABLE Event CASCADE;
