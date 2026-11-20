import type { ClothingDetails } from '../api/clothing'
import type { ClothingCategory, ClothingItem } from '../types'

/**
 * What the details form holds while the user types. Tags are one comma-separated string here
 * ("casual, summer") and become a list only when sent to the API.
 */
export interface DetailsFormValues {
  name: string
  category: ClothingCategory | ''
  color: string
  tags: string
}

export const EMPTY_DETAILS: DetailsFormValues = { name: '', category: '', color: '', tags: '' }

/** Pre-fill the form from an existing item (for editing). */
export function formValuesFromItem(item: ClothingItem): DetailsFormValues {
  return { name: item.name, category: item.category, color: item.color ?? '', tags: item.tags.join(', ') }
}

/** Form values → what the API expects. "casual, Summer ,  work" → ["casual", "Summer", "work"]. */
export function toClothingDetails(values: DetailsFormValues): ClothingDetails {
  return {
    name: values.name,
    category: values.category || null,
    color: values.color,
    tags: values.tags.split(',').map((t) => t.trim()).filter(Boolean),
  }
}

/** The backend may report tag errors as "tags" or "tags[2]"; show any of them under the tags field. */
export function tagsError(fieldErrors: Record<string, string>): string | undefined {
  return Object.entries(fieldErrors).find(([key]) => key.startsWith('tags'))?.[1]
}
