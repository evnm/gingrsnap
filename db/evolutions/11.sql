# Add slug column to Gingrsnapuser table.

# --- !Ups
ALTER TABLE GingrsnapUser ADD slug varchar(255);
UPDATE GingrsnapUser SET slug = id;
ALTER TABLE GingrsnapUser ALTER COLUMN slug SET NOT NULL;

# --- !Downs
ALTER TABLE GingrsnapUser DROP slug;
