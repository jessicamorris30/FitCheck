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

## Phase 3: Plan mode (manual)
- [ ] `Outfit` / `OutfitItem` entities, service and controller
- [ ] Frontend: PlanPage with OutfitItemPicker and OutfitCanvas, save/list outfits

## Phase 4: AI suggestions
- [ ] `AnthropicConfig`, `OutfitSuggestionService`, `AiController`
- [ ] Text prompt, image prompt, structured output, item ID validation, refusal handling
- [ ] Frontend: AiPromptPanel and SuggestionList, apply a suggestion to the canvas

## Phase 5: Polish and deploy
- [ ] Error handling (`GlobalExceptionHandler`), validation messages, loading/empty states
- [ ] Rate limiting on auth and AI endpoints
- [ ] Azure deployment (App Service / Container Apps, Flexible Server Postgres, Blob Storage, Key Vault)
- [ ] CI: build and test backend and frontend on every push
