import type { Outfit } from '../types'
import { api } from './client'

// Endpoints under /api/outfits (docs/API.md). Outfits come back with their full clothing items
// (including fresh signed image links), in the outfit's order.

export interface OutfitInput {
  name: string
  notes?: string
  /** Clothing item ids in the order they should appear. 1-20 of your own items. */
  itemIds: string[]
  /** The AI prompt this outfit came from, if any (kept on edits too). */
  aiPrompt?: string | null
}

/** Maximum items in one outfit (matches the backend's Outfit.MAX_ITEMS). */
export const MAX_OUTFIT_ITEMS = 20

/** Your outfits, most recently changed first. */
export async function listOutfits(): Promise<Outfit[]> {
  const { data } = await api.get<Outfit[]>('/outfits')
  return data
}

export async function getOutfit(id: string): Promise<Outfit> {
  const { data } = await api.get<Outfit>(`/outfits/${id}`)
  return data
}

export async function createOutfit(input: OutfitInput): Promise<Outfit> {
  const { data } = await api.post<Outfit>('/outfits', input)
  return data
}

/** Replaces name, notes and the full item list (order included). */
export async function updateOutfit(id: string, input: OutfitInput): Promise<Outfit> {
  const { data } = await api.put<Outfit>(`/outfits/${id}`, input)
  return data
}

/** Deletes the outfit. Your clothes are not affected. */
export async function deleteOutfit(id: string): Promise<void> {
  await api.delete(`/outfits/${id}`)
}
