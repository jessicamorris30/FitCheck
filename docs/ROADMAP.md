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

## Later: Outfit calendar
- [ ] Put saved outfits on specific days ("wear this on Friday") with a simple week/month view

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

## Later
- [x] Two tabs refreshing at the same moment (e.g. opening an email link in a new tab) no longer trip
      refresh-token reuse detection: a just-replaced token works for 10 more seconds
- [ ] Decode large photos at reduced size (ImageIO subsampling) to cut upload memory (~50 MB per phone photo today)
- [ ] Faster cold starts (Spring AOT/CDS) or one always-on copy
- [ ] Managed identity for storage (user delegation SAS) and Key Vault for secrets
- [ ] CDN for photos
- [ ] HEIC (iPhone) photos, tag search, pagination for large wardrobes
