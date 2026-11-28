-- Admin (Phase 8). Who is admin is NOT stored here: it comes from the ADMIN_EMAILS setting on the
-- server (kept out of the public repo), checked on every admin request.

ALTER TABLE users
    -- Frozen (suspended) accounts can't log in or use the app. NULL = active.
    ADD COLUMN frozen_at      TIMESTAMPTZ,
    ADD COLUMN frozen_reason  TEXT,
    -- For the admin user list: last password login, and last time the app was used at all
    -- (a login or a session refresh, which happens every ~15 minutes while someone is active).
    ADD COLUMN last_login_at  TIMESTAMPTZ,
    ADD COLUMN last_active_at TIMESTAMPTZ;

-- Every admin action, kept even if the admin's or the target's account is later deleted
-- (hence the copied emails and no foreign key on the target).
CREATE TABLE admin_audit_log (
    id             UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    admin_id       UUID         REFERENCES users (id) ON DELETE SET NULL,
    admin_email    VARCHAR(320) NOT NULL,
    action         VARCHAR(40)  NOT NULL,
    target_user_id UUID,
    target_email   VARCHAR(320),
    details        TEXT,
    created_at     TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_admin_audit_created ON admin_audit_log (created_at DESC);
CREATE INDEX ix_admin_audit_target ON admin_audit_log (target_user_id);
