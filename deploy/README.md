# Deploy Directory

This directory contains deployment artifacts for the Portfolio Platform backend.

## Files

| File | Description |
|------|-------------|
| `nginx/portfolio-v2.nginx.conf` | Nginx reverse proxy config (deployed by CI/CD) |
| `portfolio-platform.service` | systemd unit file for the Spring Boot service |
| `deploy.sh` | Manual deployment fallback script |
| `smoke-tests.sh` | Post-deploy health check script (NFR-R7) |

---

## One-Time Oracle A1 Bootstrap

These steps must be performed **once** via SSH before CI/CD can function.

### 1. Create deployment directory

```bash
sudo mkdir -p /opt/portfolio-platform
sudo chown ubuntu:ubuntu /opt/portfolio-platform
```

### 2. Install systemd service

```bash
# SCP the service file to the server first, then:
sudo cp /tmp/portfolio-platform.service /etc/systemd/system/portfolio-platform.service
sudo systemctl daemon-reload
sudo systemctl enable portfolio-platform
```

### 3. Create the environment file

Create `/opt/portfolio-platform/.env` with real values — **never commit this file**:

```bash
nano /opt/portfolio-platform/.env
```

Template:
```
SPRING_DATASOURCE_URL=jdbc:postgresql://localhost:5432/portfolio_v2
SPRING_DATASOURCE_USERNAME=portfolio_user
SPRING_DATASOURCE_PASSWORD=<secure-password>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_ID=<google-client-id>
SPRING_SECURITY_OAUTH2_CLIENT_REGISTRATION_GOOGLE_CLIENT_SECRET=<google-secret>
JWT_SECRET=<base64-encoded-256bit-secret>
```

### 4. Install smoke test script

```bash
cp /tmp/smoke-tests.sh /opt/portfolio-platform/smoke-tests.sh
chmod +x /opt/portfolio-platform/smoke-tests.sh
```

### 5. Initial manual deploy (first time only)

```bash
# From local machine — SCP JAR manually
scp target/portfolio-platform-0.0.1-SNAPSHOT.jar ubuntu@<ORACLE_HOST>:/opt/portfolio-platform/portfolio-platform.jar

# On Oracle A1
sudo systemctl start portfolio-platform
sudo systemctl status portfolio-platform
```

---

## GitHub Secrets Setup

Configure these secrets at: **GitHub repo → Settings → Secrets and variables → Actions**

| Secret | Value |
|--------|-------|
| `ORACLE_HOST` | IP or domain of Oracle A1 instance (e.g. `152.xx.xx.xx`) |
| `ORACLE_USER` | SSH username — `ubuntu` for Oracle Ubuntu images |
| `ORACLE_SSH_KEY` | Private SSH key (PEM format, full content including header/footer) |
| `ORACLE_HOST_KEY` | Oracle A1 host public key line (see below — prevents MITM) |

### Generate a dedicated deploy key

```bash
# On local machine
ssh-keygen -t ed25519 -C "github-actions-deploy" -f ~/.ssh/oracle_deploy_key -N ""

# Add public key to Oracle A1
cat ~/.ssh/oracle_deploy_key.pub | ssh ubuntu@<ORACLE_HOST> "cat >> ~/.ssh/authorized_keys"

# Copy private key content → paste into GitHub secret ORACLE_SSH_KEY
cat ~/.ssh/oracle_deploy_key
```

### Get Oracle A1 host key (for ORACLE_HOST_KEY secret)

```bash
# Scan Oracle A1 and capture its host key — paste full output into ORACLE_HOST_KEY secret
ssh-keyscan <ORACLE_HOST>
# Example output (copy the line matching your host):
# 152.xx.xx.xx ssh-ed25519 AAAAC3NzaC1lZDI1NTE5AAAA...
```

This prevents MITM attacks by trusting a pre-verified fingerprint instead of auto-accepting on first connect.

---

## Git Repository Note

The `.github/workflows/deploy.yml` workflow **must be at the root of the git repository** to be picked up by GitHub Actions. This deploy directory assumes `portfolio-platform/` is its own dedicated git repository (separate from any monorepo parent).

If you are working in a monorepo:
1. Move `.github/` to the monorepo root, and
2. Add `working-directory: portfolio-platform` to all Maven steps in the workflow.

---

## CI/CD Pipeline Overview

On every push to `main`:

1. **test job** — runs `mvn test` (all unit tests must pass)
2. **deploy job** (only if test passes):
   - Builds JAR via `mvn clean package -DskipTests`
   - SCPs JAR to `/tmp/portfolio-platform.jar` on Oracle A1
   - SCPs Nginx config to `/tmp/portfolio-v2.nginx.conf` on Oracle A1
   - Via SSH: moves Nginx config, reloads Nginx, restarts Spring Boot service
   - Runs `smoke-tests.sh` to verify deployment

Workflow file: `.github/workflows/deploy.yml`
