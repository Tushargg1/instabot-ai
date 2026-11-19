# Kiro Master Prompt — Instagram Reel AI Summarizer

Build a production-ready monorepo called `instagram-reel-ai` with:

- Backend: Java 17+, Spring Boot 3.x, Maven
- Frontend: React 19 + Vite + TypeScript
- Database: PostgreSQL 16+
- ORM: Spring Data JPA / Hibernate
- Migrations: Flyway
- AI: OpenAI API
- Media processing: FFmpeg
- Containerization: Docker Compose
- Tests: JUnit 5 + Mockito + Testcontainers where practical

## Product goal

The owner connects an Instagram Professional account to the application. When the owner sends a supported Reel/media message to the Instagram account, the application should:

1. Receive the Instagram webhook event.
2. Verify the webhook request.
3. Persist the inbound event idempotently.
4. Identify the sender and message.
5. Determine whether the message contains supported Reel/media information.
6. Queue an asynchronous processing job.
7. Obtain media only through supported/authorized mechanisms.
8. Never ask for or store an Instagram password.
9. Download media to temporary storage only when the application is authorized to do so.
10. Extract audio with FFmpeg when necessary.
11. Transcribe speech with OpenAI audio transcription.
12. Summarize the transcript with OpenAI structured output.
13. Persist transcript, summary, key points, tags, entities, source URL and processing metadata.
14. Send a concise completion message back through the Instagram Messaging API.
15. Show the saved Reel in the React dashboard.

Do NOT implement Instagram login automation with Selenium/Playwright, browser-cookie extraction, password storage, private API scraping, or bypasses around Instagram/Meta restrictions.

The application must fail gracefully when Instagram does not expose downloadable media for an event. In that case, persist the event as `MEDIA_UNAVAILABLE`, send a useful explanation to the owner, and provide a supported next step rather than attempting an unauthorized workaround.

## Architecture

Use:

Instagram / Meta
  -> HTTPS webhook
  -> Spring Boot WebhookController
  -> PostgreSQL + job queue
  -> async ReelProcessingService
  -> MediaService
  -> FFmpeg
  -> OpenAI TranscriptionService
  -> OpenAI SummaryService
  -> PostgreSQL
  -> Instagram Messaging API reply

React dashboard talks only to Spring Boot REST APIs.

Use a modular backend:

- config
- controller
- service
- repository
- entity
- dto
- mapper
- exception
- security
- instagram
- ai
- media
- job
- storage

## Functional requirements

### Instagram connection

Create an admin UI for storing/configuring:

- Instagram professional account id
- Meta app id
- Meta access token reference
- webhook verify token
- webhook subscription status

Secrets must never be returned by GET APIs and must never be logged.

Use environment variables for secrets in development. Abstract secret access behind a `SecretProvider`.

### Webhook

Implement:

GET `/api/webhooks/instagram`

POST `/api/webhooks/instagram`

GET must implement Meta webhook verification.

POST must:

- validate the webhook signature when applicable
- parse the event
- persist a unique event identifier
- return HTTP 200 quickly
- enqueue processing asynchronously

Never perform long-running AI/media work inside the webhook request.

### Processing

Create a job state machine:

RECEIVED
QUEUED
DOWNLOADING
EXTRACTING_AUDIO
TRANSCRIBING
SUMMARIZING
SAVING
REPLYING
COMPLETED

Failure states:

MEDIA_UNAVAILABLE
TRANSCRIPTION_FAILED
SUMMARY_FAILED
REPLY_FAILED
FAILED

Add retry count and exponential backoff.

The same webhook event must never create duplicate Reel records.

### AI summary schema

The model must return structured JSON equivalent to:

{
  "title": "...",
  "summary": "...",
  "keyPoints": ["...", "..."],
  "actionItems": ["..."],
  "topics": ["...", "..."],
  "toolsOrProducts": ["...", "..."],
  "peopleOrCompanies": ["...", "..."],
  "claimsToVerify": ["...", "..."],
  "language": "en",
  "confidence": 0.0
}

Do not invent facts. If something is unclear, state that it is unclear.

Summary should normally be 80–180 words.

### Dashboard

Create a polished responsive dashboard with:

- Overview
- Saved Reels
- Reel detail
- Processing jobs
- Instagram connection
- Settings

Saved Reels table/cards:

- thumbnail if available
- title
- summary excerpt
- creator
- saved date
- topics/tags
- processing status

Search by:

- title
- summary
- transcript
- topic
- creator

Filters:

- date
- processing status
- topic

Reel detail page:

- source URL
- embedded/open-source link where supported
- transcript
- summary
- key points
- action items
- tags
- tools/products
- claims to verify
- processing metadata

Do not expose access tokens.

## Database

Use PostgreSQL and Flyway.

Tables:

instagram_accounts
instagram_webhook_events
reels
reel_transcripts
reel_summaries
processing_jobs

Use UUID primary keys.

Use UTC timestamps.

Add indexes for:

- instagram_webhook_events.external_event_id UNIQUE
- reels.source_url
- reels.created_at
- reels.processing_status
- reel_summaries.title
- processing_jobs.status
- processing_jobs.created_at

Use JSONB for flexible structured AI output where useful, while also keeping important searchable fields normalized.

## API

Implement:

GET  `/api/reels`
GET  `/api/reels/{id}`
DELETE `/api/reels/{id}`
GET `/api/reels/search?q=...`
GET `/api/jobs`
GET `/api/jobs/{id}`

Instagram:

GET `/api/instagram/status`
POST `/api/instagram/test`
POST `/api/webhooks/instagram` 
GET `/api/webhooks/instagram`

Health:

GET `/api/health`

Use DTOs. Never expose JPA entities directly.

## Security

For dashboard APIs implement JWT authentication.

Roles:

ADMIN
USER

For the first version, allow one ADMIN account configured through environment variables or a bootstrap migration. Do not build public signup.

Protect:

- Instagram settings
- job retry endpoints
- deletion
- secret-related configuration

Webhook endpoint must use Meta's verification/signature mechanisms rather than dashboard JWT.

## Configuration

Use:

SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD
OPENAI_API_KEY
OPENAI_TRANSCRIPTION_MODEL
OPENAI_SUMMARY_MODEL
INSTAGRAM_APP_ID
INSTAGRAM_APP_SECRET
INSTAGRAM_ACCESS_TOKEN
INSTAGRAM_ACCOUNT_ID
INSTAGRAM_WEBHOOK_VERIFY_TOKEN
JWT_SECRET
MEDIA_TEMP_DIR
MAX_MEDIA_SIZE_MB
FFMPEG_PATH

Never hard-code secrets.

## Reliability

Use:

- connection pooling
- transaction boundaries
- idempotency
- retry/backoff
- timeouts
- structured logging
- correlation/request ids
- graceful shutdown
- cleanup of temporary media
- maximum file-size checks
- MIME-type validation
- SSRF protection for any server-side media URL fetching

Never let arbitrary user-controlled URLs access internal/private network addresses.

## Docker

Provide:

docker-compose.yml

Services:

- postgres
- backend
- frontend

FFmpeg must be available in the backend image.

Provide `.env.example`.

## Testing

Write tests for:

- webhook verification
- duplicate webhook handling
- webhook parsing
- job state transitions
- media validation
- transcription service mocking
- summary service mocking
- repository queries
- Reel REST APIs
- authentication
- SSRF protections

Use Testcontainers for PostgreSQL integration tests if practical.

## Frontend

Use TypeScript.

Use a clean modern SaaS dashboard.

Use React Router.

Use TanStack Query for API state.

Use a small API client layer.

Do not put business logic directly into components.

## Observability

Use structured logs.

Every processing job should have:

jobId
eventId
reelId
stage
durationMs
status

Never log:

access tokens
JWTs
API keys
passwords
full transcripts at INFO level

## Developer experience

Provide:

README.md
.env.example
docker-compose.yml
backend/README.md
frontend/README.md

README must explain:

1. prerequisites
2. environment variables
3. local PostgreSQL option
4. Docker startup
5. database migrations
6. how to expose localhost for Meta webhook development
7. Meta/Instagram configuration
8. OpenAI configuration
9. how to test webhook delivery
10. how to run backend tests
11. how to run frontend
12. production deployment notes

## Important implementation rule

Do not invent undocumented Instagram API endpoints or fields.

Create an `InstagramClient` interface and isolate Meta-specific API calls in `MetaInstagramClient`.

Where current Meta API behavior cannot be confirmed from official documentation, implement a clearly marked adapter/stub and document exactly what must be configured.

The application must still run locally with a `MockInstagramClient` and `MockAiClient`.

## Definition of done

The project is done only when:

- `docker compose up --build` starts all services
- Flyway creates the PostgreSQL schema
- backend health endpoint works
- frontend loads
- admin authentication works
- mock webhook can create a Reel/job
- mocked AI processing completes end-to-end
- duplicate webhook is ignored
- failed jobs can be retried
- real Instagram credentials can be supplied through environment variables
- no secrets are committed
- tests pass
- README is complete
