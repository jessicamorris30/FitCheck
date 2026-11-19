import type { OutfitSuggestionRequest, OutfitSuggestionResponse } from '../types'
import { api } from './client'

// TODO: implement once POST /api/ai/outfit-suggestions exists (docs/AI_INTEGRATION.md).
// The Claude API is only ever called from the backend; the browser never sees the API key.

export async function suggestOutfits(
  request: OutfitSuggestionRequest,
): Promise<OutfitSuggestionResponse> {
  const form = new FormData()
  if (request.prompt) form.append('prompt', request.prompt)
  if (request.image) form.append('image', request.image)
  request.itemIds?.forEach((id) => form.append('itemIds', id))

  const { data } = await api.post<OutfitSuggestionResponse>('/ai/outfit-suggestions', form)
  return data
}
