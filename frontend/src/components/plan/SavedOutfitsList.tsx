import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { parseApiError } from '../../api/errors'
import { listOutfits } from '../../api/outfits'
import type { Outfit } from '../../types'
import OutfitThumbnail from './OutfitThumbnail'

/**
 * Your saved outfits as a grid, most recently changed first: collage thumbnail, name, item count.
 * Used by the Outfits tab. Click a card to open the outfit.
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
        <li key={outfit.id}>
          <Link to={`/outfits/${outfit.id}`} className="group flex flex-col gap-2">
            <div className="relative transition group-hover:opacity-90 group-hover:shadow-md">
              <OutfitThumbnail items={outfit.items} />
              {outfit.aiPrompt && (
                <span
                  title={`AI stylist: “${outfit.aiPrompt}”`}
                  className="absolute top-2 left-2 rounded-full bg-violet-600/90 px-2 py-0.5 text-xs font-medium text-white"
                >
                  ✨ AI
                </span>
              )}
            </div>
            <div>
              <p className="truncate font-medium group-hover:underline">{outfit.name}</p>
              <p className="text-sm text-gray-500">
                {outfit.items.length} {outfit.items.length === 1 ? 'item' : 'items'}
              </p>
            </div>
          </Link>
        </li>
      ))}
    </ul>
  )
}
