# --- !Ups

CREATE TABLE Recipe (
    id bigserial NOT NULL,
    title varchar(255) NOT NULL,
    slug varChar(255) NOT NULL,
    authorId bigint NOT NULL,
    createdAt timestamp NOT NULL,
    modifiedAt timestamp NOT NULL,
    publishedAt timestamp,
    body text NOT NULL,
    parentRecipe bigint,
    FOREIGN KEY (authorId) REFERENCES GingrsnapUser(id) ON DELETE CASCADE,
    FOREIGN KEY (parentRecipe) REFERENCES Recipe(id),
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE Recipe CASCADE;
