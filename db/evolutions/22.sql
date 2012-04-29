# Make email and password optional, add Twitter username.

# --- !Ups
ALTER TABLE GingrsnapUser ALTER COLUMN emailAddr DROP NOT NULL;
ALTER TABLE GingrsnapUser ALTER COLUMN password DROP NOT NULL;
ALTER TABLE gingrsnapuser ADD COLUMN twUsername varchar(15);
