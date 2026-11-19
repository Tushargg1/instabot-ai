-- V10: Marketing Playbooks, Talk Tracks, and Document Knowledge per Flow Tag
CREATE TABLE IF NOT EXISTS marketing_playbooks (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    title VARCHAR(255) NOT NULL,
    tag VARCHAR(100) NOT NULL,
    content TEXT NOT NULL,
    pdf_filename VARCHAR(255),
    strict_guidelines BOOLEAN DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_playbooks_tag ON marketing_playbooks(LOWER(tag));
CREATE INDEX IF NOT EXISTS idx_playbooks_created ON marketing_playbooks(created_at DESC);
