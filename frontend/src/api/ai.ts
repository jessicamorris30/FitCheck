import type { AiStatus, AiUsage, OutfitSuggestionRequest, OutfitSuggestionResponse } from '../types'
import { api } from './client'

// The AI stylist (docs/AI_INTEGRATION.md). Claude is only ever called by the backend: the browser
// never sees the API key, it just asks FitCheck's own API.

/** Is the AI stylist switched on? Free (doesn't call Claude). */
export async function getAiStatus(): Promise<AiStatus> {
  const { data } = await api.get<AiStatus>('/ai/status')
  return data
}

/** How many AI requests are left today. Free. */
export async function getAiUsage(): Promise<AiUsage> {
  const { data } = await api.get<AiUsage>('/ai/usage')
  return data
}

/**
 * Asks the AI stylist for 1-3 outfits from your wardrobe. Takes roughly 10-40 seconds.
 * Text only → JSON; with a photo → multipart/form-data (same endpoint, the backend handles both).
 */
export async function suggestOutfits(request: OutfitSuggestionRequest): Promise<OutfitSuggestionResponse> {
  if (!request.image) {
    const { data } = await api.post<OutfitSuggestionResponse>('/ai/outfit-suggestions', { prompt: request.prompt })
    return data
  }
  const form = new FormData()
  if (request.prompt) form.append('prompt', request.prompt)
  form.append('image', request.image)
  const { data } = await api.post<OutfitSuggestionResponse>('/ai/outfit-suggestions', form)
  return data
}
