import type { OutfitSuggestion } from '../../types'
import OutfitThumbnail from './OutfitThumbnail'

interface SuggestionListProps {
  suggestions: OutfitSuggestion[]
  /** When given, each card gets a "Use this outfit" button (wired up in step 4.7). */
  onUse?: (suggestion: OutfitSuggestion) => void
}

/** The AI's suggestions as cards: collage, title, the stylist's reasoning, and the items by name. */
export default function SuggestionList({ suggestions, onUse }: SuggestionListProps) {
  return (
    <ol className="grid gap-4 md:grid-cols-3" aria-label="AI outfit suggestions">
      {suggestions.map((s, i) => (
        <li key={i} className="flex flex-col gap-3 rounded-xl border border-gray-200 bg-white p-4">
          <div className="mx-auto w-full max-w-[220px]">
            <OutfitThumbnail items={s.items} />
          </div>
          <div className="flex-1">
            <p className="font-semibold">{s.title}</p>
            <p className="mt-1 text-sm text-gray-600">{s.reasoning}</p>
            <p className="mt-2 text-xs text-gray-500">{s.items.map((item) => item.name).join(' · ')}</p>
          </div>
          {onUse && (
            <button
              type="button"
              onClick={() => onUse(s)}
              className="rounded-lg bg-gray-900 px-4 py-2 text-sm font-medium text-white hover:bg-gray-700"
            >
              Use this outfit
            </button>
          )}
        </li>
      ))}
    </ol>
  )
}
