# LinguaAI — AI-Powered Language Learning Platform

> **Learn smarter with your personal AI language tutor.**

LinguaAI is a full-stack language-learning platform: a native **Android app (Kotlin + Jetpack Compose)**
with offline-first learning data, a **Kotlin Ktor backend** with MySQL persistence and JWT
authentication, and a **server-side AI gateway** that powers a context-aware AI Tutor — without ever
shipping an AI API key inside the app.

| Layer | Stack |
| --- | --- |
| Mobile | Kotlin, Jetpack Compose, Material 3, MVVM + Clean Architecture, Hilt, Room, DataStore, WorkManager |
| Backend | Kotlin, Ktor, Exposed, Flyway, JWT (access + refresh rotation), bcrypt |
| Database | MySQL 8 (Docker) with H2 in-memory for integration tests |
| AI | Provider-agnostic AI gateway (OpenAI-compatible / Mock), server-managed prompts, per-user rate limiting |
| Quality | JUnit, MockWebServer, Compose UI tests, detekt, ktlint, Android Lint, GitHub Actions CI |
| Delivery | Docker Compose (backend + MySQL), Conventional Commits, Mermaid docs |

## Status

🚧 **Under active development** — see [IMPLEMENTATION_PLAN.md](IMPLEMENTATION_PLAN.md) for the
phased build plan and commit sequence.

## Documentation

- [Architecture](docs/architecture/README.md) — system & app architecture, ADRs
- [API reference](docs/api/README.md)
- [Database ERD](docs/database/erd.md)
- [Diagrams](docs/diagrams/README.md)
- [Development guide](docs/DEVELOPMENT.md) · [Deployment guide](docs/DEPLOYMENT.md) · [Testing guide](docs/TESTING.md)

## Quick start (once released)

```bash
# 1. Backend + MySQL
cp .env.example .env   # fill in secrets
docker compose up --build

# 2. Android
# Open android/ in Android Studio, or:
cd android && ./gradlew assembleDebug
```

## License

[MIT](LICENSE)
