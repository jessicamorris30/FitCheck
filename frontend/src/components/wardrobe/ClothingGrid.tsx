import type { ClothingItem } from '../../types'
import ClothingCard from './ClothingCard'

/** Responsive grid: 2 columns on phones, up to 5 on wide screens. */
export default function ClothingGrid({ items }: { items: ClothingItem[] }) {
  return (
    <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5">
      {items.map((item) => (
        // key: React uses it to track which card is which between renders. Must be unique and stable.
        <li key={item.id}>
          <ClothingCard item={item} />
        </li>
      ))}
    </ul>
  )
}

/** Grey placeholder cards shown while the wardrobe is loading. */
export function ClothingGridSkeleton({ count = 8 }: { count?: number }) {
  return (
    <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3 lg:grid-cols-4 xl:grid-cols-5" aria-busy="true">
      {Array.from({ length: count }, (_, i) => (
        <li key={i} className="animate-pulse overflow-hidden rounded-xl border border-gray-200">
          <div className="aspect-[3/4] bg-gray-100" />
          <div className="space-y-2 p-3">
            <div className="h-4 w-3/4 rounded bg-gray-100" />
            <div className="h-3 w-1/2 rounded bg-gray-100" />
          </div>
        </li>
      ))}
    </ul>
  )
}
