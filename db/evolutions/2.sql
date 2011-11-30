# --- !Ups

CREATE TABLE Recipe (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    title varchar(255) NOT NULL,
    authorId bigint(20) NOT NULL,
    createdAt date NOT NULL,
    body text NOT NULL,
    FOREIGN KEY (authorId) REFERENCES User(id) ON DELETE CASCADE,
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE Recipe;
