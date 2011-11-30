# --- !Ups

CREATE TABLE User (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    emailAddr varchar(255) NOT NULL,
    password varchar(255) NOT NULL,
    salt varchar(255) NOT NULL,
    fullname varchar(255) NOT NULL,
    createdAt date NOT NULL,
    twAccessToken varchar(255),
    twAccessTokenSecret varchar(255),
    PRIMARY KEY (id)
);

# --- !Downs

DROP TABLE User;
