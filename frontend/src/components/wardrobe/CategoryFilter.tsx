import { CATEGORIES } from '../../lib/categories'
import type { ClothingCategory } from '../../types'

interface CategoryFilterProps {
  /** The selected category, or null for "All". */
  value: ClothingCategory | null
  onChange: (category: ClothingCategory | null) => void
}

/** A row of pill-shaped tabs: All, Tops, Bottoms, ... Scrolls sideways on narrow screens. */
export default function CategoryFilter({ value, onChange }: CategoryFilterProps) {
  const options: { value: ClothingCategory | null; label: string }[] = [
    { value: null, label: 'All' },
    ...CATEGORIES,
  ]

  return (
    <div role="tablist" aria-label="Filter by category" className="-mx-1 flex gap-2 overflow-x-auto px-1 pb-1">
      {options.map((option) => {
        const selected = option.value === value
        return (
          <button
            key={option.label}
            role="tab"
            aria-selected={selected}
            onClick={() => onChange(option.value)}
            className={`shrink-0 rounded-full border px-4 py-1.5 text-sm transition ${
              selected
                ? 'border-gray-900 bg-gray-900 text-white'
                : 'border-gray-300 bg-white text-gray-700 hover:border-gray-900'
            }`}
          >
            {option.label}
          </button>
        )
      })}
    </div>
  )
}
