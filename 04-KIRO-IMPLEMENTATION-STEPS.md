# Kiro Implementation Sequence

Use the master prompt first.

Then implement in these phases.

## Phase 1 — Scaffold

Create:

- Spring Boot backend
- React TypeScript frontend
- PostgreSQL Docker service
- Flyway
- environment configuration
- health endpoint

Verify the application starts.

## Phase 2 — Database

Create entities, repositories and Flyway migrations.

Run integration tests against PostgreSQL.

## Phase 3 — Authentication

Implement JWT dashboard authentication.

Create one bootstrap ADMIN.

Never expose secrets.

## Phase 4 — Instagram adapter

Implement:

InstagramClient
MetaInstagramClient
MockInstagramClient

Implement webhook controller.

Keep Meta-specific code isolated.

## Phase 5 — Job system

Implement:

ProcessingJob
JobService
JobWorker
RetryPolicy

Webhook must enqueue and return quickly.

## Phase 6 — Media

Implement:

MediaService
MediaValidator
TemporaryMediaStorage
FFmpegAudioExtractor

Add MIME and size validation.

Add SSRF protection.

## Phase 7 — AI

Implement:

TranscriptionService
OpenAiTranscriptionService
SummaryService
OpenAiSummaryService
MockAiService

Use structured model output.

## Phase 8 — End-to-end pipeline

Wire:

webhook -> event -> reel -> job -> media -> transcription -> summary -> persistence -> reply

Add an integration test using mock Instagram + mock AI.

## Phase 9 — Frontend

Build:

Dashboard
Reels list
Search
Filters
Reel detail
Jobs
Instagram status
Settings

## Phase 10 — Production hardening

Add:

- rate limits
- timeouts
- retries
- structured logs
- correlation ids
- cleanup jobs
- database indexes
- security headers
- CORS configuration
- Docker healthchecks

## Phase 11 — Documentation

Document exactly how to:

- create/configure Meta app
- connect Instagram Professional account
- configure webhook
- set environment variables
- expose local webhook
- test with a real DM
- troubleshoot unsupported media

Do not claim a feature works unless it has been verified against the currently supported Meta API.
