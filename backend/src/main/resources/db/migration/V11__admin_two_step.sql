-- 2-step login for admin (Phase 8.2): 6-digit codes from an authenticator app.

ALTER TABLE users
    -- The shared secret, once 2-step is on (Base32). Never sent to the browser after setup.
    ADD COLUMN totp_secret         VARCHAR(64),
    -- During setup: the secret shown as a QR code, until a working code proves the app has it.
    ADD COLUMN totp_pending_secret VARCHAR(64),
    ADD COLUMN totp_enabled_at     TIMESTAMPTZ,
    -- The 30-second step of the last accepted code: the same code can't be used twice.
    ADD COLUMN totp_last_step      BIGINT;

-- One-time backup codes, for when the phone is lost. Only their SHA-256 hash is stored.
CREATE TABLE admin_recovery_codes (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    code_hash  CHAR(64)    NOT NULL UNIQUE,
    used_at    TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_admin_recovery_codes_user ON admin_recovery_codes (user_id);
