-- V6: DM auto-reply rules table
CREATE TABLE dm_reply_rules (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    name        VARCHAR(255) NOT NULL,
    match_type  VARCHAR(20)  NOT NULL CHECK (match_type IN ('KEYWORD','REGEX','ALL')),
    match_value TEXT,
    reply_text  TEXT         NOT NULL,
    priority    INT          NOT NULL DEFAULT 100,
    enabled     BOOLEAN      NOT NULL DEFAULT TRUE,
    created_at  TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at  TIMESTAMPTZ  NOT NULL DEFAULT now()
);

CREATE INDEX idx_dm_reply_rules_user_enabled ON dm_reply_rules(user_id, enabled, priority);
