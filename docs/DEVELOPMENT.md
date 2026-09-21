# Development guide

## Prerequisites

| Tool | Version | Notes |
| --- | --- | --- |
| JDK | 24 | `JAVA_HOME` must point at it — Gradle picks the JDK from `JAVA_HOME`, not from `java` on `PATH` |
| Android SDK | API 33+ | Set `ANDROID_HOME`, or put `sdk.dir` in `android/local.properties` |
| Docker | any recent | For MySQL, or the whole stack |
| MySQL | 8 | Only if you run the backend outside Docker |

> **`JAVA_HOME` matters more than it looks.** With a newer JDK on `PATH` and no
> `JAVA_HOME`, Gradle fails with a bare version number as the entire error
> message. If a Gradle command fails with something that looks like
> `What went wrong: 26.0.1`, this is why.

## Run everything with Docker

```bash
cp .env.example .env      # then fill in JWT_SECRET at minimum
docker-compose up --build
```

This starts MySQL and the backend. `GET http://localhost:8080/api/v1/health`
should return `ok`.

## Run the backend locally

```bash
cd server
JAVA_HOME=/path/to/jdk-24 ./gradlew run
```

Migrations run on startup (`RUN_MIGRATIONS=true`), so a fresh database is
provisioned automatically. Point `DB_URL` at a running MySQL, or at H2 for a
throwaway instance.

## Run the Android app

```bash
cd android
JAVA_HOME=/path/to/jdk-24 ./gradlew assembleDebug
```

The debug build targets `http://10.0.2.2:8080/api/v1/` — the host loopback as
seen from the Android emulator. On a physical device, point `BASE_URL` at your
machine's LAN address instead.

### Lint like CI does

GitHub CI runs `:app:ktlintMainSourceSetCheck`, which applies rules the
locally-typical `:app:ktlintDebugSourceSetCheck` does not — chained-call
newline placement, expression bodies on signatures, `SCREAMING_SNAKE_CASE`
for constants. A run that passes locally can still fail CI for that reason.
Run the main-source-set task before pushing:

```bash
cd android && ./gradlew :app:ktlintMainSourceSetCheck
```

### Debug against the compose backend through adb reverse

To point the app at the Docker Compose backend through the host loopback
instead of `10.0.2.2`, forward the port and build with the matching base URL:

```bash
adb reverse tcp:8081 tcp:8081
cd android && ./gradlew assembleDebug \
  -Plinguaai.debugBaseUrl=http://127.0.0.1:8081/api/v1/
```

The device's own `127.0.0.1:8081` then reaches the host, and `127.0.0.1` is
cleartext-allowlisted in `network_security_config.xml` for exactly this
debugging case; production traffic still must use HTTPS. 8081 is the host port
the Compose stack publishes when `SERVER_PORT=8081` is set in `.env` — adjust
both sides if the backend listens elsewhere.

Note that Gradle's configuration cache can serve stale compile results for
flags like this one, so add `--no-configuration-cache` whenever a
BuildConfig-changing flag must take effect.

## Tests

```bash
cd server  && JAVA_HOME=/path/to/jdk-24 ./gradlew test   # 84 tests, no network needed
cd android && JAVA_HOME=/path/to/jdk-24 ./gradlew testDebugUnitTest
```

See [TESTING.md](TESTING.md) for what each suite covers, and which tests need a
device.

## Configuration

All configuration is environment-driven; `.env.example` documents every variable.
The backend reads it through `AppConfig.fromEnv`, so nothing is hard-coded.

Two variables are worth calling out:

- `AI_PROVIDER=mock` runs the tutor with no network and no key. Use it for
  development and demos.
- `AI_PROVIDER=openai-compatible` with `AI_BASE_URL` and `AI_API_KEY` runs
  against any OpenAI-compatible endpoint.
- `QDRANT_URL=http://qdrant:6333` selects the Qdrant retrieval engine in the
  Compose stack. Keep `RAG_AUTO_INDEX=false` during a large data load and run
  the authenticated reindex operation explicitly after the import.

The reproducible million-row vocabulary load is documented in the
[multilingual catalogue guide](data/multilingual-vocabulary.md). Its raw source
files are cached under `.cache/multilingual-vocabulary` and are never committed.

## Conventions

- **Conventional Commits.** `feat:`, `fix:`, `chore:`, `test:`, `docs:`.
- **Build before you commit.** The repository went through a period where five
  consecutive commits landed on a build that did not compile, because nothing ran
  the build. Do not repeat that.
- **Schema changes are new migrations.** Never edit an applied Flyway migration.
- **Room schema changes** require a new version, a `Migration`, and the exported
  schema JSON committed under `android/app/schemas/`.
- **No secrets in the repository.** `.env` is gitignored; `.env.example` is the
  template and must stay complete.

## Troubleshooting

| Symptom | Cause |
| --- | --- |
| Gradle fails with a bare version number | `JAVA_HOME` unset; Gradle took the JDK from `PATH` |
| `Could not create parent directory for lock file` | The Gradle user home is a broken symlink |
| `Unable to read Kotlin metadata` | Hilt older than 2.53 with Kotlin 2.1 — see [ADR notes](architecture/README.md) |
| `Cannot find the schema file in the assets folder` | `app/schemas` is not registered as an androidTest asset root |
