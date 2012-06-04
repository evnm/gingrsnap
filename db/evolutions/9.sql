# --- !Ups

CREATE TABLE Make (
    id bigserial NOT NULL,
    userId bigInt NOT NULL,
    recipeId bigInt NOT NULL,
    createdAt timestamp NOT NULL,
    FOREIGN KEY (userId) REFERENCES GingrsnapUser(id) ON DELETE CASCADE,
    FOREIGN KEY (recipeId) REFERENCES Recipe(id) ON DELETE CASCADE,
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE Make CASCADE;
