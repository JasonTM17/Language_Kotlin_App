# Deployment guide

## What ships

| Artifact | Where |
| --- | --- |
| Backend image | `server/Dockerfile` — multi-stage, runs as a non-root user |
| Local stack | `docker-compose.yml` — backend + MySQL 8 |
| Android APK | `android/app/build/outputs/apk/debug/app-debug.apk` |

## Deploy the backend with Docker

```bash
cp .env.example .env
# Generate a real secret — the compose file refuses to start without one:
#   openssl rand -base64 64
$EDITOR .env

docker-compose up --build -d
docker-compose ps          # both services should report healthy
curl http://localhost:8080/api/v1/health
```

`docker-compose.yml` uses `:?` on `DB_PASSWORD`, `DB_ROOT_PASSWORD` and
`JWT_SECRET`, so a missing value fails immediately with a clear message instead of
starting a container with an empty secret.

### Why the backend waits for a healthcheck

`depends_on: condition: service_healthy` rather than plain `depends_on`. MySQL
accepts a TCP connection several seconds before it can serve queries, so a
container that merely started is not a database that works. Without this, the
backend crash-loops on first boot and the cause looks like an application bug.

## Configuration

Every value comes from the environment; nothing is hard-coded. `.env.example`
documents the full set. The ones that matter in production:

| Variable | Notes |
| --- | --- |
| `JWT_SECRET` | **Must** be a long random value. Rotating it invalidates all sessions. |
| `DB_PASSWORD`, `DB_ROOT_PASSWORD` | Required; compose will not start without them. |
| `AI_PROVIDER` | `mock` for a demo with no cost, `openai-compatible` for a real provider. |
| `AI_API_KEY` | Server-side only. Never sent to, or stored on, the device — see [ADR-0001](architecture/adr/0001-ai-key-never-on-device.md). |
| `RUN_MIGRATIONS` | `true` applies Flyway migrations at startup. |
| `AI_RATE_LIMIT_PER_MINUTE` | Per-user cost guard. |

## Migrations

Flyway runs at startup when `RUN_MIGRATIONS=true`, so a fresh database is
provisioned with no manual step. Migrations are forward-only: never edit an
applied file, add a new one.

## Known limitations

These are real and are stated rather than glossed over:

- **Rate limiting is in-process.** With more than one backend instance each
  instance counts independently, so the effective quota multiplies. Fine for a
  single-instance deployment; needs a shared counter (Redis) behind the same
  interface before horizontal scaling.
- **Conversation memory is bounded, and the summary is extractive.** It preserves
  topic continuity, not nuance — see
  [ADR-0004](architecture/adr/0004-bounded-conversation-memory.md).
- **The database volume is not backed up** by anything in this repository.
- **No TLS termination.** The compose stack serves plain HTTP; put it behind a
  reverse proxy for anything public.
- **The Android release build points at a placeholder host**
  (`https://linguaai.example.com/api/v1/`). Set `BASE_URL` before shipping.

## Deploying the Android app

```bash
cd android
JAVA_HOME=/path/to/jdk-24 ./gradlew assembleRelease
```

A release build needs a signing keystore. Debug builds are unsigned-for-release
and installable only on a development device.

The app targets the emulator loopback (`10.0.2.2`) in debug. For a physical
device, point `BASE_URL` at a reachable host.
