import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { listClothing } from '../../api/clothing'
import { parseApiError } from '../../api/errors'
import { categoryLabel } from '../../lib/categories'
import type { ClothingCategory, ClothingItem } from '../../types'
import CategoryFilter from '../wardrobe/CategoryFilter'

interface OutfitItemPickerProps {
  /** Ids of items already in the outfit (shown with a ✓). */
  selectedIds: Set<string>
  /** Click on an item: add it, or remove it if it's already in the outfit. */
  onToggle: (item: ClothingItem) => void
  /** True when the outfit is full: unselected items can't be added. */
  full: boolean
}

/**
 * Your wardrobe as a compact, clickable grid, with the same category tabs as the Wardrobe page.
 * Unlike the Wardrobe page, the filter here is plain component state (not in the URL): it's a
 * helper panel, not a page you'd bookmark.
 */
export default function OutfitItemPicker({ selectedIds, onToggle, full }: OutfitItemPickerProps) {
  const [category, setCategory] = useState<ClothingCategory | null>(null)

  // Same "result remembers its request" loading pattern as WardrobePage.
  const requestKey = category ?? 'ALL'
  const [result, setResult] = useState<{ key: string; items: ClothingItem[]; error: string | null } | null>(null)
  const loading = result?.key !== requestKey

  useEffect(() => {
    let cancelled = false
    listClothing(category ?? undefined)
      .then((items) => !cancelled && setResult({ key: requestKey, items, error: null }))
      .catch((err) => !cancelled && setResult({ key: requestKey, items: [], error: parseApiError(err).message }))
    return () => {
      cancelled = true
    }
  }, [category, requestKey])

  return (
    <section className="flex flex-col gap-4" aria-label="Your wardrobe">
      <CategoryFilter value={category} onChange={setCategory} />

      {loading ? (
        <ul className="grid grid-cols-3 gap-3 sm:grid-cols-4 lg:grid-cols-6" aria-busy="true">
          {Array.from({ length: 8 }, (_, i) => (
            <li key={i} className="aspect-[3/4] animate-pulse rounded-lg bg-gray-100" />
          ))}
        </ul>
      ) : result?.error ? (
        <p role="alert" className="rounded-lg bg-red-50 p-4 text-sm text-red-700">
          {result.error}
        </p>
      ) : result?.items.length === 0 ? (
        <p className="rounded-lg border border-dashed border-gray-300 p-6 text-center text-sm text-gray-500">
          {category ? 'Nothing in this category yet.' : 'Your wardrobe is empty. '}
          {!category && (
            <Link to="/wardrobe" className="font-medium text-gray-900 underline">
              Add some clothes first
            </Link>
          )}
        </p>
      ) : (
        <ul className="grid grid-cols-3 gap-3 sm:grid-cols-4 lg:grid-cols-6">
          {result?.items.map((item) => {
            const selected = selectedIds.has(item.id)
            const blocked = full && !selected
            return (
              <li key={item.id}>
                {/* A real <button>: keyboard-accessible, and aria-pressed tells screen readers it's a toggle. */}
                <button
                  type="button"
                  onClick={() => onToggle(item)}
                  disabled={blocked}
                  aria-pressed={selected}
                  title={blocked ? 'This outfit is full (20 items)' : selected ? 'Remove from outfit' : 'Add to outfit'}
                  className={`group relative block w-full overflow-hidden rounded-lg border-2 text-left transition disabled:cursor-not-allowed disabled:opacity-40 ${
                    selected ? 'border-gray-900' : 'border-transparent hover:border-gray-300'
                  }`}
                >
                  <img src={item.thumbnailUrl} alt="" loading="lazy" className="aspect-[3/4] w-full bg-gray-100 object-cover" />
                  {selected && (
                    <span className="absolute top-1.5 right-1.5 flex h-6 w-6 items-center justify-center rounded-full bg-gray-900 text-sm text-white">
                      ✓
                    </span>
                  )}
                  <span className="block truncate px-1 pt-1 text-xs font-medium text-gray-900">{item.name}</span>
                  <span className="block px-1 pb-1 text-xs text-gray-500">{categoryLabel(item.category)}</span>
                </button>
              </li>
            )
          })}
        </ul>
      )}
    </section>
  )
}
