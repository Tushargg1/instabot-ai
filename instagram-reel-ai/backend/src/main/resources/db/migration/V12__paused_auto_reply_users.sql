-- V12: Paused Auto-Reply Users (Pause automatic replies per user)
CREATE TABLE IF NOT EXISTS paused_auto_reply_users (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_identifier VARCHAR(150) NOT NULL UNIQUE,
    username VARCHAR(100),
    sender_id VARCHAR(100),
    paused_by VARCHAR(100) DEFAULT 'admin',
    reason TEXT,
    paused_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_paused_user_identifier ON paused_auto_reply_users(LOWER(user_identifier));
CREATE INDEX IF NOT EXISTS idx_paused_user_username ON paused_auto_reply_users(LOWER(username));
CREATE INDEX IF NOT EXISTS idx_paused_user_sender_id ON paused_auto_reply_users(sender_id);

ALTER TABLE marketing_leads ADD COLUMN IF NOT EXISTS auto_reply_paused BOOLEAN NOT NULL DEFAULT FALSE;
