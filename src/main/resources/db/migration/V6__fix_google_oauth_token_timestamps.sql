ALTER TABLE google_oauth_token ALTER COLUMN token_expiry TYPE TIMESTAMPTZ;
ALTER TABLE google_oauth_token ALTER COLUMN created_at TYPE TIMESTAMPTZ;
ALTER TABLE google_oauth_token ALTER COLUMN updated_at TYPE TIMESTAMPTZ;
