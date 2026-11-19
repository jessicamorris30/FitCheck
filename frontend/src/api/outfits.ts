import type { Outfit } from '../types'
import { api } from './client'

// TODO: implement once the backend /api/outfits endpoints exist (docs/API.md#outfits).

export interface OutfitInput {
  name: string
  notes?: string
  itemIds: string[]
}

export async function listOutfits(): Promise<Outfit[]> {
  const { data } = await api.get<Outfit[]>('/outfits')
  return data
}

export async function createOutfit(input: OutfitInput): Promise<Outfit> {
  const { data } = await api.post<Outfit>('/outfits', input)
  return data
}

export async function updateOutfit(id: string, input: OutfitInput): Promise<Outfit> {
  const { data } = await api.put<Outfit>(`/outfits/${id}`, input)
  return data
}

export async function deleteOutfit(id: string): Promise<void> {
  await api.delete(`/outfits/${id}`)
}
