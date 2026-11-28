import type { OutfitDay, PlannedOutfit } from '../types'
import { api } from './client'

// Endpoints under /api/calendar. Days are plain "YYYY-MM-DD" strings (see lib/dates.ts).

/** Your planned outfits from `from` to `to` (both included, at most 62 days), by day then order added. */
export async function listPlanned(from: string, to: string): Promise<PlannedOutfit[]> {
  const { data } = await api.get<PlannedOutfit[]>('/calendar', { params: { from, to } })
  return data
}

/** Every day one of your outfits is on, earliest first (past days included). */
export async function listOutfitDays(outfitId: string): Promise<OutfitDay[]> {
  const { data } = await api.get<OutfitDay[]>(`/calendar/outfits/${outfitId}`)
  return data
}

/** Puts one of your outfits on a day. 409 if it's already on that day. */
export async function planOutfit(date: string, outfitId: string): Promise<PlannedOutfit> {
  const { data } = await api.post<PlannedOutfit>('/calendar', { date, outfitId })
  return data
}

/** Takes an entry off its day (the outfit itself stays). */
export async function removePlanned(id: string): Promise<void> {
  await api.delete(`/calendar/${id}`)
}
