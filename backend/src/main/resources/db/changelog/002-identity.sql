--liquibase formatted sql
--changeset sidequest:002-users
CREATE TABLE users (
    id UUID PRIMARY KEY,
    email VARCHAR(254) NOT NULL UNIQUE,
    username VARCHAR(30) NOT NULL UNIQUE,
    password_hash VARCHAR(512) NOT NULL,
    status VARCHAR(16) NOT NULL DEFAULT 'ACTIVE',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT users_email_canonical CHECK (email = lower(btrim(email)) AND length(email) > 0),
    CONSTRAINT users_username_canonical CHECK (username ~ '^[a-z0-9_]{3,30}$'),
    CONSTRAINT users_password_encoded CHECK (password_hash LIKE '{argon2}$argon2id$%'),
    CONSTRAINT users_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT users_timestamps CHECK (updated_at >= created_at)
);
--rollback DROP TABLE users;

--changeset sidequest:003-jdbc-sessions
-- Spring Session JDBC 3.4 PostgreSQL schema. Liquibase owns initialization.
CREATE TABLE spring_session (
    primary_id CHAR(36) NOT NULL PRIMARY KEY,
    session_id CHAR(36) NOT NULL,
    creation_time BIGINT NOT NULL,
    last_access_time BIGINT NOT NULL,
    max_inactive_interval INT NOT NULL,
    expiry_time BIGINT NOT NULL,
    principal_name VARCHAR(100)
);
CREATE UNIQUE INDEX spring_session_ix1 ON spring_session (session_id);
CREATE INDEX spring_session_ix2 ON spring_session (expiry_time);
CREATE INDEX spring_session_ix3 ON spring_session (principal_name);
CREATE TABLE spring_session_attributes (
    session_primary_id CHAR(36) NOT NULL,
    attribute_name VARCHAR(200) NOT NULL,
    attribute_bytes BYTEA NOT NULL,
    PRIMARY KEY (session_primary_id, attribute_name),
    FOREIGN KEY (session_primary_id) REFERENCES spring_session(primary_id) ON DELETE CASCADE
);
--rollback DROP TABLE spring_session_attributes;
--rollback DROP TABLE spring_session;
