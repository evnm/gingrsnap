# --- !Ups

CREATE TABLE RecipeList (
    id bigserial NOT NULL,
    creatorId bigInt NOT NULL references GingrsnapUser(id),
    title varchar(255) NOT NULL,
    slug varchar(255) NOT NULL,
    description varchar(255),
    createdAt timestamp with time zone NOT NULL,
    modifiedAt timestamp with time zone NOT NULL,
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE RecipeList CASCADE;
