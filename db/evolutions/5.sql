# --- !Ups

CREATE TABLE RecipeIngredient (
    recipeId bigint(20) NOT NULL,
    ingredientId bigint(20) NOT NULL,
    FOREIGN KEY (recipeId) REFERENCES Recipe(id) ON DELETE CASCADE,
    FOREIGN KEY (ingredientId) REFERENCES Ingredient(id) ON DELETE CASCADE
);

CREATE INDEX IDX_RECIPE_INGREDIENT ON RecipeIngredient (recipeId);

# --- !Downs

DROP TABLE RecipeIngredient;
