// Shared API types. Keep in sync with the backend DTOs (see docs/API.md).

export interface User {
  id: string
  email: string
  /** Lower case; can be used to log in. Null for accounts from before usernames existed. */
  username: string | null
  firstName: string | null
  lastName: string | null
  /** False until username, first and last name are set; the app asks for them first. */
  profileComplete: boolean
  /** Light/dark choice saved on the account; null if never chosen (follow the device). */
  theme: 'system' | 'light' | 'dark' | null
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
  /** What was asked of the AI stylist if this outfit came from a suggestion; null if hand-made. */
  aiPrompt: string | null
  /** The outfit's clothing items, in order. */
  items: ClothingItem[]
  createdAt: string
  updatedAt: string
}

// ---- Outfit calendar (/api/calendar) ----

/** One outfit put on one day. */
export interface PlannedOutfit {
  /** The calendar entry's own id (to remove it), not the outfit's. */
  id: string
  /** The day, "YYYY-MM-DD". */
  date: string
  outfit: Outfit
}

/** One day an outfit is on (for the outfit's own page). */
export interface OutfitDay {
  /** The calendar entry's id. */
  id: string
  /** The day, "YYYY-MM-DD". */
  date: string
}

// ---- My style (/api/users/me/style) ----

export interface StylePhoto {
  id: string
  imageUrl: string
  thumbnailUrl: string
  addedAt: string
}

/** The user's own style, which the AI stylist takes into account (when useWithAi is on). */
export interface Style {
  /** The user's own words about their style; null if none. */
  note: string | null
  useWithAi: boolean
  photos: StylePhoto[]
  /** How many photos are allowed in total (5). */
  maxPhotos: number
  /** Null if the user has never set anything. */
  updatedAt: string | null
}

// ---- AI stylist (/api/ai) ----

export interface OutfitSuggestionRequest {
  /** What the user is going for, e.g. "90s grunge, cozy". Optional if there's a photo. */
  prompt?: string
  /** Optional inspiration photo of a look the user likes. */
  image?: File
}

/** One outfit suggested by the AI, with its full clothing items in order. */
export interface OutfitSuggestion {
  title: string
  reasoning: string
  items: ClothingItem[]
}

export interface OutfitSuggestionResponse {
  suggestions: OutfitSuggestion[]
}

export interface AiStatus {
  configured: boolean
  model: string
}

/** "n of limit left today", resets at midnight UTC. */
export interface AiUsage {
  used: number
  limit: number
  remaining: number
  resetsAt: string
}
