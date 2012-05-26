# --- !Ups

CREATE TABLE Event (
    id bigserial NOT NULL,
    eventType int NOT NULL,
    subjectId bigInt NOT NULL,
    objectId bigInt NOT NULL,
    createdAt timestamp NOT NULL,
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE Event CASCADE;
