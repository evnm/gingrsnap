# --- !Ups

CREATE TABLE Tip (
    id bigserial NOT NULL,
    userId bigint NOT NULL,
    recipeId bigint NOT NULL,
    createdAt timestamp with time zone NOT NULL,
    body text NOT NULL,
    FOREIGN KEY (userId) REFERENCES GingrsnapUser(id) ON DELETE CASCADE,
    FOREIGN KEY (recipeId) REFERENCES Recipe(id) ON DELETE CASCADE,
    PRIMARY KEY (id)
);
INSERT INTO Feature (id, state) VALUES ('recipe-tips', false);

# --- !Downs

DROP TABLE Tip CASCADE;
