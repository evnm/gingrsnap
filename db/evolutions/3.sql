# --- !Ups

CREATE TABLE Ingredient (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    name varchar(255) NOT NULL,
    recipeId bigInt(20) NOT NULL,
    createdAt date NOT NULL,
    FOREIGN KEY (recipeId) REFERENCES Recipe(id) ON DELETE CASCADE,
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE Ingredient;
