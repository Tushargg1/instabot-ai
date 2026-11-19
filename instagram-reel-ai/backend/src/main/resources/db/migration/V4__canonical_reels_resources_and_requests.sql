ALTER TABLE reels ADD COLUMN canonical_key VARCHAR(500);
ALTER TABLE reels ADD COLUMN request_count BIGINT NOT NULL DEFAULT 0;
ALTER TABLE reels ADD COLUMN last_requested_at TIMESTAMPTZ;

-- Historical rows remain distinct; all newly ingested rows use deterministic media/shortcode keys.
UPDATE reels
SET canonical_key = 'legacy:reel:' || id,
    request_count = CASE WHEN webhook_event_id IS NULL THEN 0 ELSE 1 END,
    last_requested_at = COALESCE(updated_at, created_at);
ALTER TABLE reels ALTER COLUMN canonical_key SET NOT NULL;
ALTER TABLE reels ADD CONSTRAINT uq_reels_canonical_key UNIQUE (canonical_key);

ALTER TABLE processing_jobs ADD COLUMN job_type VARCHAR(50) NOT NULL DEFAULT 'AI_PIPELINE';
ALTER TABLE processing_jobs ADD COLUMN priority INT NOT NULL DEFAULT 100;
ALTER TABLE processing_jobs ADD COLUMN assigned_worker VARCHAR(255);

CREATE TABLE reel_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id UUID NOT NULL REFERENCES reels(id) ON DELETE CASCADE,
    webhook_event_id UUID NOT NULL UNIQUE REFERENCES instagram_webhook_events(id) ON DELETE CASCADE,
    instagram_sender_id VARCHAR(255) NOT NULL,
    instagram_recipient_id VARCHAR(255),
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    reply_status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    completed_at TIMESTAMPTZ,
    replied_at TIMESTAMPTZ,
    last_error TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

INSERT INTO reel_requests (reel_id, webhook_event_id, instagram_sender_id, instagram_recipient_id,
                           status, reply_status, completed_at, replied_at)
SELECT r.id, e.id, e.sender_id, e.recipient_id, 'COMPLETED', 'REPLIED', e.received_at, e.received_at
FROM reels r JOIN instagram_webhook_events e ON e.id = r.webhook_event_id
ON CONFLICT (webhook_event_id) DO NOTHING;

CREATE TABLE reel_resources (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_id UUID NOT NULL UNIQUE REFERENCES reels(id) ON DELETE CASCADE,
    resource_url TEXT,
    resource_type VARCHAR(100),
    extracted_data JSONB,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    required_action VARCHAR(100),
    action_payload TEXT,
    request_count BIGINT NOT NULL DEFAULT 0,
    first_found_at TIMESTAMPTZ,
    last_requested_at TIMESTAMPTZ,
    last_verified_at TIMESTAMPTZ,
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Keep one active job per canonical Reel and job type while preserving completed history.
WITH ranked AS (
    SELECT id, ROW_NUMBER() OVER (PARTITION BY reel_id, job_type ORDER BY created_at) AS position
    FROM processing_jobs
    WHERE status IN ('QUEUED','CLAIMED','DOWNLOADING','EXTRACTING_AUDIO','TRANSCRIBING','SUMMARIZING','SAVING','REPLYING')
)
UPDATE processing_jobs p SET status = 'SUPERSEDED', completed_at = NOW()
FROM ranked r WHERE p.id = r.id AND r.position > 1;

CREATE UNIQUE INDEX uq_processing_jobs_active_reel_type
ON processing_jobs(reel_id, job_type)
WHERE status IN ('QUEUED','CLAIMED','DOWNLOADING','EXTRACTING_AUDIO','TRANSCRIBING','SUMMARIZING','SAVING','REPLYING');
CREATE INDEX idx_reel_requests_reel_reply ON reel_requests(reel_id, reply_status);
CREATE INDEX idx_reels_last_requested ON reels(last_requested_at DESC);
CREATE INDEX idx_resources_status_expiry ON reel_resources(status, expires_at);
