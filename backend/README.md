# Backend

Spring Boot API for the EnglishTranslation app. Spec: `../.scratch/translator/spec.md`.

## Flow

```
request ─► normalise ─► cache key ─► Postgres hit? ──yes──► return (cached: true)
                                          │ no
                                          ▼
                                  Claude (structured JSON) ─► save ─► return (cached: false)
```

| Package | Job |
|---|---|
| `cache` | `CacheKeys`: normalise text, build the sha256 key. The iOS app must copy these rules. |
| `claude` | `ClaudeService`: the only class that calls the Claude API. Prompts live here. |
| `translation` | `POST /api/translations` — sentences, into the `targetLang` the app sends (`TargetLanguage` lists the codes) |
| `word` | `GET /api/words/{word}` — words missing from the bundled dictionary |
| `common` | Maps Claude errors to HTTP (refused → 422, API down → 502) |

Schema changes go in `src/main/resources/db/migration` (Flyway). Hibernate only validates.

## Run

Needs Docker (Spring Boot starts Postgres from `compose.yaml` for you) and `CLAUDE_API_KEY`.

```bash
cp .env.example .env
```

Put your key in `.env` (git-ignored), then:

```bash
./mvnw spring-boot:run
```

The API runs on port **8081** (8080 is used by the local Jitsi container). Change it with `PORT=9000`.

```bash
curl -s localhost:8081/api/translations -H 'Content-Type: application/json' -d '{"text": "I was reluctant to leave.", "targetLang": "vi"}'
```

```bash
curl -s localhost:8081/api/words/serendipity
```

Run the same request twice: the second answer has `"cached": true` and makes no Claude call.

## Test

```bash
./mvnw test
```

Tests use H2 and a mocked `ClaudeService`, so they need neither Docker nor an API key.

## Deploy (Docker, VPS)

`Dockerfile` builds a small image (JDK to build, JRE 17 to run, non-root user). `docker-compose.prod.yml` runs the backend and
Postgres with a named volume. Health check: `GET /actuator/health`.

```bash
cp .env.example .env    # set CLAUDE_API_KEY and DB_PASSWORD
docker compose -f docker-compose.prod.yml up -d --build
```

Full steps (HTTPS with Caddy, firewall, iPhone setup): `backlog/docs/guides/doc-11 - Deploy-to-a-VPS.md`.
