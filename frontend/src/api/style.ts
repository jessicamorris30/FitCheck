import type { Style } from '../types'
import { api } from './client'

// "My style" under /api/users/me/style (docs/API.md). Every call returns the whole style as the
// page may see it: note, switch, photos. (The AI's own notes on the style never reach the browser.)

export async function getStyle(): Promise<Style> {
  const { data } = await api.get<Style>('/users/me/style')
  return data
}

/** Saves the note (blank = none) and whether the AI stylist should use the style. */
export async function saveStyle(note: string, useWithAi: boolean): Promise<Style> {
  const { data } = await api.put<Style>('/users/me/style', { note, useWithAi })
  return data
}

/** Adds one style photo (JPEG/PNG/WebP, at most 10 MB; at most 5 photos in total). */
export async function addStylePhoto(image: File): Promise<Style> {
  const form = new FormData()
  form.append('image', image)
  const { data } = await api.post<Style>('/users/me/style/photos', form)
  return data
}

export async function deleteStylePhoto(id: string): Promise<Style> {
  const { data } = await api.delete<Style>(`/users/me/style/photos/${id}`)
  return data
}
