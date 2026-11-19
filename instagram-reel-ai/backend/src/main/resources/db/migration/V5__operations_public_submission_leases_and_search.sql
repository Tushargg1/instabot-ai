-- Consolidated operational model. PostgreSQL production only; H2 dev keeps Flyway disabled.
ALTER TABLE processing_jobs
    ADD COLUMN lease_owner VARCHAR(128),
    ADD COLUMN lease_expires_at TIMESTAMPTZ,
    ADD COLUMN heartbeat_at TIMESTAMPTZ,
    ADD COLUMN retryable BOOLEAN NOT NULL DEFAULT TRUE,
    ADD COLUMN retry_reason VARCHAR(255);

ALTER TABLE reel_requests ALTER COLUMN webhook_event_id DROP NOT NULL;
ALTER TABLE reel_requests ALTER COLUMN instagram_sender_id DROP NOT NULL;
ALTER TABLE reel_requests ADD COLUMN source VARCHAR(50) NOT NULL DEFAULT 'INSTAGRAM_WEBHOOK';
ALTER TABLE reel_requests ADD COLUMN external_request_id VARCHAR(255);
ALTER TABLE reel_requests ADD CONSTRAINT ck_reel_request_identity CHECK (
    (source = 'INSTAGRAM_WEBHOOK' AND webhook_event_id IS NOT NULL AND instagram_sender_id IS NOT NULL)
    OR (source <> 'INSTAGRAM_WEBHOOK' AND external_request_id IS NOT NULL));
CREATE UNIQUE INDEX uq_reel_requests_source_external
    ON reel_requests(source, external_request_id) WHERE external_request_id IS NOT NULL;

ALTER TABLE reel_resources
    ADD COLUMN last_refresh_attempt_at TIMESTAMPTZ,
    ADD COLUMN last_refresh_error TEXT,
    ADD COLUMN refresh_failure_count INT NOT NULL DEFAULT 0,
    ADD COLUMN refresh_metadata JSONB;

CREATE TABLE manual_actions (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    reel_resource_id UUID NOT NULL REFERENCES reel_resources(id) ON DELETE CASCADE,
    action_type VARCHAR(100) NOT NULL,
    action_payload TEXT,
    status VARCHAR(50) NOT NULL DEFAULT 'PENDING',
    claimed_by_user_id UUID REFERENCES app_users(id),
    claimed_at TIMESTAMPTZ,
    lease_expires_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    failed_at TIMESTAMPTZ,
    result TEXT,
    failure_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_manual_actions_status_created ON manual_actions(status, created_at);
CREATE INDEX idx_manual_actions_resource_status ON manual_actions(reel_resource_id, status);
CREATE UNIQUE INDEX uq_manual_actions_open_resource
    ON manual_actions(reel_resource_id)
    WHERE status IN ('PENDING', 'CLAIMED');

CREATE TABLE worker_instances (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    instance_id VARCHAR(128) NOT NULL UNIQUE,
    host_name VARCHAR(255) NOT NULL,
    process_id BIGINT NOT NULL,
    application_name VARCHAR(100) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL,
    registered_at TIMESTAMPTZ NOT NULL,
    last_heartbeat_at TIMESTAMPTZ NOT NULL,
    stopped_at TIMESTAMPTZ
);
CREATE INDEX idx_worker_instances_heartbeat ON worker_instances(last_heartbeat_at);

CREATE TABLE public_submission_tokens (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id UUID NOT NULL REFERENCES app_users(id) ON DELETE CASCADE,
    token_digest VARCHAR(64) NOT NULL UNIQUE,
    public_label VARCHAR(120),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
CREATE INDEX idx_public_submission_tokens_expiry ON public_submission_tokens(expires_at);

-- Canonical identity is tenant-local; the same Reel may be saved independently by different users.
ALTER TABLE reels DROP CONSTRAINT uq_reels_canonical_key;
ALTER TABLE reels ADD CONSTRAINT uq_reels_user_canonical UNIQUE (user_id, canonical_key);

CREATE INDEX idx_jobs_claimable
    ON processing_jobs(status, next_retry_at, priority, created_at)
    WHERE status = 'QUEUED';
CREATE INDEX idx_jobs_lease_expiry
    ON processing_jobs(lease_expires_at) WHERE lease_owner IS NOT NULL;
CREATE INDEX idx_jobs_user_status ON processing_jobs(user_id, status);
CREATE INDEX idx_requests_reel_source ON reel_requests(reel_id, source);
CREATE INDEX idx_resources_reel_expiry ON reel_resources(reel_id, expires_at);

ALTER TABLE reels ADD COLUMN search_document TSVECTOR NOT NULL DEFAULT ''::tsvector;
CREATE OR REPLACE FUNCTION refresh_reel_search_document(target_reel UUID) RETURNS VOID AS $$
BEGIN
    UPDATE reels r SET search_document =
        setweight(to_tsvector('simple', coalesce(r.creator_username, '') || ' ' || coalesce(r.caption, '')), 'A') ||
        setweight(to_tsvector('simple', coalesce((SELECT s.title || ' ' || s.summary FROM reel_summaries s WHERE s.reel_id = r.id), '')), 'B') ||
        setweight(to_tsvector('simple', coalesce((SELECT t.transcript FROM reel_transcripts t WHERE t.reel_id = r.id), '')), 'C')
    WHERE r.id = target_reel;
END;
$$ LANGUAGE plpgsql;

CREATE OR REPLACE FUNCTION reel_search_trigger() RETURNS TRIGGER AS $$
BEGIN
    PERFORM refresh_reel_search_document(NEW.id);
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_reel_search AFTER INSERT OR UPDATE OF creator_username, caption ON reels
    FOR EACH ROW EXECUTE FUNCTION reel_search_trigger();

CREATE OR REPLACE FUNCTION reel_child_search_trigger() RETURNS TRIGGER AS $$
BEGIN
    PERFORM refresh_reel_search_document(COALESCE(NEW.reel_id, OLD.reel_id));
    RETURN COALESCE(NEW, OLD);
END;
$$ LANGUAGE plpgsql;
CREATE TRIGGER trg_summary_search AFTER INSERT OR UPDATE OR DELETE ON reel_summaries
    FOR EACH ROW EXECUTE FUNCTION reel_child_search_trigger();
CREATE TRIGGER trg_transcript_search AFTER INSERT OR UPDATE OR DELETE ON reel_transcripts
    FOR EACH ROW EXECUTE FUNCTION reel_child_search_trigger();
CREATE INDEX idx_reels_search_document ON reels USING GIN(search_document);

DO $$ DECLARE reel_row RECORD;
BEGIN
    FOR reel_row IN SELECT id FROM reels LOOP
        PERFORM refresh_reel_search_document(reel_row.id);
    END LOOP;
END $$;
