# Add missing foreign keys.

# --- !Ups

ALTER TABLE Account ADD CONSTRAINT account_gingrsnapuser_fkey FOREIGN KEY (userid) references GingrsnapUser (id) MATCH FULL;
ALTER TABLE GingrsnapUserImage ADD CONSTRAINT gingrsnapuserimage_gingrsnapuser_fkey FOREIGN KEY (userid) references GingrsnapUser (id) MATCH FULL;
ALTER TABLE GingrsnapUserImage ADD CONSTRAINT gingrsnapuserimage_image_fkey FOREIGN KEY (imageid) references Image (id) MATCH FULL;
ALTER TABLE RecipeImage ADD CONSTRAINT recipeimage_recipe_fkey FOREIGN KEY (recipeid) references Recipe (id) MATCH FULL;
ALTER TABLE RecipeImage ADD CONSTRAINT recipeimage_image_fkey FOREIGN KEY (imageid) references Image (id) MATCH FULL;
ALTER TABLE Ingredient ADD CONSTRAINT ingredient_recipe_fkey FOREIGN KEY (recipeid) references Recipe (id) MATCH FULL;
ALTER TABLE Tip ADD CONSTRAINT tip_gingrsnapuser_fkey FOREIGN KEY (userid) references GingrsnapUser (id) MATCH FULL;
ALTER TABLE Tip ADD CONSTRAINT tip_recipe_fkey FOREIGN KEY (recipeid) references Recipe (id) MATCH FULL;
ALTER TABLE PasswordResetRequest ADD CONSTRAINT passwordresetrequest_gingrsnapuser_fkey FOREIGN KEY (userid) references GingrsnapUser (id) MATCH FULL;
ALTER TABLE Make ADD CONSTRAINT make_gingrsnapuser_fkey FOREIGN KEY (userid) references GingrsnapUser (id) MATCH FULL;
ALTER TABLE Make ADD CONSTRAINT make_recipe_fkey FOREIGN KEY (recipeid) references Recipe (id) MATCH FULL;
