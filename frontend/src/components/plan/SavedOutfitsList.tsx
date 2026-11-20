import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { parseApiError } from '../../api/errors'
import { listOutfits } from '../../api/outfits'
import type { Outfit } from '../../types'
import OutfitThumbnail from './OutfitThumbnail'

/**
 * Your saved outfits as a grid, most recently changed first: collage thumbnail, name, item count.
 * Used by the Outfits tab. (Opening an outfit to edit or delete it comes in step 3.6.)
 */
export default function SavedOutfitsList() {
  const [result, setResult] = useState<{ outfits: Outfit[]; error: string | null } | null>(null)

  useEffect(() => {
    let cancelled = false
    listOutfits()
      .then((outfits) => !cancelled && setResult({ outfits, error: null }))
      .catch((err) => !cancelled && setResult({ outfits: [], error: parseApiError(err).message }))
    return () => {
      cancelled = true
    }
  }, [])

  if (result === null) {
    return (
      <ul className="grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-4" aria-busy="true">
        {Array.from({ length: 4 }, (_, i) => (
          <li key={i} className="aspect-square animate-pulse rounded-xl bg-gray-100" />
        ))}
      </ul>
    )
  }

  if (result.error) {
    return (
      <p role="alert" className="rounded-lg bg-red-50 p-4 text-sm text-red-700">
        {result.error}
      </p>
    )
  }

  if (result.outfits.length === 0) {
    return (
      <div className="rounded-xl border border-dashed border-gray-300 p-12 text-center text-gray-500">
        <p>You haven't saved any outfits yet.</p>
        <Link to="/plan" className="mt-3 inline-block font-medium text-gray-900 underline">
          Plan your first outfit
        </Link>
      </div>
    )
  }

  return (
    <ul className="grid grid-cols-2 gap-6 sm:grid-cols-3 lg:grid-cols-4">
      {result.outfits.map((outfit) => (
        <li key={outfit.id} className="flex flex-col gap-2">
          <OutfitThumbnail items={outfit.items} />
          <div>
            <p className="truncate font-medium">{outfit.name}</p>
            <p className="text-sm text-gray-500">
              {outfit.items.length} {outfit.items.length === 1 ? 'item' : 'items'}
            </p>
          </div>
        </li>
      ))}
    </ul>
  )
}
