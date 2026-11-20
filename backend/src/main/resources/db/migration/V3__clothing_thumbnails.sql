-- Small JPEG copies of clothing photos (Phase 4, step 4.1), used by the wardrobe grid and the AI stylist.
-- NULL until the thumbnail has been made: new uploads get one immediately, older items are filled in
-- in the background at startup (see ThumbnailBackfill).
ALTER TABLE clothing_items ADD COLUMN thumbnail_blob_name VARCHAR(255) UNIQUE;
