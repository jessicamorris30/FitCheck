-- "My style" (Phase 7): one style per user, shown to the AI stylist with every request.

CREATE TABLE user_styles (
    user_id                UUID PRIMARY KEY REFERENCES users (id) ON DELETE CASCADE,
    -- The user's own words about their style (optional).
    note                   TEXT,
    -- The user can switch the whole feature off without deleting anything.
    use_with_ai            BOOLEAN     NOT NULL DEFAULT TRUE,
    -- Claude's private notes on the style, written from the photos + note. NEVER sent to the browser.
    ai_description         TEXT,
    -- +1 on every change to the photos or note. The notes were written for described_version;
    -- if that's behind, they're out of date and get rewritten.
    version                INT         NOT NULL DEFAULT 0,
    described_version      INT,
    -- How many notes were written today (UTC), to cap the cost at a few per day.
    descriptions_day       DATE,
    descriptions_count     INT         NOT NULL DEFAULT 0,
    updated_at             TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE style_images (
    id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id             UUID         NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Files under users/<id>/style/ in Blob Storage (removed with the account).
    blob_name           VARCHAR(300) NOT NULL UNIQUE,
    thumbnail_blob_name VARCHAR(300) NOT NULL UNIQUE,
    created_at          TIMESTAMPTZ  NOT NULL DEFAULT now()
);
CREATE INDEX ix_style_images_user ON style_images (user_id, created_at);
