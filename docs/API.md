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
| POST | `/auth/register` | `{ email, password }` | `201 AuthResponse` + sets refresh cookie |
| POST | `/auth/login` | `{ email, password }` | `200 AuthResponse` + sets refresh cookie |
| POST | `/auth/refresh` | none (uses the refresh cookie) | `200 AuthResponse` + rotates the refresh cookie |
| POST | `/auth/logout` | none | `204`, revokes the token family and clears the cookie |
| POST | `/auth/verify-email` | `{ token }` | `200 UserResponse`, or `400` if the link is invalid or expired |

`AuthResponse`:

```json
{ "accessToken": "eyJ...", "expiresIn": 900, "user": { "id": "uuid", "email": "a@b.com", "createdAt": "..." } }
```

## Users

| Method | Path | Response |
|---|---|---|
| GET | `/users/me` | `UserResponse` `{ id, email, emailVerified, createdAt }` |
| POST | `/users/me/verification-email` | `204` sent, `409` already verified, `429` too soon (60 s cooldown) |
| DELETE | `/users/me` | `204` (deletes the account, all items, outfits and blobs) |

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
| POST | `/outfits` | `{ name, notes?, itemIds[] }` | `201 OutfitResponse` |
| PUT | `/outfits/{id}` | `{ name, notes?, itemIds[] }` | `OutfitResponse` |
| DELETE | `/outfits/{id}` | none | `204` |

## AI

| Method | Path | Body | Response |
|---|---|---|---|
| POST | `/ai/outfit-suggestions` | multipart: `prompt` (text, optional), `image` (optional), `itemIds` (optional, repeatable) | `OutfitSuggestionResponse` |

At least one of `prompt` or `image` is required.

```json
{
  "suggestions": [
    { "title": "Rainy-day minimalist", "itemIds": ["uuid", "uuid", "uuid"], "reasoning": "..." }
  ]
}
```

Possible errors: `422` when the request is declined (for example, a refusal from Claude), `503` when the AI service is unavailable.
