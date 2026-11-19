ALTER TABLE instagram_accounts ADD COLUMN username VARCHAR(255);
ALTER TABLE instagram_accounts ADD COLUMN profile_picture_url TEXT;
ALTER TABLE instagram_accounts ADD COLUMN access_token_encrypted TEXT;
ALTER TABLE instagram_accounts ADD COLUMN token_expires_at TIMESTAMPTZ;
ALTER TABLE instagram_accounts ADD COLUMN scopes TEXT;
ALTER TABLE instagram_accounts ADD COLUMN connection_status VARCHAR(50) NOT NULL DEFAULT 'DISCONNECTED';

CREATE TABLE instagram_oauth_states (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    state_hash VARCHAR(64) NOT NULL UNIQUE,
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_instagram_oauth_states_hash ON instagram_oauth_states(state_hash);
CREATE INDEX idx_instagram_oauth_states_expiry ON instagram_oauth_states(expires_at);
CREATE INDEX idx_instagram_accounts_user_active ON instagram_accounts(user_id, active);
