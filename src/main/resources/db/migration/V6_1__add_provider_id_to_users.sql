-- Add provider_id column to users table for OAuth2 provider linking
ALTER TABLE users ADD COLUMN IF NOT EXISTS provider_id VARCHAR(255);

-- Create unique constraint for provider + provider_id combination
-- (allows same provider_id across different providers)
CREATE UNIQUE INDEX IF NOT EXISTS idx_users_provider_provider_id
ON users(provider, provider_id)
WHERE provider_id IS NOT NULL;
