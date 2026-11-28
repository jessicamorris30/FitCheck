-- Light / dark mode (Phase 7): the user's choice, so it follows them to every device.
-- NULL = never chosen (the app then follows the device's own setting, like 'system').
ALTER TABLE users
    ADD COLUMN theme VARCHAR(10) CHECK (theme IN ('system', 'light', 'dark'));
