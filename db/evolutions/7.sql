# --- !Ups

CREATE TABLE GingrsnapUserImage (
    userId bigInt NOT NULL,
    imageId bigInt NOT NULL,
    FOREIGN KEY (userId) REFERENCES GingrsnapUser(id) ON DELETE CASCADE,
    FOREIGN KEY (imageId) REFERENCES Image(id) ON DELETE CASCADE
);

CREATE INDEX IDX_USER_IMAGE ON GingrsnapUserImage (userId);

# --- !Downs

DROP TABLE GingrsnapUserImage CASCADE;
