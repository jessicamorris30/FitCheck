# AI integration (Claude API)

In plan mode, a user can ask for outfit ideas from their own wardrobe by describing an aesthetic in text, by uploading an inspiration image, or both.

## Principles

- **Backend only.** The browser calls `POST /api/ai/outfit-suggestions`, and only `OutfitSuggestionService` talks to Claude. `ANTHROPIC_API_KEY` never reaches the frontend.
- **Suggest only real items.** Claude may only choose from the clothing item IDs we send. The backend checks every returned ID against the user's wardrobe before responding.
- **Structured output.** We ask for JSON that matches the `OutfitSuggestionResponse` shape, not free text that we'd have to parse.

## SDK and model

- Dependency: `com.anthropic:anthropic-java` (version set in `backend/pom.xml`).
- Client: `AnthropicOkHttpClient`, created once as a bean in `config/AnthropicConfig`.
- Model: `claude-opus-5-5` by default, configurable with `ANTHROPIC_MODEL` / `fitcheck.ai.model`.
- Thinking is adaptive on this model. Set effort explicitly (`fitcheck.ai.effort`, default `medium`). Use `max_tokens` of about 16000 for non-streaming calls.

## Request design

1. **System prompt:** FitCheck's stylist role and rules. Use only the provided item IDs, propose up to N outfits, each a coherent combination (for example top + bottom + shoes, or dress + shoes), and explain the reasoning briefly.
2. **Wardrobe context:** a compact list of the user's items, `{ id, name, category, color, tags }`. Keep it in a stable order so prompt caching works across repeated requests. Optionally add small thumbnails as image blocks if metadata alone isn't enough.
3. **User turn:**
   - the aesthetic prompt text, if given
   - the inspiration image as a base64 `image` content block, if given (jpeg/png/webp/gif)
4. **Output format:** `output_config.format` with a JSON schema for `{ suggestions: [{ title, itemIds[], reasoning }] }`.

## Response handling

1. Check `stop_reason` before reading content:
   - `end_turn`: parse the structured JSON.
   - `refusal`: return `422` with a friendly message. Never show raw model output.
   - `max_tokens`: treat as a failure (log it, return `503`) or retry with a higher limit.
2. Drop any suggestion that contains an ID not in the user's wardrobe.
3. Map SDK exceptions (rate limit, overloaded, network) to `503` and log them. The SDK already retries some transient errors.

## Cost and latency

- Send metadata, not full images, for the wardrobe whenever possible.
- Cache the stable prefix (system prompt + wardrobe list) with prompt caching.
- Consider per-user rate limits on the AI endpoint.

## Privacy

Inspiration images and wardrobe metadata are sent to Anthropic only when the user explicitly asks for suggestions. Say this in the UI and in the privacy policy.
