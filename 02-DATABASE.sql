CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE instagram_accounts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instagram_account_id VARCHAR(255) NOT NULL UNIQUE,
    display_name VARCHAR(255),
    access_token_ref VARCHAR(500),
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE instagram_webhook_events (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    external_event_id VARCHAR(500) NOT NULL UNIQUE,
    event_type VARCHAR(100),
    sender_id VARCHAR(255),
    recipient_id VARCHAR(255),
    raw_payload JSONB NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    processed BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE reels (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instagram_account_id UUID REFERENCES instagram_accounts(id),
    webhook_event_id UUID REFERENCES instagram_webhook_events(id),
    source_url TEXT,
    external_media_id VARCHAR(500),
    creator_username VARCHAR(255),
    caption TEXT,
    processing_status VARCHAR(50) NOT NULL,
    media_mime_type VARCHAR(100),
    media_size_bytes BIGINT,
    saved_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE reel_transcripts (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id UUID NOT NULL UNIQUE REFERENCES reels(id) ON DELETE CASCADE,
    transcript TEXT NOT NULL,
    language VARCHAR(20),
    duration_seconds NUMERIC(12,3),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE reel_summaries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id UUID NOT NULL UNIQUE REFERENCES reels(id) ON DELETE CASCADE,
    title VARCHAR(500),
    summary TEXT NOT NULL,
    key_points JSONB NOT NULL DEFAULT '[]',
    action_items JSONB NOT NULL DEFAULT '[]',
    topics JSONB NOT NULL DEFAULT '[]',
    tools_or_products JSONB NOT NULL DEFAULT '[]',
    people_or_companies JSONB NOT NULL DEFAULT '[]',
    claims_to_verify JSONB NOT NULL DEFAULT '[]',
    language VARCHAR(20),
    confidence NUMERIC(5,4),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE processing_jobs (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id UUID NOT NULL REFERENCES reels(id) ON DELETE CASCADE,
    status VARCHAR(50) NOT NULL,
    attempts INT NOT NULL DEFAULT 0,
    last_error_code VARCHAR(100),
    last_error_message TEXT,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_reels_status ON reels(processing_status);
CREATE INDEX idx_reels_created_at ON reels(created_at DESC);
CREATE INDEX idx_jobs_status_created ON processing_jobs(status, created_at);
CREATE INDEX idx_reels_source_url ON reels(source_url);
