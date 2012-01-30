# --- !Ups

CREATE TABLE Feature (
    id varchar(64) NOT NULL,
    state boolean NOT NULL,
    PRIMARY KEY (id)
);
INSERT INTO Feature (id, state) VALUES ('forking', true);


# --- !Downs

DROP TABLE Feature CASCADE;
