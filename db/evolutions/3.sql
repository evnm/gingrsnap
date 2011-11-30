# --- !Ups

CREATE TABLE Ingredient (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    name varchar(255) NOT NULL,
    createdAt date NOT NULL,
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE Ingredient;
