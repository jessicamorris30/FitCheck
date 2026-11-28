# Roadmap

## Phase 0: Project skeleton ✅
- [x] Monorepo layout, docs, Docker Compose (Postgres + Azurite)
- [x] Spring Boot backend generated (Initializr), packages and placeholders
- [x] React + TS + Tailwind frontend generated (Vite), routes and placeholders
- [x] Initial database schema (V1 migration)

## Phase 1: Authentication ✅
- [x] `JwtProperties`, `JwtService`, `JwtAuthenticationFilter`, `SecurityConfig`, `CorsConfig`
- [x] `User` and `RefreshToken` entities and repositories
- [x] Register, login, refresh (rotation + reuse detection) and logout endpoints
- [x] Frontend: Login/Register pages, `AuthProvider`, silent refresh, 401 interceptor, `ProtectedRoute` redirect
- [x] Tests for token rotation and reuse detection
- [x] Email verification on sign-up (Mailpit locally, SMTP provider in production)

## Phase 2: Wardrobe ✅
- [x] `AzureStorageConfig`, `BlobStorageService` (upload, delete, SAS URLs)
- [x] `ClothingItem` entity, service and controller (multipart upload, list/filter, update, delete)
- [x] Frontend: WardrobePage, ClothingGrid/Card, CategoryFilter, UploadClothingModal, ClothingDetailPage
- [ ] Later: thumbnails, HEIC (iPhone) photos, tag search, pagination for large wardrobes

## Phase 3: Plan mode (manual) ✅
- [x] `Outfit` / `OutfitItem` entities, service and controller (create, list, view, edit, delete)
- [x] Frontend: two-step Plan flow (choose clothes → arrange, name, save) and an Outfits tab to browse, open, edit and delete outfits

## Phase 4: AI stylist ✅
- [x] Thumbnails for every clothing photo (also speeds up grids)
- [x] `AnthropicConfig`, `StylistClient` (Claude via the official Java SDK, fake in tests), `OutfitSuggestionService`, `AiController`
- [x] Text prompt, inspiration photo, structured output, prompt caching, item id validation, refusal handling and fallbacks
- [x] Guard rails: confirmed email required, 20 requests per user per day, refunds when the AI fails
- [x] Frontend: Ask the AI stylist panel and suggestion cards; Use this outfit, then save with a ✨ AI badge

## Phase 6: Outfit calendar ✅ (v1.2.0)
Put saved outfits on specific days ("wear this on Friday"); several per day; past days double as a
"what I wore" history.
- [x] 6.1 Backend: `planned_outfits` table (V6), `CalendarService`, `/api/calendar` (list a range, plan, remove)
- [x] 6.2 Calendar page: week view (Mon–Sun, week kept in the address), add/remove outfits, nav link
- [x] 6.3 Month view (tap a day to open its week); "On your calendar" on outfit pages: Plan this
      outfit, upcoming days, last worn (`GET /api/calendar/outfits/{id}`)
- [x] 6.4 Polish (compact empty days on phones, empty-week hint, docs), release v1.2

## Phase 5: Polish and launch ✅ (v1.0.0, live at https://fitcheckwardrobe.com)
- [x] Account: change password, forgot/reset password, delete account (with all photos)
- [x] Rate limits on auth endpoints, per-account login lock, frontend polish
- [x] CI on every push (GitHub Actions)
- [x] Production profile; one container serves the website and the API
- [x] Docker image; Azure infrastructure as code (Bicep): Container Apps, PostgreSQL, Blob Storage, email
- [x] Email from fitcheckwardrobe.com (SPF, DKIM, DMARC); custom domain with HTTPS
- [x] Automatic deploys on green pushes to main (GitHub OIDC, no stored passwords)
- [x] Go-live check, including AI prompt-injection tests and a rate-limit spoofing fix
- [x] Monitoring: alerts, budget, saved log searches; [DEPLOYMENT.md](DEPLOYMENT.md)

## Phase 7: Profiles, your style, light/dark mode ✅ (v1.3.0)
- [x] 7.1 Accounts (backend): username + first/last name (V7), sign-up requires them, log in with email
      or username (one wrong-password lock per account), `PUT /users/me/profile`, `check-username`
- [x] 7.2 Accounts (screens): sign-up fields with live username check, "Email or username" login,
      "Finish your profile" for older accounts, edit on Account page, @username in the top bar
- [x] 7.3 Your style (backend): up to 5 style photos + note (V8, `/users/me/style`); Claude writes private
      style notes in the background (one call per burst of changes, max 10/day, confirmed emails only)
- [x] 7.4 Your style (screen): "My style" on the Account page, on/off switch for the AI stylist
- [x] 7.5 The AI stylist uses your style in every request (Plan-mode input still wins for that request)
- [x] 7.6 Light / Dark / System theme, saved to the account (V9) and remembered per device; dark mode
      swaps the colour palette (index.css), no white flash (public/theme-init.js)
- [x] 7.7 Polish and release v1.3: "Using your saved style" hint in the stylist panel, privacy note
      mentions the style notes

## Later
- [x] Two tabs refreshing at the same moment (e.g. opening an email link in a new tab) no longer trip
      refresh-token reuse detection: a just-replaced token works for 10 more seconds
- [x] Lighter photo uploads: rotate after shrinking (~95 → ~48 MB per phone photo), at most 2 processed
      at once, images over 100 megapixels refused. (Decoding at reduced size was tried and rejected:
      Java can only skip pixels, which made striped and knit fabrics change colour in thumbnails.)
- [x] Faster cold starts (v1.1): Java 25 startup cache built into the image (app start 85 s → 34 s
      under the same CPU limit locally) and health checks every 3 s instead of 10-15 s
- [ ] Managed identity for storage (user delegation SAS) and Key Vault for secrets
- [ ] CDN for photos
- [ ] HEIC (iPhone) photos, tag search, pagination for large wardrobes
