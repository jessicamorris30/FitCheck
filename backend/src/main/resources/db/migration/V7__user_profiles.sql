-- User profiles (Phase 7): a username (can be used to log in instead of the email) and a name.
-- Accounts made before this have none of them; the app asks for them on the next login.

ALTER TABLE users
    -- Stored in lower case ("jessica.m"); letters, numbers, '.' and '_' only, never '@', so
    -- login can tell a username from an email by the '@'.
    ADD COLUMN username   VARCHAR(30),
    ADD COLUMN first_name VARCHAR(50),
    ADD COLUMN last_name  VARCHAR(50);

-- One account per username. lower() as well, in case anything ever stores mixed case.
-- (NULLs don't clash in a unique index, so existing accounts without a username are fine.)
CREATE UNIQUE INDEX ux_users_username ON users (lower(username));
