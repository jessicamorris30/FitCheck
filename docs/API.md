# REST API

Base path: `/api`. Request and response bodies are JSON unless marked multipart.
Every endpoint except `/api/auth/register`, `/api/auth/login` and `/api/auth/refresh` requires `Authorization: Bearer <accessToken>`.

Errors use one shape (`ApiError`):

```json
{ "status": 404, "error": "Not Found", "message": "Clothing item not found", "timestamp": "2026-11-18T12:00:00Z" }
```

> Status: this is the planned contract. Endpoints are not implemented yet.

## Auth

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/auth/register` | `{ email, password, username, firstName, lastName }` | `201 AuthResponse` + sets refresh cookie. `400` with `fieldErrors` (`username` if taken or reserved), `409` email taken |
| POST | `/auth/check-username` | `{ username }` | `200 { username, available, message }` (the live check at sign-up; 60/min per IP) |
| POST | `/auth/login` | `{ login, password }`: `login` is an email (contains `@`) or a username, any letter case (`email` is accepted as the field name too) | `200 AuthResponse` + sets refresh cookie. `401` wrong login/password, `429` after 5 wrong passwords for one account in 15 min (counted per account, whether email or username was typed) |
| POST | `/auth/refresh` | none (uses the refresh cookie) | `200 AuthResponse` + rotates the refresh cookie |
| POST | `/auth/logout` | none | `204`, revokes the token family and clears the cookie |
| POST | `/auth/verify-email` | `{ token }` | `200 UserResponse`, or `400` if the link is invalid or expired |
| POST | `/auth/forgot-password` | `{ email }` | always `204`, for every address; emails a reset link (valid 1 h, single-use) if the account exists |
| POST | `/auth/reset-password` | `{ token, newPassword }` | `204`, then log in; all sessions are revoked and the email counts as confirmed. `400` if the link is invalid, used or expired |
| POST | `/auth/change-password` | `{ currentPassword, newPassword }` (**login required**) | `200 AuthResponse` + a new refresh cookie for this device; every other session is revoked. `400` with `fieldErrors.currentPassword` / `newPassword` |

`AuthResponse`:

```json
{ "accessToken": "eyJ...", "expiresIn": 900, "user": UserResponse }
```

## Users

| Method | Path | Response |
|---|---|---|
| GET | `/users/me` | `UserResponse` `{ id, email, username, firstName, lastName, profileComplete, theme, emailVerified, createdAt }`. `profileComplete` is false for accounts from before usernames existed until they set one |
| PUT | `/users/me/profile` | Body `{ username, firstName, lastName }` → `UserResponse`. `400` with `fieldErrors` (`username` if taken or reserved) |
| PUT | `/users/me/preferences` | Body `{ theme }` (`system`, `light` or `dark`) → `UserResponse`; saved on the account so it follows the user to every device |
| DELETE | `/users/me` | Body `{ password }`. `204`: the account and everything in it are deleted (database rows by cascade; files under `users/<id>/` after commit) and the refresh cookie is cleared. `400` with `fieldErrors.password` if the password is wrong |
| POST | `/users/me/verification-email` | `204` sent, `409` already verified, `429` too soon (60 s cooldown) |

## My style (Phase 7)

The logged-in user's own style, used by the AI stylist. Every answer is the whole style as the browser may see it. Claude's private notes on the style (written in the background from the photos and note) are **never** included.

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/users/me/style` | none | `StyleResponse` |
| PUT | `/users/me/style` | `{ note?, useWithAi }` (note at most 1000 characters; blank = none) | `StyleResponse` |
| POST | `/users/me/style/photos` | multipart `image` (JPEG/PNG/WebP, at most 10 MB) | `201 StyleResponse`; `400` not an image or already 5 photos. Stored as a 1024 px JPEG (no metadata) plus a 320 px thumbnail |
| DELETE | `/users/me/style/photos/{id}` | none | `StyleResponse`; `404` if not yours |

`StyleResponse`: `{ note, useWithAi, photos: [{ id, imageUrl, thumbnailUrl, addedAt }], maxPhotos: 5, updatedAt }`.

## Clothing

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/clothing?category=TOP` | none | `ClothingItemResponse[]` |
| GET | `/clothing/{id}` | none | `ClothingItemResponse` |
| POST | `/clothing` | multipart: `image` (jpeg/png/webp, ≤ 10 MB), `metadata` (JSON `ClothingItemRequest`) | `201 ClothingItemResponse` |
| PUT | `/clothing/{id}` | `ClothingItemRequest` | `ClothingItemResponse` |
| DELETE | `/clothing/{id}` | none | `204` (also deletes the blob) |

`ClothingItemRequest`: `{ name, category, color?, tags? }`. Categories: `TOP, BOTTOM, DRESS, OUTERWEAR, SHOES, ACCESSORY, OTHER`.

`ClothingItemResponse`: `{ id, name, category, color, tags, imageUrl, createdAt }`. `imageUrl` is a short-lived SAS URL; don't store it.

## Outfits

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/outfits` | none | `OutfitResponse[]` |
| GET | `/outfits/{id}` | none | `OutfitResponse` |
| POST | `/outfits` | `{ name, notes?, itemIds[], aiPrompt? }` | `201 OutfitResponse` |
| PUT | `/outfits/{id}` | `{ name, notes?, itemIds[], aiPrompt? }` (replaces everything) | `OutfitResponse` |
| DELETE | `/outfits/{id}` | none | `204` (clothes are not affected) |

`OutfitResponse`: `{ id, name, notes, aiPrompt, items: ClothingItemResponse[], createdAt, updatedAt }`. `aiPrompt` is set when the outfit came from the AI stylist.

## Calendar

Saved outfits put on days. Dates are plain days, `"YYYY-MM-DD"`, with no time zone. A day can hold several different outfits (at most 10), shown in the order added. Only your own outfits; dates 2000-2100.

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/calendar?from=2026-11-30&to=2026-12-06` | none | `PlannedOutfitResponse[]` by day, then order added (both days included, at most 62 days) |
| GET | `/calendar/outfits/{outfitId}` | none | `[{ id, date }]`: every day that outfit is on, earliest first (`404` if not yours) |
| POST | `/calendar` | `{ date, outfitId }` | `201 PlannedOutfitResponse` (`404` outfit not yours, `409` already on that day, `400` day full or bad date) |
| DELETE | `/calendar/{id}` | none | `204` (takes it off the day; the outfit stays) |

`PlannedOutfitResponse`: `{ id, date, outfit: OutfitResponse }`. `id` is the calendar entry's own id. Deleting an outfit (or the account) removes its calendar entries.

## Admin (Phase 8)

Only for admin accounts: the email must be in the server's `ADMIN_EMAILS` setting **and** confirmed (and the account not frozen). For everyone else every `/admin/**` path answers `404 No endpoint ...`, exactly like a page that doesn't exist; not logged in → `401` like the rest of the API. Checked against the database on every request.

| Method | Path | Response |
|---|---|---|
| GET | `/admin/me` | `{ id, email, admin: true }` |

**2-step login (8.2).** Every admin endpoint except `/admin/2fa/*` also needs the "code entered" pass: an HttpOnly cookie `fitcheck_admin` (Path `/api/admin`, SameSite=Strict, 8 hours, for one admin; useless without that admin's login token). Without it: `403` "Set up two-step verification..." or "Enter the code from your authenticator app...". Logging out clears it.

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/admin/2fa/status` | none | `{ enabled, unlocked, recoveryCodesLeft }` |
| POST | `/admin/2fa/setup` | none | `{ secret, otpauthUri }` (for the QR code); `409` if already on |
| POST | `/admin/2fa/enable` | `{ code }` | `{ recoveryCodes: [10 × "ABCDE-FGHJK"] }` shown once + the pass cookie |
| POST | `/admin/2fa/verify` | `{ code }` (6 digits, or a backup code) | `204` + the pass cookie |
| POST | `/admin/2fa/disable` | `{ password, code }` | `204`, pass cleared, backup codes deleted |
| POST | `/admin/2fa/recovery-codes` | `{ code }` | new backup codes (old ones stop working) |

Codes: standard TOTP (RFC 6238; 6 digits, 30 s, ±1 step), each code usable once. Wrong code → `400 fieldErrors.code`; 5 wrong codes in 15 min → `429`.

Frozen accounts: login with the right password → `403` "This account has been suspended..." (a wrong password still gets the normal `401`, so guessers learn nothing); session refresh → `401`; any existing access token stops working at once.

`UserResponse.admin` is `true` for the admin (to show the Admin link). `GET /ai/usage` has `unlimited: true` for admins: no daily limit (requests are still counted).

## AI stylist

| Method | Path | Body | Response |
|---|---|---|---|
| GET | `/ai/status` | none | `{ configured, model }` (free) |
| GET | `/ai/usage` | none | `{ used, limit, remaining, resetsAt }` (free) |
| POST | `/ai/outfit-suggestions` | JSON `{ prompt }`, **or** multipart `prompt?` + `image?` (at least one) | `SuggestionsResponse` (~10–40 s) |

```json
{
  "suggestions": [
    { "title": "Classic Office Trench Look", "reasoning": "…", "items": [ /* ClothingItemResponse, in order */ ] }
  ]
}
```

Errors:
- `400`: no prompt or photo, prompt over 500 characters, the photo isn't an image, or the wardrobe is empty
- `403`: email not confirmed
- `429`: 20 requests today (UTC)
- `422`: Claude declined the request
- `503`: AI not configured or unavailable (that request isn't counted)

See [AI_INTEGRATION.md](AI_INTEGRATION.md).
