-- FitCheck initial schema. See docs/DATABASE.md.
-- Flyway applies this once; never edit it after it has run anywhere. Add V2__*.sql instead.

CREATE TABLE users (
    id            UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    email         VARCHAR(320) NOT NULL,
    password_hash VARCHAR(100) NOT NULL,
    created_at    TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at    TIMESTAMPTZ  NOT NULL DEFAULT now()
);
-- Emails are compared case-insensitively.
CREATE UNIQUE INDEX ux_users_email ON users (lower(email));

-- Rotating refresh tokens (docs/AUTH.md). Only a SHA-256 hash of the token is stored.
-- All tokens descended from one login share a family_id; reuse of a revoked token revokes the family.
CREATE TABLE refresh_tokens (
    id          UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id     UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    token_hash  CHAR(64)    NOT NULL UNIQUE,
    family_id   UUID        NOT NULL,
    expires_at  TIMESTAMPTZ NOT NULL,
    revoked_at  TIMESTAMPTZ,
    replaced_by UUID        REFERENCES refresh_tokens (id) ON DELETE SET NULL,
    created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX ix_refresh_tokens_user ON refresh_tokens (user_id);
CREATE INDEX ix_refresh_tokens_family ON refresh_tokens (family_id);

CREATE TABLE clothing_items (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(120) NOT NULL,
    category   VARCHAR(20)  NOT NULL
        CHECK (category IN ('TOP', 'BOTTOM', 'DRESS', 'OUTERWEAR', 'SHOES', 'ACCESSORY', 'OTHER')),
    color      VARCHAR(40),
    tags       TEXT[]       NOT NULL DEFAULT '{}',
    -- Name of the image blob in Azure Blob Storage (not a URL; URLs are short-lived SAS links).
    blob_name  VARCHAR(255) NOT NULL UNIQUE,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_clothing_items_user_category ON clothing_items (user_id, category);

CREATE TABLE outfits (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    name       VARCHAR(120) NOT NULL,
    notes      TEXT,
    -- Text/description of the AI prompt that produced this outfit, if any.
    ai_prompt  TEXT,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_outfits_user ON outfits (user_id);

CREATE TABLE outfit_items (
    outfit_id        UUID NOT NULL REFERENCES outfits (id) ON DELETE CASCADE,
    clothing_item_id UUID NOT NULL REFERENCES clothing_items (id) ON DELETE CASCADE,
    position         INT  NOT NULL DEFAULT 0,
    PRIMARY KEY (outfit_id, clothing_item_id)
);
CREATE INDEX ix_outfit_items_clothing ON outfit_items (clothing_item_id);
