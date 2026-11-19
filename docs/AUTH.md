# Authentication

FitCheck uses a hybrid scheme: **short-lived JWT access tokens** plus **rotating refresh tokens in HttpOnly cookies**.

| Token | Lifetime | Where it lives | Sent how |
|---|---|---|---|
| Access token (JWT, HS256) | 15 min (`JWT_ACCESS_TOKEN_TTL`) | JavaScript memory only (`api/client.ts`) | `Authorization: Bearer ...` header |
| Refresh token (opaque, 256-bit random) | 14 days (`JWT_REFRESH_TOKEN_TTL`) | HttpOnly cookie `fitcheck_refresh` | Sent by the browser automatically, only to `/api/auth/*` |

Why both:
- Access tokens are never in `localStorage`, so an XSS bug can't steal a long-lived credential.
- The refresh cookie is HttpOnly, so JavaScript can't read it. Its path is `/api/auth`, so ordinary API calls never carry it.
- Short access-token lifetimes limit the damage if one leaks.

## Cookie attributes

```
Set-Cookie: fitcheck_refresh=<token>; HttpOnly; Secure; SameSite=Strict; Path=/api/auth; Max-Age=1209600
```

`Secure` is turned off only in the `dev` profile, because the local dev server uses plain http.

## Flows

**Register / login**
1. Validate credentials. Passwords are hashed with BCrypt.
2. Issue a JWT access token (subject = user ID, `exp` = now + 15 min).
3. Create a refresh token: random 32 bytes, base64url. Store `SHA-256(token)` with a new `family_id` and the expiry. Set the cookie.
4. Return `AuthResponse { accessToken, expiresIn, user }`.

**Refresh (rotation)**
1. Read the cookie, hash it, and look up the row.
2. If the token is missing or expired, return `401` and clear the cookie.
3. **If it has already been revoked (reuse detected)**, revoke every token in the `family_id`, return `401` and clear the cookie. Someone replayed an old token, so the whole session is ended.
4. Otherwise, revoke the current row, create a new token in the same family (`replaced_by` points to it), set the new cookie and return a new access token.

**Logout**: revoke the family, clear the cookie (`Max-Age=0`) and return `204`. The frontend drops its in-memory access token.

**Page load**: the SPA calls `POST /api/auth/refresh` once. If it succeeds, the user is signed in without typing their password again.

**401 during use**: the axios interceptor runs one refresh at a time, even if several requests fail together, then retries the original request. If the refresh fails, the user is sent to `/login`.

## CSRF

The refresh endpoint is the only one that relies on a cookie. It's protected by `SameSite=Strict`, by the narrow cookie path, and by the fact that it only issues a token to the caller and doesn't change data. All other endpoints use the bearer header, which browsers never attach automatically, so Spring's CSRF protection is disabled for this stateless API.

## Implementation map

| Concern | Class |
|---|---|
| Filter chain, password encoder | `config/SecurityConfig` |
| JWT create/validate (JJWT) | `auth/JwtService` |
| Bearer header → SecurityContext | `auth/JwtAuthenticationFilter` |
| Refresh token issue/rotate/revoke | `auth/RefreshTokenService`, `auth/RefreshToken`, `auth/RefreshTokenRepository` |
| Endpoints and cookie handling | `auth/AuthController`, `auth/AuthService` |
| Settings | `config/JwtProperties` (`fitcheck.jwt.*`) |

## Later hardening

- Rate-limit `/auth/login` and `/auth/register`.
- Scheduled cleanup of expired refresh tokens.
- Email verification and password reset.
