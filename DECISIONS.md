# Platform BE — Key Technical Decisions

_These decisions are architectural constraints, not guidelines. Deviating from them requires team consensus._

## Language & Runtime

| Decision | Choice | Rationale |
|---|---|---|
| Language | Java 21 LTS | Stable virtual threads, wide ecosystem, all required libraries tested against 21 |
| Runtime | Spring Boot 3.5.x | Spring Boot 3.4.x was removed from start.spring.io by March 2026; 3.5.x is the current LTS-equivalent 3.x stable track. Concern about SB4 was ecosystem maturity (springdoc-openapi SB4 support). SB 3.5.x is NOT SB4. |
| Build tool | Maven (not Gradle) | Predictable CI/CD debugging, safer for solo dev vs Gradle edge cases |

## Dependencies

| Dependency | Version | Notes |
|---|---|---|
| `springdoc-openapi-starter-webmvc-ui` | 2.8.6 | NOT from Spring Initializr — added manually. Exposes `/api-docs` (Swagger UI) |
| `flyway-core` | BOM-managed | NOT from Spring Initializr — added manually. Owns all schema changes |
| `logstash-logback-encoder` | 8.0 | Structured JSON logging in prod. No PII fields |
| `testcontainers` | BOM-managed | PostgreSQL integration tests — requires Docker |
| `caffeine` | BOM-managed | In-memory cache for GitHub API (1h TTL) |

## Package Boundary (Enforced from Day 1)

```
dev.chinh.portfolio/
├── auth/
│   ├── session/    ← Session entity + SessionRepository ONLY HERE (architectural boundary)
│   ├── jwt/
│   └── oauth2/
├── platform/
│   ├── metrics/    ← Polling scheduler
│   ├── websocket/  ← Native WebSocket (NOT STOMP/SockJS)
│   ├── webhook/    ← GitHub webhook + HMAC-SHA256 verification
│   ├── contact/
│   └── admin/
├── apps/wallet/    ← Wallet App domain (Epic 5+)
└── shared/
    ├── error/      ← Structured error: {"error": {"code": ..., "message": ...}}
    ├── ratelimit/
    └── config/     ← SecurityConfig, CorsConfig, app registry
```

**Rule:** `Session` entity lives ONLY in `auth.session.*`. No cross-boundary import. Cross-boundary access → 403.

## Security

- JWT: RS256 (asymmetric), access token 15m, refresh token 7d
- CORS: Explicit allow-list — `https://chinh.dev`, `https://wallet.chinh.dev`, `http://localhost:5173`. **No wildcards.**
- CSRF: Disabled (stateless REST API)
- Password hashing: BCrypt cost factor 12

## API Contract

- Base path: `/api/v1/`
- Error format: `{"error": {"code": "SNAKE_CASE_CODE", "message": "Human readable"}}`
- OpenAPI: `GET /api-docs` → Swagger UI, `GET /v3/api-docs` → raw JSON
- No stack traces in any response

## Database

- Migration tool: Flyway (not Liquibase) — SQL-native, zero config with Spring Boot
- Migration location: `src/main/resources/db/migration/`
- Naming: `V{n}__{description}.sql` (double underscore mandatory)
- Hibernate `ddl-auto: validate` — Flyway owns schema, Hibernate NEVER creates tables

## HTTP Client

- `RestClient` (Spring Boot 3.2+ synchronous client) — NOT `RestTemplate` (maintenance mode), NOT `WebClient` (requires reactive stack)

## WebSocket

- Native WebSocket (NOT STOMP over SockJS) — one-way broadcast BE → FE, simpler, no extra libraries

## Caching

- Spring `@Cacheable` + Caffeine — no manual `ConcurrentHashMap`. All cache use `@Cacheable` annotation.

## Infrastructure

- Deploy target: Oracle A1 ARM (always-free) — always-on, no cold start
- Reverse proxy: Nginx (SSL termination, WebSocket upgrade, rate limiting)
- CI/CD: GitHub Actions → SSH deploy to Oracle A1
- Database: PostgreSQL co-located on Oracle A1 (localhost only)

## Config-Driven Demo App Registration

This section documents the config-driven registration workflow for demo apps (Story 3.7).

### Adding a New Demo App

1. Edit `src/main/resources/showcase.yml`
2. Add a new entry under `apps:`:

```yaml
apps:
  - id: my-app
    name: "My Application"
    healthEndpoint: "https://myapp.example.com/health"
    pollIntervalSeconds: 60  # optional, defaults to 60
```

3. Commit and redeploy the BE — polling starts automatically on next `@Scheduled` tick

### Removing a Demo App

1. Edit `src/main/resources/showcase.yml`
2. Remove or comment out the app entry
3. Commit and redeploy — polling stops for that app
4. Historical metrics remain in the database but are no longer updated or broadcast via WebSocket

### Config Validation

- Spring Boot uses `spring.config.import: optional:classpath:showcase.yml` to load the config
- If `showcase.yml` contains invalid YAML syntax, the application fails to start with a clear error
- The `DemoAppRegistry` uses `@ConfigurationProperties(prefix = "")` to bind the root-level `apps` list
- Unit test `DemoAppRegistryTest.getApps_bindingProperties_correctlyMapsFields()` validates the binding works correctly (runs without Docker)

### Polling Behavior

- `MetricsAggregationService.pollAll()` runs every 60 seconds (`@Scheduled(fixedDelay = 60000)`)
- It iterates over `registry.getApps()` — if the config has 0 apps, nothing is polled
- Each app's `/health` endpoint is called via `RestClient`
- Results are stored in `ProjectHealth` table and broadcast via WebSocket to FE clients

### Testing

- Unit test: `DemoAppRegistryTest` — validates config loads correctly
- Integration test: `MetricsAggregationServiceTest` — validates polling behavior (Story 3.1)

## Smoke Test CI Gate (Story 6.4.3)

### Stack Rationale

| Tool | Purpose | Rationale |
|---|---|---|
| Jest | Test runner | Industry standard, fast, great TypeScript support |
| `supertest` | HTTP assertions | Clean declarative API for REST endpoint testing |
| `ws` | WebSocket client | Most widely used Node.js WebSocket library |
| `dotenv` | Env loading | Loads `DEPLOYED_BE_URL` from CI environment |

**Why Node.js for a Java Spring Boot app?**

Smoke tests are HTTP/WebSocket **black-box** integration tests that run against the **deployed** Cloud Run endpoint — not against the Spring Boot JAR in-process. Node.js is the ideal runtime for this because:
- `supertest` and `ws` are purpose-built for HTTP/WS client scripting
- `npm` is natively supported in GitHub Actions without extra setup
- No Java test environment needed in the CI runner
- The BE remains the system-under-test; the test runtime is irrelevant to it

**Alternative rejected:** JUnit `@SpringBootTest` — would run inside the JAR (not against the deployed endpoint) and requires Java in the CI runner.

### CI Integration

Two workflows work together:

```
deploy.yml (on success)
  ├── uploads: deployed-url artifact
  └── runs: bash deploy/smoke-tests.sh  (curl health checks only — lightweight pass/fail)

smoke.yml (triggered by workflow_run from deploy.yml)
  ├── downloads: deployed-url artifact
  └── runs: npm run smoke:test  (Jest suite — primary CI gate)
```

- `smoke.yml` is the **primary CI gate** (triggered automatically after every deploy via `workflow_run`)
- `deploy/smoke-tests.sh` in `deploy.yml` runs lightweight curl health checks only; the full Jest smoke test suite runs exclusively in `smoke.yml`
- Both exit with non-zero on failure → GitHub Actions marks the workflow as failed → deploy flagged for investigation

### Smoke Test Suites

| Suite | File | What it tests |
|---|---|---|
| REST | `smoke/rest-smoke-tests.test.ts` | POST /api/v1/contact-submissions (201), GET /api/v1/admin/analytics (200, ≥1 submissions) |
| WebSocket | `smoke/websocket-metrics.test.ts` | Connect to /ws/metrics, receive project_health message with valid `status` within 5s |

### Required GitHub Secrets

| Secret | Purpose |
|---|---|
| `SMOKE_TEST_ADMIN_TOKEN` | JWT for the Owner account — enables the admin analytics assertion (AC-1b). Optional: if absent, only the contact POST is validated (AC-1a). |

### Cloud Run WebSocket Support

As of 2025, Cloud Run natively supports WebSocket (no additional configuration needed beyond HTTPS/WSS). If the WebSocket handshake fails, `smoke/websocket-metrics.test.ts` fails with a clear error message including the attempted URL.

### Timeout Configuration

| Layer | Value | Rationale |
|---|---|---|
| Jest `testTimeout` | 30,000ms | Per-test timeout; sufficient for HTTP round-trips and WS handshake |
| `smoke.yml` job `timeout-minutes` | 10 | GitHub Actions safety limit; smoke tests should complete in < 1 minute |
| WebSocket message wait | 5,000ms | AC-2 hard requirement; metrics pipeline must broadcast within 5s |
