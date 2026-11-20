import { useCallback, useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { listClothing } from '../api/clothing'
import { parseApiError } from '../api/errors'
import Button from '../components/ui/Button'
import ClothingGrid, { ClothingGridSkeleton } from '../components/wardrobe/ClothingGrid'
import CategoryFilter from '../components/wardrobe/CategoryFilter'
import UploadClothingModal from '../components/wardrobe/UploadClothingModal'
import { CATEGORIES, isCategory } from '../lib/categories'
import type { ClothingCategory, ClothingItem } from '../types'

/**
 * Browse your wardrobe: category tabs + a grid of your clothes.
 *
 * The selected category lives in the URL (/wardrobe?category=SHOES), not just in React state,
 * so reloading the page, the back button and shared links all keep the filter.
 */
export default function WardrobePage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const raw = searchParams.get('category')
  const category: ClothingCategory | null = isCategory(raw) ? raw : null

  const [reloadKey, setReloadKey] = useState(0) // bump to re-fetch (the "Try again" button)

  // Which request we currently want, e.g. "SHOES#0". Each response remembers the key it was for.
  // "Loading" isn't stored separately: we're loading whenever the newest result belongs to an
  // older request (or there's no result yet). One source of truth, so the two can't disagree.
  const requestKey = `${category ?? 'ALL'}#${reloadKey}`
  const [result, setResult] = useState<{ key: string; items: ClothingItem[]; error: string | null } | null>(null)

  const loading = result?.key !== requestKey
  const items = result?.items ?? []
  const error = loading ? null : (result?.error ?? null)

  // Fetch whenever the category (or reloadKey) changes.
  useEffect(() => {
    let cancelled = false // ignore a slow response if the user already switched tabs
    listClothing(category ?? undefined)
      .then((data) => {
        if (!cancelled) setResult({ key: requestKey, items: data, error: null })
      })
      .catch((err) => {
        if (!cancelled) setResult({ key: requestKey, items: [], error: parseApiError(err).message })
      })
    return () => {
      cancelled = true
    }
  }, [category, requestKey])

  const [uploadOpen, setUploadOpen] = useState(false)

  // Show a freshly uploaded item immediately, at the top (newest first), without re-fetching.
  // If a different category tab is selected, it doesn't belong in the current view.
  const handleUploaded = useCallback(
    (item: ClothingItem) => {
      if (category && item.category !== category) return
      setResult((prev) => (prev ? { ...prev, items: [item, ...prev.items] } : prev))
    },
    [category],
  )

  const selectCategory = useCallback(
    (next: ClothingCategory | null) => {
      setSearchParams(next ? { category: next } : {})
    },
    [setSearchParams],
  )

  const categoryName = CATEGORIES.find((c) => c.value === category)?.label.toLowerCase()

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-2">
        <div>
          <h1 className="text-2xl font-semibold">Your wardrobe</h1>
          {!loading && !error && (
            <p className="text-sm text-gray-500">
              {items.length} {items.length === 1 ? 'item' : 'items'}
              {categoryName && ` in ${categoryName}`}
            </p>
          )}
        </div>
        <Button onClick={() => setUploadOpen(true)}>+ Add item</Button>
      </header>

      {/* Only rendered while open, so each opening starts with an empty form. */}
      {uploadOpen && <UploadClothingModal onClose={() => setUploadOpen(false)} onUploaded={handleUploaded} />}

      <CategoryFilter value={category} onChange={selectCategory} />

      {loading ? (
        <ClothingGridSkeleton />
      ) : error ? (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-6 text-center">
          <p className="text-red-700">{error}</p>
          <button onClick={() => setReloadKey((k) => k + 1)} className="mt-3 font-medium text-red-800 underline">
            Try again
          </button>
        </div>
      ) : items.length === 0 ? (
        <div className="rounded-xl border border-dashed border-gray-300 p-12 text-center text-gray-500">
          <p>{category ? `No ${categoryName} yet.` : 'Your wardrobe is empty. Upload your first piece of clothing to get started.'}</p>
          <button onClick={() => setUploadOpen(true)} className="mt-3 font-medium text-gray-900 underline">
            Add an item
          </button>
        </div>
      ) : (
        <ClothingGrid items={items} />
      )}
    </section>
  )
}
