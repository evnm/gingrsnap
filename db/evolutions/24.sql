# --- !Ups

CREATE TABLE List (
    id bigserial NOT NULL,
    creatorId bigInt NOT NULL references GingrsnapUser(id),
    createdAt timestamp with time zone NOT NULL
);

# --- !Downs

DROP TABLE List CASCADE;
