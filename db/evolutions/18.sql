# --- !Ups

CREATE INDEX event_createdat_desc_index ON Event (createdat DESC NULLS LAST);
CREATE INDEX event_subjectid_index ON Event (subjectid);
CREATE INDEX follow_subjectid_index ON Follow (subjectid);

# --- !Downs

DROP INDEX event_createdat_desc_index;
DROP INDEX event_subjectid_index;
DROP INDEX follow_subjectid_index;
