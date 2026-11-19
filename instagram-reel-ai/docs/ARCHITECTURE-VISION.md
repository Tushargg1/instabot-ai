# Instagram Reel Intelligence & Resource Finder

## Product boundary
One operator-owned Main Instagram Professional account is the user gateway. End users may send Reels from normal personal accounts; they never connect credentials to the dashboard. Worker accounts may perform only documented Meta API operations or explicit legitimate manual actions.

## Core invariant
A canonical Reel is processed once and reused many times:

`webhook event -> reel request -> canonical Reel -> single active job -> shared transcript/summary/resource -> reply fan-out`

PostgreSQL is the source of truth. Redis is a disposable hot cache. A Redis outage must not lose data or block durable ingestion.

## Durable model
- `reels.canonical_key`: deterministic media ID or normalized URL identity.
- `reel_requests`: one row per sender/webhook; many requests reference one Reel.
- `reel_transcripts` and `reel_summaries`: one shared result per Reel.
- `reel_resources`: creator links/actions and verification/expiry metadata.
- `processing_jobs`: typed, retryable jobs with one active job per Reel/type.
- `instagram_webhook_events.external_event_id`: delivery idempotency.

## Processing stages
1. Validate Meta webhook signature.
2. Persist event/request and coalesce by canonical key.
3. Return cached result or ensure one database job.
4. Claim the job atomically.
5. Download approved media, transcribe, summarize, and persist.
6. Fan the result out to every pending request by Job ID/request association.

## Safety
No Instagram passwords, scraping, private APIs, access-control bypasses, or rate-limit bypasses. Media downloads require SSRF, size, redirect, and MIME defenses. Temporary media is deleted after processing.

## Delivery phases
1. Canonical identity, request coalescing, atomic claims, reply fan-out.
2. Redis hot result cache and dashboard metrics.
3. Resource detection/extraction and manual-action workflow.
4. Signed web-form fallback for inaccessible forwarded Reels.
5. Worker registry/heartbeats, retention, full-text search, and scale-out queue.
