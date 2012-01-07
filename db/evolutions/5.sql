# --- !Ups

CREATE TABLE Image (
    id bigserial NOT NULL,
    s3Key varchar(255) NOT NULL,
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE Image CASCADE;
