-- V11: AI Lead Prospector Agent and Discovered Prospects Queue
CREATE TABLE IF NOT EXISTS discovered_prospects (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    flow_tag VARCHAR(100) NOT NULL,
    instagram_username VARCHAR(100) NOT NULL,
    contact_name VARCHAR(255),
    avatar_url TEXT,
    profile_bio TEXT,
    niche_signals TEXT,
    estimated_followers VARCHAR(50),
    proposed_intro_text TEXT NOT NULL,
    match_score INT DEFAULT 85,
    status VARCHAR(50) NOT NULL DEFAULT 'DISCOVERED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_prospects_flow_tag ON discovered_prospects(LOWER(flow_tag));
CREATE INDEX IF NOT EXISTS idx_prospects_status ON discovered_prospects(status);
CREATE INDEX IF NOT EXISTS idx_prospects_created ON discovered_prospects(created_at DESC);
