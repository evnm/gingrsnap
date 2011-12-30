# --- !Ups

CREATE TABLE Ingredient (
    id bigserial NOT NULL,
    name varchar(255) NOT NULL,
    recipeId bigInt NOT NULL,
    createdAt timestamp NOT NULL,
    FOREIGN KEY (recipeId) REFERENCES Recipe(id) ON DELETE CASCADE,
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE Ingredient CASCADE;
