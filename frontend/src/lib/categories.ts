import type { ClothingCategory } from '../types'

/**
 * Display names for each category, in the order the filter tabs show them.
 * The keys must match the backend's ClothingCategory enum exactly.
 */
export const CATEGORIES: { value: ClothingCategory; label: string }[] = [
  { value: 'TOP', label: 'Tops' },
  { value: 'BOTTOM', label: 'Bottoms' },
  { value: 'DRESS', label: 'Dresses' },
  { value: 'OUTERWEAR', label: 'Outerwear' },
  { value: 'SHOES', label: 'Shoes' },
  { value: 'ACCESSORY', label: 'Accessories' },
  { value: 'OTHER', label: 'Other' },
]

/** "SHOES" → "Shoes" (singular-ish label for one item). */
export function categoryLabel(category: ClothingCategory): string {
  const singular: Record<ClothingCategory, string> = {
    TOP: 'Top',
    BOTTOM: 'Bottom',
    DRESS: 'Dress',
    OUTERWEAR: 'Outerwear',
    SHOES: 'Shoes',
    ACCESSORY: 'Accessory',
    OTHER: 'Other',
  }
  return singular[category]
}

/** Type guard: is this string (e.g. from the URL) a real category? */
export function isCategory(value: string | null): value is ClothingCategory {
  return CATEGORIES.some((c) => c.value === value)
}
