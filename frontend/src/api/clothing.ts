import type { ClothingCategory, ClothingItem } from '../types'
import { api } from './client'

// Endpoints under /api/clothing (docs/API.md). Every item's imageUrl is a short-lived signed
// link: display it, don't store it.

/** Your wardrobe, newest first. Pass a category to get only that category. */
export async function listClothing(category?: ClothingCategory): Promise<ClothingItem[]> {
  const { data } = await api.get<ClothingItem[]>('/clothing', { params: { category } })
  return data
}

export async function getClothingItem(id: string): Promise<ClothingItem> {
  const { data } = await api.get<ClothingItem>(`/clothing/${id}`)
  return data
}

/** Uploads an image plus metadata as multipart/form-data. */
export async function uploadClothingItem(form: FormData): Promise<ClothingItem> {
  const { data } = await api.post<ClothingItem>('/clothing', form)
  return data
}

export async function deleteClothingItem(id: string): Promise<void> {
  await api.delete(`/clothing/${id}`)
}
