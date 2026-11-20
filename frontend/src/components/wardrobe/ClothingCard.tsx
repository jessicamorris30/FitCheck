import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { categoryLabel } from '../../lib/categories'
import type { ClothingItem } from '../../types'

/** One item in the wardrobe grid: photo, name and category. Click to open its detail page. */
export default function ClothingCard({ item }: { item: ClothingItem }) {
  // If the image fails (e.g. its signed link expired after the page sat open for a long time),
  // show a neutral placeholder instead of the browser's broken-image icon.
  const [broken, setBroken] = useState(false)
  // Remember where we came from (e.g. /wardrobe?category=SHOES) so "Back" on the detail page
  // returns to the same filtered view.
  const location = useLocation()

  return (
    <Link
      to={`/wardrobe/${item.id}`}
      state={{ from: location.pathname + location.search }}
      className="group block overflow-hidden rounded-xl border border-gray-200 bg-white transition hover:shadow-md"
    >
      <div className="aspect-[3/4] bg-gray-100">
        {broken ? (
          <div className="flex h-full items-center justify-center text-xs text-gray-400">Image unavailable</div>
        ) : (
          <img
            src={item.imageUrl}
            alt={item.name}
            loading="lazy" // only download images as they scroll into view
            onError={() => setBroken(true)}
            className="h-full w-full object-cover transition group-hover:scale-[1.02]"
          />
        )}
      </div>
      <div className="p-3">
        <p className="truncate font-medium text-gray-900">{item.name}</p>
        <p className="text-sm text-gray-500">
          {categoryLabel(item.category)}
          {item.color && ` · ${item.color}`}
        </p>
      </div>
    </Link>
  )
}
