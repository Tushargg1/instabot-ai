# Instagram Reel Intelligence & Resource Finder

A full-stack platform where users send Reels to one Main Instagram account. The backend canonicalizes each Reel, processes it once, stores shared AI/resource intelligence, and fans the result out to every requester.

## Stack

- **Backend**: Java 17, Spring Boot 3.3, Spring Data JPA, Spring Security
- **Frontend**: React 19, TypeScript, Vite, TailwindCSS, TanStack Query
- **Database**: PostgreSQL 16 with Flyway migrations
- **Hot cache**: Redis 7.4 (fail-open; never the source of truth)
- **Queue**: PostgreSQL-backed typed jobs with atomic claims
- **AI**: Groq Whisper + Llama defaults, provider abstraction in progress
- **Media**: FFmpeg for audio extraction
- **Infrastructure**: Docker Compose

## Prerequisites

- Docker & Docker Compose
- Java 17+ (for local backend development)
- Node.js 20+ (for local frontend development)
- FFmpeg (installed in Docker image, needed locally for dev)

## Quick Start with Docker

```bash
# 1. Clone and enter the project
cd instagram-reel-ai

# 2. Copy environment file
cp .env.example .env

# 3. Edit .env with your configuration (see below)

# 4. Start all services
docker compose up --build
```

Services:
- Frontend: http://localhost:3002
- Backend API: http://localhost:8085
- PostgreSQL: localhost:5432

Default admin login: `admin` / `admin123`

## Environment Variables

| Variable | Required | Description |
|----------|----------|-------------|
| `SPRING_DATASOURCE_PASSWORD` | Yes | PostgreSQL password |
| `OPENAI_API_KEY` | No* | OpenAI API key for real transcription/summary |
| `INSTAGRAM_APP_ID` | OAuth* | Deployment-level Meta App ID |
| `INSTAGRAM_APP_SECRET` | OAuth* | Deployment-level Meta App Secret; never entered by end users |
| `INSTAGRAM_OAUTH_REDIRECT_URI` | OAuth* | Exact backend callback registered in Meta |
| `INSTAGRAM_FRONTEND_REDIRECT_URI` | OAuth* | Frontend Instagram page used after callback |
| `INSTAGRAM_GRAPH_API_VERSION` | No | Versioned Graph API path (default `v26.0`) |
| `INSTAGRAM_WEBHOOK_VERIFY_TOKEN` | Webhooks* | Random deployment-level webhook verification token |
| `JWT_SECRET` | Yes | JWT signing secret (32+ characters) |
| `ADMIN_USERNAME` | No | Bootstrap admin username (default: admin) |
| `ADMIN_PASSWORD` | No | Bootstrap admin password (default: admin123) |

*When left empty, mock implementations are used automatically.

## Development Mode

The app includes mock implementations for both Instagram and OpenAI services. When API keys are not configured, the entire pipeline can be tested using the "Send Test Event" button in the Instagram page of the dashboard.

### Local PostgreSQL

```bash
docker compose up postgres
```

### Run Backend Locally

```bash
cd backend
./mvnw spring-boot:run
```

### Run Frontend Locally

```bash
cd frontend
npm install
npm run dev
```

The Vite dev server runs on `http://localhost:3002` and proxies `/api/*` requests to `localhost:8085`.

## Database Migrations

Flyway runs automatically on application startup. Migrations are in:
```
backend/src/main/resources/db/migration/
```

## Meta / Instagram OAuth Configuration

The Meta App and single Main Instagram account belong to the application operator. End users send Reels to that account from normal personal accounts; they do not connect credentials or need to convert their own account.

1. Create a Meta App at https://developers.facebook.com and add the Instagram API product.
2. Configure **Instagram API with Instagram Login** for Professional Business/Creator accounts.
3. Register this exact Valid OAuth Redirect URI:
   - Local: `http://localhost:8085/api/instagram/oauth/callback`
   - Production: `https://your-api-domain/api/instagram/oauth/callback`
4. Request the current scopes:
   - `instagram_business_basic`
   - `instagram_business_manage_messages`
5. Configure the webhook:
   - Callback URL: `https://your-api-domain/api/webhooks/instagram`
   - Verify token: the same random value as `INSTAGRAM_WEBHOOK_VERIFY_TOKEN`
   - Subscribe to the messaging fields required by your approved Meta app configuration.
6. Set `INSTAGRAM_APP_ID`, `INSTAGRAM_APP_SECRET`, `INSTAGRAM_OAUTH_REDIRECT_URI`, `INSTAGRAM_FRONTEND_REDIRECT_URI`, and `INSTAGRAM_WEBHOOK_VERIFY_TOKEN` in the deployment environment.
7. In development mode, add each connecting account as an app tester. For public users, complete Meta App Review and switch the app live.
8. Sign in to this application, open **Instagram**, and click **Connect with Instagram**.

The backend validates a single-use, expiring OAuth state, exchanges the code server-side, upgrades the token, encrypts it with AES-256-GCM, and assigns the Instagram account to the signed-in application user. Disconnecting removes the locally stored token.

## Exposing Localhost for Webhook Development

Use a tunneling service to expose your local backend to the internet:

```bash
# Using ngrok
ngrok http 8085

# Using cloudflared
cloudflared tunnel --url http://localhost:8085
```

Set the tunnel URL as your webhook callback URL in the Meta App dashboard.

## Testing Webhook Delivery

1. Start the application
2. Login to the dashboard
3. Navigate to the Instagram page
4. Click "Send Test Event"
5. Check the Jobs page for processing status
6. View the created Reel in the Saved Reels page

For real webhooks, send a video/reel DM to your connected Instagram Professional account.

## Running Backend Tests

```bash
cd backend
./mvnw test
```

## API Endpoints

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| GET | `/api/health` | No | Health check |
| POST | `/api/auth/login` | No | Login |
| GET | `/api/webhooks/instagram` | No | Meta webhook verification |
| POST | `/api/webhooks/instagram` | No | Webhook event receiver |
| GET | `/api/reels` | JWT | List reels |
| GET | `/api/reels/{id}` | JWT | Get reel detail |
| DELETE | `/api/reels/{id}` | ADMIN | Delete reel |
| GET | `/api/reels/search?q=` | JWT | Search reels |
| GET | `/api/jobs` | JWT | List jobs |
| GET | `/api/jobs/{id}` | JWT | Get job detail |
| POST | `/api/jobs/{id}/retry` | JWT | Retry failed job |
| GET | `/api/instagram/status` | JWT | Current user's connection status |
| POST | `/api/instagram/oauth/start` | JWT | Create a single-use state and return Meta authorization URL |
| GET | `/api/instagram/oauth/callback` | No | Validate state, exchange code, store encrypted user token |
| DELETE | `/api/instagram/connection` | JWT | Disconnect current user's account |
| POST | `/api/instagram/test` | JWT/dev only | Send an owned mock event |

## Production Deployment Notes

- Use a managed PostgreSQL instance
- Enable HTTPS with a reverse proxy
- Use a secret manager (AWS Secrets Manager, Vault, etc.)
- Set a strong `JWT_SECRET` (64+ random characters)
- Change the admin password immediately
- Configure strict CORS origins
- Set up monitoring and alerting
- Enable database backups
- Consider rate limiting on webhook endpoints
- Use a persistent volume for temporary media if needed

## Architecture

```
Instagram DM → Meta Webhook → Spring Boot → PostgreSQL
                                    ↓
                              Async Job Worker
                                    ↓
                    Media Download → FFmpeg → OpenAI Whisper
                                                ↓
                                          OpenAI GPT-4o
                                                ↓
                                    Save Results → Reply to User
```

## Important

- Uses only Meta/Instagram-supported APIs and permissions
- Does NOT store Instagram passwords
- Does NOT use browser automation
- Does NOT bypass Meta permissions or rate limits
- Fails gracefully when media is unavailable via the API
