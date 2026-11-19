# Architecture

## Components

### Backend

Spring Boot application.

Packages:

com.example.instagramreelai
├── config
├── controller
├── dto
├── entity
├── exception
├── repository
├── service
├── instagram
├── ai
├── media
├── job
└── security

### Frontend

src/
├── api/
├── components/
├── layouts/
├── pages/
├── hooks/
├── types/
└── routes/

## Processing sequence

1. Instagram sends webhook.
2. Verify request.
3. Persist event.
4. Check external_event_id uniqueness.
5. Parse message/media metadata.
6. Create Reel.
7. Create ProcessingJob.
8. Return HTTP 200.
9. Worker picks job.
10. Resolve authorized media.
11. Validate MIME type and size.
12. Download to temporary directory.
13. FFmpeg extracts audio.
14. OpenAI transcribes.
15. OpenAI summarizes using structured output.
16. Persist transcript and summary.
17. Delete temporary files.
18. Reply to Instagram user.
19. Mark job COMPLETED.

## Failure handling

Media unavailable:
- mark reel MEDIA_UNAVAILABLE
- store safe error code
- reply with instructions

AI failure:
- retry up to configured maximum
- exponential backoff
- final state FAILED

Instagram reply failure:
- keep Reel COMPLETED but mark reply status REPLY_FAILED
- allow manual retry

## Security boundaries

Webhook:
Meta verification/signature

Dashboard:
JWT

Secrets:
environment/secret provider

Media:
temporary isolated storage

Database:
least-privilege application user

## Non-goals

- scraping private Instagram endpoints
- Instagram password storage
- Selenium login
- browser automation
- bypassing rate limits
- bypassing Meta permissions
- downloading media that the API does not authorize
