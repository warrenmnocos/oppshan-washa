CREATE TABLE user_account (
    uuid             UUID         NOT NULL,
    first_name       VARCHAR(255),
    last_name        VARCHAR(255),
    created_at       TIMESTAMPTZ  NOT NULL,
    last_modified_at TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_user_account PRIMARY KEY (uuid)
);
CREATE INDEX idx_user_account_created_at ON user_account (created_at);

CREATE TABLE idp_account (
    uuid              UUID         NOT NULL,
    provider_id       VARCHAR(255) NOT NULL,
    provider_name     VARCHAR(255) NOT NULL,
    user_account_uuid UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    last_modified_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_idp_account PRIMARY KEY (uuid),
    CONSTRAINT fk_idp_account_user_account
        FOREIGN KEY (user_account_uuid) REFERENCES user_account (uuid),
    CONSTRAINT uc_idp_account_provider
        UNIQUE (provider_id, provider_name, user_account_uuid)
);
CREATE INDEX idx_idp_account_user_account_uuid ON idp_account (user_account_uuid);

CREATE TABLE google_account (
    uuid      UUID         NOT NULL,
    name      VARCHAR(255),
    email     VARCHAR(255) NOT NULL,
    photo_url VARCHAR(2048),
    CONSTRAINT pk_google_account PRIMARY KEY (uuid),
    CONSTRAINT fk_google_account_idp_account
        FOREIGN KEY (uuid) REFERENCES idp_account (uuid)
);
CREATE INDEX idx_google_account_email ON google_account (email);

-- Allowlist: which emails are permitted, and which person each belongs to.
-- Seeded from Parameter Store on startup (IdentityBootstrap). provider_id (the Google `sub`) is
-- captured on first login when the google_account row is created.
CREATE TABLE allowed_identity (
    email             VARCHAR(255) NOT NULL,
    user_account_uuid UUID         NOT NULL,
    created_at        TIMESTAMPTZ  NOT NULL,
    last_modified_at  TIMESTAMPTZ  NOT NULL,
    CONSTRAINT pk_allowed_identity PRIMARY KEY (email),
    CONSTRAINT fk_allowed_identity_user_account
        FOREIGN KEY (user_account_uuid) REFERENCES user_account (uuid)
);
