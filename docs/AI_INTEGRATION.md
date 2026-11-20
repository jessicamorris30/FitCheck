# AI stylist (Claude API)

In Plan mode, **✨ Ask the AI stylist** suggests 1–3 outfits made **only from the user's own clothes**, from a text description, an inspiration photo, or both. **Use this outfit** opens it in Plan step 2, where it can be adjusted and saved. The saved outfit remembers the prompt (`outfits.ai_prompt`) and shows a ✨ AI badge.

## Principles

- **Backend only.** The browser calls FitCheck's own `/api/ai/*`, and only `ClaudeStylistClient` talks to Claude. `ANTHROPIC_API_KEY` lives in `.env` locally and in Azure settings/Key Vault in production. It never reaches the browser or git.
- **Only real items.** Claude refers to clothes by item number. The backend maps the numbers back to ids and **drops anything that isn't the user's**, so clothes from an inspiration photo can never appear as owned.
- **Structured output.** The answer has a guaranteed JSON shape (`{suggestions: [{title, itemNumbers, reasoning}]}`), derived from Java records. No parsing free text.
- **Never on a test run.** Tests use `FakeStylistClient`. Real calls only happen in the app, or in the opt-in `@Tag("live")` test.

## How a request works

```
POST /api/ai/outfit-suggestions   (JSON {prompt}  or  multipart: prompt? + image?)
 1. Free checks: prompt and/or photo; prompt ≤ 500 chars; photo = real JPEG/PNG/WebP;
    wardrobe not empty                                                     → 400
 2. Guard rails (AiUsageService): email confirmed → else 403; < 20 today (UTC) → else 429; record 1 use
 3. Build one request (ClaudeStylistClient):
      system:  stylist rules (constant, so it can be cached)
      user:    "Item 1: <details>" + thumbnail, "Item 2: …" …  ← cache breakpoint
               [inspiration photo, shrunk to ≤1024px JPEG, EXIF/GPS removed, not stored]
               "What I'm going for: <prompt>"
      model claude-opus-5-5 · effort medium (explicit) · adaptive thinking · max_tokens 16000
      structured output · server-side refusal fallbacks ("default", beta server-side-fallback-2026-07-01)
 4. stop_reason: refusal → 422 (counts) · not end_turn → 503 (refunded) · end_turn → read typed answer
 5. Item numbers → ids → keep only the user's items; drop empty suggestions (none left → 503, refunded)
```

## Cost and speed (measured, demo wardrobe of 9 items)

| Request | Input tokens | Output | ≈ Cost | Time |
|---|---|---|---|---|
| Text, cache written | ~2,170 | ~330 | ~1.7¢ | ~8 s |
| Text, cache read | 2,138 cached + ~27 | ~290 | ~0.7¢ | ~8 s |
| Photo only | ~3,300 (photo ≈ 1,050) | ~410 | ~2.4¢ | ~10 s |

What keeps costs down:
- **Thumbnails** (≤320px, ~20 KB each) instead of full photos.
- **Prompt caching:** the wardrobe goes first, oldest first and in a stable order, so new items are appended and the prefix is reused for a few minutes.
- **Item numbers** instead of UUIDs.
- **A wardrobe cap** of 200 items.
- **A daily limit** of 20 requests per user.

## Privacy

The prompt, inspiration photo and wardrobe (item details plus small photos) are sent to Anthropic **only when the user presses Get suggestions**. The panel says so. The inspiration photo is re-encoded, which strips EXIF metadata including GPS location, and it is never stored. Claude keeps no memory between requests: each request is built from scratch.

## Code map

| Concern | Where |
|---|---|
| SDK calls, prompt, caching, structured output, stop reasons | `stylist/ClaudeStylistClient` |
| Interface + no-key fallback + test fake | `stylist/StylistClient`, `NotConfiguredStylistClient`, `FakeStylistClient` (tests) |
| Wardrobe gathering, photo prep, answer validation, refunds | `ai/OutfitSuggestionService` |
| Confirmed email + daily limit | `ai/AiUsageService`, `ai_usage` table (V4) |
| Settings | `config/AiProperties` (`fitcheck.ai.*`), `config/AnthropicConfig` |
| Endpoints | `ai/AiController`: `GET /status`, `GET /usage`, `POST /outfit-suggestions` |
| UI | `components/plan/AiPromptPanel`, `SuggestionList`; `PlanPage.applySuggestion` |

Live connection check (fraction of a cent):
`.\mvnw.cmd test "-Dtest=ClaudeConnectionLiveTest" "-Dtest.excludedGroups=none"`
