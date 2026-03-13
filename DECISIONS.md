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

1. Edit `src/main/resources/demo-apps.yml`
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

1. Edit `src/main/resources/demo-apps.yml`
2. Remove or comment out the app entry
3. Commit and redeploy — polling stops for that app
4. Historical metrics remain in the database but are no longer updated or broadcast via WebSocket

### Config Validation

- Spring Boot uses `spring.config.import: optional:classpath:demo-apps.yml` to load the config
- If `demo-apps.yml` contains invalid YAML syntax, the application fails to start with a clear error
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
