# --- !Ups

CREATE TABLE GingrsnapUser (
    id bigserial NOT NULL,
    emailAddr varchar(255) NOT NULL,
    password varchar(255) NOT NULL,
    salt varchar(255) NOT NULL,
    fullname varchar(255) NOT NULL,
    createdAt timestamp NOT NULL,
    twAccessToken varchar(255),
    twAccessTokenSecret varchar(255),
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE GingrsnapUser CASCADE;
