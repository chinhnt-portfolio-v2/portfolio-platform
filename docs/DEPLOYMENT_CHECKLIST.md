# Production Deployment Checklist

Use this checklist after deploying to Cloud Run to verify everything is working correctly.

## Pre-Deployment Checklist

Before pushing to main, verify:

- [ ] All environment variables are set in GitHub Secrets
- [ ] Neon PostgreSQL is provisioned and accessible
- [ ] Google OAuth2 credentials are created with correct redirect URIs
- [ ] JWT secret is 64+ characters
- [ ] CORS_ALLOWED_ORIGINS includes production frontend domain
- [ ] All tests pass locally (`./mvnw test`)
- [ ] Code is lint-free

---

## Post-Deployment Verification

After CI/CD completes, verify each endpoint:

### 1. Health Endpoint

```bash
curl https://portfolio-platform-1095331155372.asia-southeast1.run.app/actuator/health
```

**Expected:** `{"status":"UP"}`

### 2. API Docs

```bash
curl https://portfolio-platform-1095331155372.asia-southeast1.run.app/api-docs
```

**Expected:** HTML page with Swagger/OpenAPI documentation

### 3. Project Health (Demo Apps)

```bash
curl https://portfolio-platform-1095331155372.asia-southeast1.run.app/api/v1/project-health
```

**Expected:** JSON with demo app health statuses

### 4. Contact Form Submission

```bash
curl -X POST https://portfolio-platform-1095331155372.asia-southeast1.run.app/api/v1/contact \
  -H "Content-Type: application/json" \
  -d '{"name":"Test","email":"test@example.com","message":"Test message","honeypot":""}'
```

**Expected:** Success response with confirmation

### 5. OAuth2 Login Flow (if configured)

1. Visit: `https://portfolio-platform-1095331155372.asia-southeast1.run.app/api/v1/auth/oauth2/login/google`
2. Redirects to Google login
3. After authentication, redirects back with JWT tokens

**Expected:** Successful authentication and token generation

---

## Automated Smoke Tests

The CI/CD pipeline runs `deploy/smoke-tests.sh` automatically:

```bash
./deploy/smoke-tests.sh
```

This verifies:
- `/actuator/health`
- `/api/v1/project-health`

---

### 6. Admin Showcase Apps (Owner Only)

```bash
# Requires Owner JWT token
curl https://portfolio-platform-1095331155372.asia-southeast1.run.app/api/v1/admin/showcase/apps \
  -H "Authorization: Bearer <owner-jwt>"
```

**Expected:** `{"apps":[{"slug":"wallet-app","name":"Wallet App",...}]}`

---

## Managing Demo App Health Polling

Demo apps are registered in `src/main/resources/showcase.yml`. Edit this file to add or remove apps:

```yaml
apps:
  - slug: wallet-app
    name: "Wallet App"
    healthEndpoint: "https://wallet.chinh.dev/health"
    demoUrl: "https://wallet.chinh.dev"
```

### Adding a new demo app

1. Edit `showcase.yml`, add a new entry with `slug`, `name`, `healthEndpoint`, and optional `demoUrl`
2. Commit and push to `main`
3. CI/CD deploys automatically — BE restarts and begins polling the new app

### Removing a demo app

1. Remove the entry from `showcase.yml`
2. Commit and push — orphan `project_health` records are automatically hidden after 7 days of staleness (Story 3.5)

### Caveats

- **Fail-fast:** Malformed YAML → application refuses to start. Check YAML syntax before pushing.
- **Restart required:** Changes to `showcase.yml` take effect on BE restart (next deploy or manual restart).
- **Slug alignment:** `slug` values in `showcase.yml` MUST match `slug` values in `portfolio-fe/src/config/projects.ts` for the same apps.
- **dev profile:** Run with `SPRING_PROFILES_ACTIVE=dev` for Neon dev database + DEBUG logging.

### Health Check Fails

- Check Cloud Run logs in GCP Console
- Verify database credentials are correct
- Ensure Neon PostgreSQL is accessible from Cloud Run (check Neon IP allowlist)

### 502 Bad Gateway

- Cloud Run may still be starting up
- Check container startup logs
- Verify all required environment variables are set

### OAuth2 Not Working

- Verify GOOGLE_CLIENT_ID and GOOGLE_CLIENT_SECRET are set
- Check redirect URIs in Google Cloud Console match exactly
- Ensure CORS_ALLOWED_ORIGINS includes your frontend domain

### CORS Errors

- Verify CORS_ALLOWED_ORIGINS environment variable
- Check exact domain match (no trailing slashes)
- Ensure frontend is using HTTPS

---

## Rollback Procedure

If deployment fails:

1. Go to GitHub Actions
2. Find the failed workflow run
3. Click on the deployment step
4. Use GCP Console to roll back to previous revision

Or via gcloud CLI:

```bash
gcloud run revisions list --service portfolio-platform
gcloud run traffic update portfolio-platform --to-revision=REVISION_NAME
```
