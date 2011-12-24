# --- !Ups

CREATE TABLE Account (
    id bigint(20) NOT NULL AUTO_INCREMENT,
    userId bigint(20) NOT NULL,
    location varchar(255),
    url varchar(255),
    PRIMARY KEY (id),
    FOREIGN KEY (userId) REFERENCES User(id) ON DELETE CASCADE
);

# --- !Downs

DROP TABLE Account;
