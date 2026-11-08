# MY SERVICES — My Launcher Core

Initial backend foundation for the My Launcher ecosystem. This service is separate from the Android application and is intended to become the shared API entry point for My Account, My Cloud, My Mod Hub, My Download, My Security, My Profile, My Performance, My News, My Update and My Support.

## Included
- Express API with security headers, explicit CORS allow-list and request-size limits.
- PostgreSQL schema for users, service catalog and refresh-session storage.
- Argon2id password hashing and short-lived signed access tokens.
- Database-backed health endpoint and first-party service catalog endpoint.
- Docker deployment file and environment template.
- Registration disabled by default until email verification and abuse controls exist.

## Run locally
1. Copy `.env.example` to `.env` and set strong secrets.
2. Create a PostgreSQL database and apply `schema.sql`.
3. Run `npm install` then `npm start`.
4. The API listens on `PORT` (default 8080).

## Endpoints
- `GET /health` — database-backed health check.
- `GET /v1/services` — enabled first-party service catalog.
- `POST /v1/auth/register` — disabled unless explicitly enabled; verification workflow is still required.
- `POST /v1/auth/login` — verified accounts only.
- `GET /v1/account/me` — authenticated account profile.

## Production requirements
Set `DATABASE_URL`, `JWT_SECRET` (at least 32 random bytes), `JWT_ISSUER`, `CORS_ORIGINS`, `DATABASE_SSL=true` where required, and `PORT`. Never commit credentials. Before public registration, implement email verification, password reset, refresh-token rotation/revocation, account deletion/export, audit events, bot protection and monitoring.

Microsoft/Xbox and ELY.BY remain external identity integrations. MY SERVICES must not collect their passwords or impersonate their authentication systems. This commit is a backend foundation, not a production-ready identity provider.
