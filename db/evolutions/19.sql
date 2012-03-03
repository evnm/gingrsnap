# Create Twitter user id column in GingrsnapUser.

# --- !Ups

ALTER TABLE gingrsnapuser ADD COLUMN twUserId bigint;
