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

export interface ClothingDetails {
  name: string
  category: ClothingCategory | null
  color: string
  tags: string[]
}

/**
 * Uploads a photo plus its details as multipart/form-data: two named parts, "image" (the file)
 * and "metadata" (JSON). The JSON is wrapped in a Blob so it can carry its own
 * Content-Type, which is how the backend's @RequestPart knows to parse it as JSON.
 *
 * We don't set the request's Content-Type ourselves: given FormData, the browser adds
 * "multipart/form-data; boundary=..." with the boundary string that separates the parts.
 *
 * @param onProgress called with 0-100 as the file uploads
 */
export async function uploadClothingItem(
  image: File,
  details: ClothingDetails,
  onProgress?: (percent: number) => void,
): Promise<ClothingItem> {
  const form = new FormData()
  form.append('image', image)
  form.append('metadata', new Blob([JSON.stringify(details)], { type: 'application/json' }))

  const { data } = await api.post<ClothingItem>('/clothing', form, {
    onUploadProgress: (e) => {
      if (onProgress && e.total) onProgress(Math.round((e.loaded / e.total) * 100))
    },
  })
  return data
}

export async function deleteClothingItem(id: string): Promise<void> {
  await api.delete(`/clothing/${id}`)
}
