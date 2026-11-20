-- One row per AI stylist request that reached Claude (Phase 4, step 4.5).
-- Used to enforce the daily limit per user (fitcheck.ai.daily-limit) and to show "n of 20 left today".
CREATE TABLE ai_usage (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
-- "How many requests did this user make since midnight?" is answered from this index alone.
CREATE INDEX ix_ai_usage_user_created ON ai_usage (user_id, created_at);
