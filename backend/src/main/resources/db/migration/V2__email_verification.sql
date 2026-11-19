-- Email verification (Phase 1, step 8). See docs/AUTH.md#email-verification.

-- NULL = not verified yet. Accounts created before this migration start unverified.
ALTER TABLE users ADD COLUMN email_verified_at TIMESTAMPTZ;

-- One row per verification email sent. Like refresh tokens, only a SHA-256 hash is stored.
CREATE TABLE email_verification_tokens (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash CHAR(64)    NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_email_verification_tokens_user ON email_verification_tokens (user_id, created_at DESC);
