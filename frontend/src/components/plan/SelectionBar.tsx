import { MAX_OUTFIT_ITEMS } from '../../api/outfits'
import type { ClothingItem } from '../../types'

interface SelectionBarProps {
  items: ClothingItem[]
  onClear: () => void
  onNext: () => void
}

/**
 * Bar pinned to the bottom of the screen while choosing clothes: a strip of what you've picked,
 * a count, and "Next". It stays visible however far you scroll through your wardrobe.
 */
export default function SelectionBar({ items, onClear, onNext }: SelectionBarProps) {
  return (
    <div className="fixed inset-x-0 bottom-0 z-10 border-t border-gray-200 bg-white/95 backdrop-blur">
      <div className="mx-auto flex max-w-6xl items-center gap-4 px-4 py-3">
        {/* Thumbnails of the picks, in the order chosen. Scrolls sideways if there are many. */}
        <ol className="flex min-w-0 flex-1 gap-2 overflow-x-auto" aria-label="Selected items">
          {items.length === 0 ? (
            <li className="text-sm text-gray-500">Click clothes to add them to your outfit.</li>
          ) : (
            items.map((item) => (
              <li key={item.id} className="shrink-0">
                <img src={item.imageUrl} alt={item.name} title={item.name} className="h-12 w-9 rounded object-cover" />
              </li>
            ))
          )}
        </ol>

        <div className="flex shrink-0 items-center gap-3">
          <span className={`text-sm ${items.length >= MAX_OUTFIT_ITEMS ? 'font-medium text-amber-700' : 'text-gray-500'}`}>
            {items.length} / {MAX_OUTFIT_ITEMS}
          </span>
          {items.length > 0 && (
            <button type="button" onClick={onClear} className="text-sm text-gray-500 underline hover:text-gray-900">
              Clear
            </button>
          )}
          <button
            type="button"
            onClick={onNext}
            disabled={items.length === 0}
            className="rounded-lg bg-gray-900 px-5 py-2 font-medium text-white hover:bg-gray-700 disabled:cursor-not-allowed disabled:opacity-40"
          >
            Next →
          </button>
        </div>
      </div>
    </div>
  )
}
