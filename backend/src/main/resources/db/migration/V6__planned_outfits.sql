-- Outfit calendar: saved outfits put on specific days ("wear this on Friday").
-- A day can have several outfits (e.g. work + gym), shown in the order they were added.
-- Past days stay, so the calendar doubles as a "what I wore" history.

CREATE TABLE planned_outfits (
    id         UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    user_id    UUID        NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    -- Deleting an outfit removes it from the calendar too.
    outfit_id  UUID        NOT NULL REFERENCES outfits (id) ON DELETE CASCADE,
    -- A plain calendar day, no time or time zone: "Friday" stays Friday wherever you are.
    day        DATE        NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    -- The same outfit twice on one day would just be a duplicate.
    CONSTRAINT uq_planned_outfits_outfit_day UNIQUE (outfit_id, day)
);

-- "Show me this user's week/month": the calendar's one main query.
CREATE INDEX ix_planned_outfits_user_day ON planned_outfits (user_id, day);
