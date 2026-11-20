import { useCallback, useMemo, useState } from 'react'
import { MAX_OUTFIT_ITEMS } from '../api/outfits'
import OutfitCanvas from '../components/plan/OutfitCanvas'
import OutfitItemPicker from '../components/plan/OutfitItemPicker'
import type { ClothingItem } from '../types'

/**
 * Plan mode: pick clothes from your wardrobe (left) to build an outfit (right).
 *
 * The whole outfit-in-progress is ONE piece of state here: the ordered list of chosen items.
 * The picker and the canvas don't keep their own copies. They receive it and report clicks
 * back ("lifting state up"), so the ✓ marks in the picker and the canvas can never disagree.
 *
 * Saving the outfit comes in step 3.5.
 */
export default function PlanPage() {
  const [items, setItems] = useState<ClothingItem[]>([])

  const selectedIds = useMemo(() => new Set(items.map((i) => i.id)), [items])
  const full = items.length >= MAX_OUTFIT_ITEMS

  // Updates use the "functional" form setItems(prev => ...) so they always start from the
  // latest list, even if several clicks happen before React re-renders.
  const toggle = useCallback((item: ClothingItem) => {
    setItems((prev) => {
      if (prev.some((i) => i.id === item.id)) return prev.filter((i) => i.id !== item.id)
      if (prev.length >= MAX_OUTFIT_ITEMS) return prev
      return [...prev, item]
    })
  }, [])

  const remove = useCallback((itemId: string) => {
    setItems((prev) => prev.filter((i) => i.id !== itemId))
  }, [])

  const move = useCallback((index: number, direction: -1 | 1) => {
    setItems((prev) => {
      const target = index + direction
      if (target < 0 || target >= prev.length) return prev
      const next = [...prev] // never change the old array: React compares by reference
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }, [])

  return (
    <section className="flex flex-col gap-6">
      <header>
        <h1 className="text-2xl font-semibold">Plan an outfit</h1>
        <p className="text-sm text-gray-500">Click clothes to add or remove them, then arrange them in order.</p>
      </header>

      {/* On phones: outfit on top, wardrobe below. On wide screens: wardrobe left, outfit right (sticky). */}
      <div className="grid gap-6 lg:grid-cols-[1fr_minmax(320px,420px)]">
        <div className="lg:order-2">
          <div className="lg:sticky lg:top-4">
            <OutfitCanvas items={items} onRemove={remove} onMove={move} onClear={() => setItems([])} />
          </div>
        </div>
        <div className="lg:order-1">
          <OutfitItemPicker selectedIds={selectedIds} onToggle={toggle} full={full} />
        </div>
      </div>
    </section>
  )
}
