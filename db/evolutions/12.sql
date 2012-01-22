# Set time fields to timestamp with time zone

# --- !Ups
ALTER TABLE gingrsnapuser
  ALTER COLUMN createdat TYPE timestamp with time zone;

ALTER TABLE Recipe
  ALTER COLUMN createdAt TYPE timestamp with time zone,
  ALTER COLUMN modifiedAt TYPE timestamp with time zone,
  ALTER COLUMN publishedAt TYPE timestamp with time zone;

ALTER TABLE Ingredient
  ALTER COLUMN createdAt TYPE timestamp with time zone;

ALTER TABLE Event
  ALTER COLUMN createdAt TYPE timestamp with time zone;

ALTER TABLE Make
  ALTER COLUMN createdAt TYPE timestamp with time zone;
