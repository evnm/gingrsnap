# --- !Ups

CREATE TABLE PasswordResetRequest (
    id varchar(128) NOT NULL,
    userId bigint NOT NULL,
    createdAt timestamp with time zone NOT NULL,
    resetCompleted boolean NOT NULL,
    FOREIGN KEY (userId) REFERENCES GingrsnapUser(id) ON DELETE CASCADE,
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE PasswordResetRequest CASCADE;
