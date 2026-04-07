# Google OAuth2 Setup Guide

This document describes how to set up Google OAuth2 credentials for the portfolio platform.

## Prerequisites

- A Google Cloud Platform (GCP) account
- A project created in Google Cloud Console

---

## Step 1: Create OAuth 2.0 Credentials

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project
3. Navigate to **APIs & Services** > **Credentials**
4. Click **Create Credentials** > **OAuth client ID**
5. Select **Web application** as the application type
6. Fill in the following:

   | Field | Value |
   |-------|-------|
   | Name | Portfolio Platform - Production |

---

## Step 2: Configure Authorized Origins and Redirect URIs

### For Local Development

Add to **Authorized JavaScript origins**:
```
http://localhost:5173
http://localhost:8080
```

Add to **Authorized redirect URIs**:
```
http://localhost:8080/api/v1/auth/oauth2/callback/google
```

### For Production (Vercel)

Add to **Authorized JavaScript origins**:
```
https://wallet.chinhnt.xyz
https://vault.chinhnt.xyz
https://ledger.chinhnt.xyz
https://codebin.chinhnt.xyz
https://portfolio.chinhnt.xyz
```

Add to **Authorized redirect URIs**:
```
https://portfolio-platform-1095331155372.asia-southeast1.run.app/api/v1/auth/oauth2/callback/google
```

> **Active flow:** Frontends call `/api/v1/auth/oauth2/login/google` (custom controller) → backend builds Google auth URL with `redirect_uri=.../callback/google` → Google redirects to callback → tokens sent to frontend.
> **Legacy flow:** Spring Security default `/oauth2/authorization/google` with `redirect_uri=.../login/oauth2/code/google` is wired but NOT used by frontends.

> **Note:** The Cloud Run URL is shown in the GCP Cloud Run console after first deployment.

---

## Step 3: Get Credentials

After creating the OAuth client, you will see:
- **Client ID** (starts with `.apps.googleusercontent.com`)
- **Client Secret**

Copy these values.

---

## Step 4: Set Environment Variables

### Local Development

```bash
export GOOGLE_CLIENT_ID="your-client-id.apps.googleusercontent.com"
export GOOGLE_CLIENT_SECRET="your-client-secret"
```

### Production (GitHub Secrets)

Add to GitHub repo → Settings → Secrets and variables → Actions:

| Secret | Value |
|--------|-------|
| `GOOGLE_CLIENT_ID` | Your OAuth client ID |
| `GOOGLE_CLIENT_SECRET` | Your OAuth client secret |

The CI/CD workflow (`.github/workflows/deploy.yml`) will inject these into Cloud Run.

---

## Step 5: Configure CORS

Set the CORS origins environment variable:

| Environment | Value |
|------------|-------|
| Development | `http://localhost:5173` |
| Production | `https://portfolio.chinhnt.xyz` |

---

## Verification

After setup, test the OAuth2 flow:

1. Deploy the application with valid credentials
2. Visit `/api/v1/auth/oauth2/login/google`
3. You should be redirected to Google login
4. After authenticating, you should receive JWT tokens
5. User is redirected back to the frontend

---

## Troubleshooting

### Invalid Client ID

If you see "invalid_client" error:
- Verify `GOOGLE_CLIENT_ID` is set correctly
- Check that the client ID matches the one in Google Cloud Console

### Redirect URI Mismatch

If you see "redirect_uri_mismatch":
- Verify the redirect URI in Google Cloud Console matches exactly: `https://portfolio-platform-1095331155372.asia-southeast1.run.app/api/v1/auth/oauth2/callback/google`
- All frontends MUST use the custom controller: `/api/v1/auth/oauth2/login/google` (NOT `/oauth2/authorization/google`)
- Ensure all app domains (`wallet`, `vault`, `ledger`, `codebin`) are in `ALLOWED_REDIRECT_DOMAINS` in `OAuth2Controller.java` and `OAuth2AuthenticationSuccessHandler.java`

### CORS Errors

If you see CORS errors:
- Verify `CORS_ALLOWED_ORIGINS` includes your frontend domain
- Check that the origin matches exactly (no trailing slashes)

---

## Environment Variable Reference

| Variable | Required | Description |
|----------|----------|-------------|
| `GOOGLE_CLIENT_ID` | Yes | OAuth2 client ID |
| `GOOGLE_CLIENT_SECRET` | Yes | OAuth2 client secret |
| `CORS_ALLOWED_ORIGINS` | Yes | Frontend domains (comma-separated) |
