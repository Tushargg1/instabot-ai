-- V9: Marketing & Outreach Campaigns with AI Sales Conversion
CREATE TABLE IF NOT EXISTS marketing_leads (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instagram_username VARCHAR(100) NOT NULL,
    instagram_sender_id VARCHAR(100),
    contact_name VARCHAR(255),
    avatar_url TEXT,
    context TEXT,
    tag VARCHAR(100) DEFAULT 'General',
    product_offer TEXT NOT NULL,
    sales_goal VARCHAR(255) DEFAULT 'Book call / Buy product',
    first_message TEXT,
    outreach_sent_at TIMESTAMPTZ,
    last_interaction_at TIMESTAMPTZ,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    conversation_history TEXT DEFAULT '[]',
    notes TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_marketing_leads_username ON marketing_leads(LOWER(instagram_username));
CREATE INDEX IF NOT EXISTS idx_marketing_leads_sender_id ON marketing_leads(instagram_sender_id);
CREATE INDEX IF NOT EXISTS idx_marketing_leads_status ON marketing_leads(status);
CREATE INDEX IF NOT EXISTS idx_marketing_leads_tag ON marketing_leads(tag);
CREATE INDEX IF NOT EXISTS idx_marketing_leads_created ON marketing_leads(created_at DESC);
