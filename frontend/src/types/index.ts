// Shared API types. Keep in sync with the backend DTOs (see docs/API.md).

export interface User {
  id: string
  email: string
  /** True once the user clicked the link in their confirmation email. */
  emailVerified: boolean
  createdAt: string
}

/** The JSON body of every backend error (ApiError.java). */
export interface ApiError {
  status: number
  error: string
  message: string
  timestamp: string
  /** Present on 400 validation errors: field name → message. */
  fieldErrors?: Record<string, string>
}

export interface AuthResponse {
  accessToken: string
  expiresIn: number
  user: User
}

export type ClothingCategory =
  | 'TOP'
  | 'BOTTOM'
  | 'DRESS'
  | 'OUTERWEAR'
  | 'SHOES'
  | 'ACCESSORY'
  | 'OTHER'

export interface ClothingItem {
  id: string
  name: string
  category: ClothingCategory
  color: string | null
  tags: string[]
  /** Full-size photo (signed link, expires after ~15 min). Use on detail pages. */
  imageUrl: string
  /** Small JPEG version (≤ 320px). Use in grids and previews: much faster to load. */
  thumbnailUrl: string
  createdAt: string
}

export interface Outfit {
  id: string
  name: string
  notes: string | null
  /** The outfit's clothing items, in order. */
  items: ClothingItem[]
  createdAt: string
  updatedAt: string
}

export interface OutfitSuggestionRequest {
  /** Text describing the desired aesthetic, e.g. "90s grunge, cozy". */
  prompt?: string
  /** Optional inspiration image (sent as multipart form data). */
  image?: File
  /** Optional: restrict suggestions to these items. */
  itemIds?: string[]
}

export interface OutfitSuggestion {
  itemIds: string[]
  title: string
  reasoning: string
}

export interface OutfitSuggestionResponse {
  suggestions: OutfitSuggestion[]
}
