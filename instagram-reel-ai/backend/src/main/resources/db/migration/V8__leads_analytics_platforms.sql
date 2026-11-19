-- V8: Lead Capture, Follower Conversion Tracking, and Multi-platform Support
CREATE TABLE IF NOT EXISTS captured_leads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email VARCHAR(255) NOT NULL,
    instagram_sender_id VARCHAR(100) NOT NULL,
    instagram_username VARCHAR(100),
    full_name VARCHAR(255),
    avatar_url TEXT,
    source_reel_id UUID REFERENCES reels(id) ON DELETE SET NULL,
    source_reel_title VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uk_leads_email_sender UNIQUE (email, instagram_sender_id)
);

CREATE INDEX IF NOT EXISTS idx_leads_email ON captured_leads(email);
CREATE INDEX IF NOT EXISTS idx_leads_sender ON captured_leads(instagram_sender_id);
CREATE INDEX IF NOT EXISTS idx_leads_created ON captured_leads(created_at DESC);

-- Track conversion from non-follower to follower
ALTER TABLE reel_requests ADD COLUMN IF NOT EXISTS was_following_initially BOOLEAN DEFAULT TRUE;
ALTER TABLE reel_requests ADD COLUMN IF NOT EXISTS converted_to_follower BOOLEAN DEFAULT FALSE;

-- Multi-platform support: tag platform on reel (INSTAGRAM, YOUTUBE, TIKTOK)
ALTER TABLE reels ADD COLUMN IF NOT EXISTS platform VARCHAR(50) DEFAULT 'INSTAGRAM';
CREATE INDEX IF NOT EXISTS idx_reels_platform ON reels(platform);
