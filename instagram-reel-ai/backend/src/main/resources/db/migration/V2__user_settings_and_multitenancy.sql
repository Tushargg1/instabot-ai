-- User settings table for API keys and credentials (encrypted)
CREATE TABLE user_settings (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    setting_key VARCHAR(100) NOT NULL,
    setting_value TEXT NOT NULL,
    encrypted BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    UNIQUE(user_id, setting_key)
);

-- Add user_id to reels for per-user data isolation
ALTER TABLE reels ADD COLUMN user_id UUID REFERENCES app_users(id);

-- Add user_id to processing_jobs
ALTER TABLE processing_jobs ADD COLUMN user_id UUID REFERENCES app_users(id);

-- Add user_id to instagram_webhook_events
ALTER TABLE instagram_webhook_events ADD COLUMN user_id UUID REFERENCES app_users(id);

-- Add user_id to instagram_accounts
ALTER TABLE instagram_accounts ADD COLUMN user_id UUID REFERENCES app_users(id);

-- Add email and display_name to app_users
ALTER TABLE app_users ADD COLUMN email VARCHAR(255);
ALTER TABLE app_users ADD COLUMN display_name VARCHAR(255);

-- Indexes
CREATE INDEX idx_user_settings_user ON user_settings(user_id);
CREATE INDEX idx_reels_user ON reels(user_id);
CREATE INDEX idx_jobs_user ON processing_jobs(user_id);
CREATE INDEX idx_accounts_user ON instagram_accounts(user_id);
