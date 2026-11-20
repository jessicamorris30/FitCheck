import { MAX_OUTFIT_ITEMS } from '../../api/outfits'
import { categoryLabel } from '../../lib/categories'
import type { ClothingItem } from '../../types'

interface OutfitCanvasProps {
  /** The outfit being built, in order. */
  items: ClothingItem[]
  onRemove: (itemId: string) => void
  /** Move the item at `index` one place earlier (-1) or later (+1). */
  onMove: (index: number, direction: -1 | 1) => void
  onClear: () => void
}

/**
 * The outfit you're putting together: your chosen items in order, each with ← → to reorder
 * and × to remove. It only displays the list; PlanPage owns it and decides what changes do.
 */
export default function OutfitCanvas({ items, onRemove, onMove, onClear }: OutfitCanvasProps) {
  return (
    <section className="flex flex-col gap-4 rounded-2xl border border-gray-200 bg-gray-50 p-4" aria-label="Your outfit">
      <header className="flex items-center justify-between">
        <h2 className="text-lg font-semibold">Your outfit</h2>
        <div className="flex items-center gap-3 text-sm">
          <span className={items.length >= MAX_OUTFIT_ITEMS ? 'font-medium text-amber-700' : 'text-gray-500'}>
            {items.length} / {MAX_OUTFIT_ITEMS}
          </span>
          {items.length > 0 && (
            <button type="button" onClick={onClear} className="text-gray-500 underline hover:text-gray-900">
              Clear
            </button>
          )}
        </div>
      </header>

      {items.length === 0 ? (
        <div className="rounded-xl border-2 border-dashed border-gray-300 p-8 text-center text-sm text-gray-500">
          Click items from your wardrobe to start building an outfit.
        </div>
      ) : (
        <ol className="grid grid-cols-2 gap-3 sm:grid-cols-3">
          {items.map((item, index) => (
            <li key={item.id} className="relative overflow-hidden rounded-xl border border-gray-200 bg-white">
              <img src={item.imageUrl} alt={item.name} className="aspect-[3/4] w-full object-cover" />

              {/* Position badge: shows the order is meaningful. */}
              <span className="absolute top-1.5 left-1.5 flex h-6 w-6 items-center justify-center rounded-full bg-white/90 text-xs font-medium">
                {index + 1}
              </span>
              <button
                type="button"
                onClick={() => onRemove(item.id)}
                aria-label={`Remove ${item.name}`}
                className="absolute top-1.5 right-1.5 flex h-6 w-6 items-center justify-center rounded-full bg-white/90 text-gray-700 hover:bg-white hover:text-red-600"
              >
                ×
              </button>

              <div className="flex items-center gap-1 p-2">
                <div className="min-w-0 flex-1">
                  <p className="truncate text-sm font-medium">{item.name}</p>
                  <p className="text-xs text-gray-500">{categoryLabel(item.category)}</p>
                </div>
                <button
                  type="button"
                  onClick={() => onMove(index, -1)}
                  disabled={index === 0}
                  aria-label={`Move ${item.name} earlier`}
                  className="rounded px-1.5 py-0.5 text-gray-600 hover:bg-gray-100 disabled:opacity-30"
                >
                  ←
                </button>
                <button
                  type="button"
                  onClick={() => onMove(index, 1)}
                  disabled={index === items.length - 1}
                  aria-label={`Move ${item.name} later`}
                  className="rounded px-1.5 py-0.5 text-gray-600 hover:bg-gray-100 disabled:opacity-30"
                >
                  →
                </button>
              </div>
            </li>
          ))}
        </ol>
      )}
    </section>
  )
}
