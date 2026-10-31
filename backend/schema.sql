-- ---------------------------------------------------------------------------
-- RUK AI — PostgreSQL schema
--
-- The application creates these tables automatically on first boot
-- (SQLAlchemy metadata.create_all), which is what the SQLite development
-- default and the test-suite use. This file is the canonical DDL for a
-- managed PostgreSQL deployment where you want to review or version the
-- schema before applying it.
--
--   psql "$DATABASE_URL" -f schema.sql
-- ---------------------------------------------------------------------------

CREATE TABLE IF NOT EXISTS users (
    id              VARCHAR(32)  PRIMARY KEY,
    email           VARCHAR(320) NOT NULL UNIQUE,
    display_name    VARCHAR(120) NOT NULL DEFAULT '',
    password_hash   VARCHAR(255),
    google_sub      VARCHAR(64)  UNIQUE,
    plan            VARCHAR(16)  NOT NULL DEFAULT 'free',
    is_active       BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_login_at   TIMESTAMPTZ
);
CREATE INDEX IF NOT EXISTS ix_users_email ON users (email);

CREATE TABLE IF NOT EXISTS api_keys (
    id              VARCHAR(32)  PRIMARY KEY,
    user_id         VARCHAR(32)  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    provider        VARCHAR(32)  NOT NULL,
    encrypted_key   TEXT         NOT NULL,
    key_hint        VARCHAR(32)  NOT NULL DEFAULT '',
    base_url        VARCHAR(255),
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    last_used_at    TIMESTAMPTZ,
    CONSTRAINT uq_user_provider UNIQUE (user_id, provider)
);
CREATE INDEX IF NOT EXISTS ix_api_keys_user_id ON api_keys (user_id);

CREATE TABLE IF NOT EXISTS conversations (
    id              VARCHAR(32)  PRIMARY KEY,
    user_id         VARCHAR(32)  NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    title           VARCHAR(200) NOT NULL DEFAULT 'New chat',
    model           VARCHAR(120) NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_conversations_user_id ON conversations (user_id);
CREATE INDEX IF NOT EXISTS ix_conv_user_updated ON conversations (user_id, updated_at);

CREATE TABLE IF NOT EXISTS messages (
    id              VARCHAR(32)  PRIMARY KEY,
    conversation_id VARCHAR(32)  NOT NULL REFERENCES conversations (id) ON DELETE CASCADE,
    role            VARCHAR(16)  NOT NULL,
    content         TEXT         NOT NULL DEFAULT '',
    model           VARCHAR(120) NOT NULL DEFAULT '',
    tokens_in       INTEGER      NOT NULL DEFAULT 0,
    tokens_out      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_messages_conversation_id ON messages (conversation_id);

CREATE TABLE IF NOT EXISTS usage_events (
    id              VARCHAR(32)  PRIMARY KEY,
    user_id         VARCHAR(32)  NOT NULL,
    kind            VARCHAR(32)  NOT NULL,
    provider        VARCHAR(32)  NOT NULL DEFAULT '',
    model           VARCHAR(120) NOT NULL DEFAULT '',
    units           INTEGER      NOT NULL DEFAULT 1,
    cost_micros     INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_usage_events_user_id ON usage_events (user_id);
CREATE INDEX IF NOT EXISTS ix_usage_events_created_at ON usage_events (created_at);

CREATE TABLE IF NOT EXISTS generations (
    id              VARCHAR(32)  PRIMARY KEY,
    user_id         VARCHAR(32)  NOT NULL,
    kind            VARCHAR(16)  NOT NULL,
    prompt          TEXT         NOT NULL,
    provider        VARCHAR(32)  NOT NULL DEFAULT '',
    model           VARCHAR(120) NOT NULL DEFAULT '',
    status          VARCHAR(24)  NOT NULL DEFAULT 'pending',
    aspect_ratio    VARCHAR(12)  NOT NULL DEFAULT '1:1',
    quality         VARCHAR(16)  NOT NULL DEFAULT 'standard',
    asset_url       TEXT,
    provider_job_id VARCHAR(200),
    error           TEXT,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_generations_user_id ON generations (user_id);
CREATE INDEX IF NOT EXISTS ix_gen_user_created ON generations (user_id, created_at);

CREATE TABLE IF NOT EXISTS documents (
    id              VARCHAR(32)  PRIMARY KEY,
    user_id         VARCHAR(32)  NOT NULL,
    filename        VARCHAR(255) NOT NULL,
    size_bytes      INTEGER      NOT NULL DEFAULT 0,
    page_count      INTEGER      NOT NULL DEFAULT 0,
    text            TEXT         NOT NULL DEFAULT '',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_documents_user_id ON documents (user_id);

CREATE TABLE IF NOT EXISTS report_links (
    id              VARCHAR(32)  PRIMARY KEY,
    user_id         VARCHAR(32)  NOT NULL,
    title           VARCHAR(200) NOT NULL DEFAULT '',
    content         TEXT         NOT NULL DEFAULT '',
    content_type    VARCHAR(64)  NOT NULL DEFAULT 'text/markdown',
    password_hash   VARCHAR(255),
    expires_at      TIMESTAMPTZ,
    revoked         BOOLEAN      NOT NULL DEFAULT FALSE,
    view_count      INTEGER      NOT NULL DEFAULT 0,
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_report_links_user_id ON report_links (user_id);

CREATE TABLE IF NOT EXISTS link_access_log (
    id              VARCHAR(32)  PRIMARY KEY,
    link_id         VARCHAR(32)  NOT NULL REFERENCES report_links (id) ON DELETE CASCADE,
    truncated_ip    VARCHAR(64)  NOT NULL DEFAULT '',
    user_agent      VARCHAR(255) NOT NULL DEFAULT '',
    outcome         VARCHAR(24)  NOT NULL DEFAULT 'ok',
    created_at      TIMESTAMPTZ  NOT NULL DEFAULT NOW()
);
CREATE INDEX IF NOT EXISTS ix_link_access_log_link_id ON link_access_log (link_id);
