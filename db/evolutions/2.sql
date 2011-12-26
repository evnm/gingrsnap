# --- !Ups

CREATE TABLE Recipe (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    title varchar(255) NOT NULL,
    slug varChar(255) NOT NULL,
    authorId bigint(20) NOT NULL,
    createdAt date NOT NULL,
    modifiedAt date NOT NULL,
    body text NOT NULL,
    parentRecipe bigint(20),
    FOREIGN KEY (authorId) REFERENCES User(id) ON DELETE CASCADE,
    FOREIGN KEY (parentRecipe) REFERENCES Recipe(id),
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE Recipe;
