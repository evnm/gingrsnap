# --- !Ups

CREATE TABLE RecipeImage (
    recipeId bigInt NOT NULL,
    imageId bigInt NOT NULL,
    FOREIGN KEY (recipeId) REFERENCES Recipe(id) ON DELETE CASCADE,
    FOREIGN KEY (imageId) REFERENCES Image(id) ON DELETE CASCADE
);

CREATE INDEX IDX_RECIPE_IMAGE ON RecipeImage (recipeId);

# --- !Downs

DROP TABLE RecipeImage CASCADE;
