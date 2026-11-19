# Instagram Reel AI

Spring Boot + React + PostgreSQL application that receives supported Instagram messages, processes Reel/media content through an asynchronous pipeline, summarizes it with AI, and stores it in a searchable personal library.

## Important

Use only Meta/Instagram-supported APIs and permissions.

Do not store Instagram passwords.

Do not use browser automation to log into Instagram.

Do not attempt to bypass Meta permissions, rate limits or media-access restrictions.

## Stack

- Java 17+
- Spring Boot 3.x
- PostgreSQL 16+
- Flyway
- React + TypeScript + Vite
- OpenAI API
- FFmpeg
- Docker

## Run

1. Copy `.env.example` to `.env`.
2. Fill PostgreSQL and AI configuration.
3. Run:

docker compose up --build

4. Open the frontend.
5. Configure the Instagram/Meta app according to the current official Meta documentation.

## Development mode

The repository must include mock Instagram and mock AI implementations so the entire pipeline can be tested without real Instagram credentials.

## OpenAI

Use the OpenAI API for transcription and structured summarization. Keep the API key server-side only.

Official OpenAI developer documentation:
https://platform.openai.com/docs

## Meta

Instagram messaging/webhook behavior and permissions can change. Follow the current Meta developer documentation for the exact setup and supported message/media fields.

## Production

Recommended:

- managed PostgreSQL
- HTTPS
- secret manager
- object storage for any retained media
- background worker
- monitoring
- backups
- database migrations
- strict CORS
- restricted outbound network access

The application should store the transcript/summary by default rather than permanently retaining every video unless the user explicitly enables media retention.
