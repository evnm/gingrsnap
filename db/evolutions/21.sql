# Follow and recipe indices. Drop Event.createdAt index, as it's not doing anything.

# --- !Ups

CREATE INDEX follow_objectid_idx ON follow (objectid);
CREATE INDEX recipe_authorid_idx ON recipe (authorid);
DROP INDEX event_createdat_desc_index;
CREATE INDEX recipeimage_imageid_idx ON recipeimage (imageid);
CREATE INDEX gingrsnapuserimage_imageid_idx ON gingrsnapuserimage (imageid);
CREATE INDEX tip_recipeid_idx ON tip (recipeid);
CREATE INDEX account_userid_idx ON account (userid);
CREATE INDEX make_recipeid_idx ON make (recipeid);
CREATE INDEX make_userid_idx ON make (userid);
CREATE INDEX ingredient_recipeid_idx ON ingredient (recipeid);
