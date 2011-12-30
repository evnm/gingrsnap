# --- !Ups

CREATE TABLE Account (
    id bigserial NOT NULL,
    userId bigint NOT NULL,
    location varchar(255),
    url varchar(255),
    PRIMARY KEY (id),
    FOREIGN KEY (userId) REFERENCES GingrsnapUser(id) ON DELETE CASCADE
);

# --- !Downs

DROP TABLE Account CASCADE;
