-- V7: Extracted Knowledge Items with categorization, provenance (who asked, when, from where)
CREATE TABLE IF NOT EXISTS extracted_knowledge_items (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id UUID NOT NULL REFERENCES reels(id) ON DELETE CASCADE,
    title VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    sub_category VARCHAR(100),
    summary TEXT,
    key_data TEXT,
    url TEXT,
    tags JSONB,
    requester_id VARCHAR(100),
    requester_username VARCHAR(100),
    requester_name VARCHAR(255),
    requester_avatar_url TEXT,
    source_reel_url TEXT,
    source_reel_title VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_eki_category ON extracted_knowledge_items(category);
CREATE INDEX IF NOT EXISTS idx_eki_sub_category ON extracted_knowledge_items(sub_category);
CREATE INDEX IF NOT EXISTS idx_eki_reel_id ON extracted_knowledge_items(reel_id);
CREATE INDEX IF NOT EXISTS idx_eki_requester_id ON extracted_knowledge_items(requester_id);
CREATE INDEX IF NOT EXISTS idx_eki_created_at ON extracted_knowledge_items(created_at DESC);
