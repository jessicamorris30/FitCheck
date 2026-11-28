import { Link } from 'react-router-dom'
import type { PlannedOutfit } from '../../types'
import OutfitThumbnail from '../plan/OutfitThumbnail'

interface DayColumnProps {
  day: Date
  isToday: boolean
  /** This day's outfits, in the order they were added. */
  entries: PlannedOutfit[]
  /** Ids of entries currently being removed (their × shows as busy). */
  removing: Set<string>
  onAdd: () => void
  onRemove: (entry: PlannedOutfit) => void
}

/**
 * One day of the week view: its date, the outfits on it, and "+ Add outfit".
 * On a phone the days stack as rows; on wider screens they sit side by side as 7 columns.
 */
export default function DayColumn({ day, isToday, entries, removing, onAdd, onRemove }: DayColumnProps) {
  const weekday = day.toLocaleDateString(undefined, { weekday: 'short' })
  const longLabel = day.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })

  // On phones/tablets an empty day is one compact row ("Tue 1 ........ + Add") so a week doesn't
  // take several screens of scrolling; on desktop every day stays a full column.
  const empty = entries.length === 0
  const layout = empty ? 'flex-row items-center lg:flex-col lg:items-stretch' : 'flex-col'

  return (
    <section
      aria-label={longLabel + (isToday ? ' (today)' : '')}
      className={`flex gap-2 rounded-xl border p-3 ${layout} ${isToday ? 'border-gray-900 bg-gray-50' : 'border-gray-200'}`}
    >
      <header className="flex flex-1 items-baseline justify-between gap-2 lg:flex-none">
        <span className="text-sm text-gray-500">
          {weekday} <span className={`text-base font-semibold ${isToday ? 'text-gray-900' : 'text-gray-700'}`}>{day.getDate()}</span>
        </span>
        {isToday && <span className="rounded-full bg-gray-900 px-2 py-0.5 text-xs font-medium text-white">Today</span>}
      </header>

      {!empty && (
        <ul className="flex flex-col gap-2">
          {entries.map((entry) => (
          // Phone/tablet: a row (small photo, name, ×). In the 7-column desktop week the columns are
          // narrow, so the photo goes on top with the full name underneath instead.
          <li key={entry.id} className="relative flex items-center gap-2 rounded-lg bg-white p-1 shadow-sm ring-1 ring-gray-100 lg:flex-col lg:items-stretch">
            <Link to={`/outfits/${entry.outfit.id}`} className="flex min-w-0 flex-1 items-center gap-2 lg:flex-col lg:items-stretch lg:gap-1">
              <div className="w-12 shrink-0 lg:w-full">
                <OutfitThumbnail items={entry.outfit.items} />
              </div>
              <span className="truncate text-sm font-medium lg:line-clamp-2 lg:px-1 lg:whitespace-normal">
                {entry.outfit.name}
              </span>
            </Link>
            <button
              type="button"
              onClick={() => onRemove(entry)}
              disabled={removing.has(entry.id)}
              aria-label={`Remove ${entry.outfit.name} from ${longLabel}`}
              title="Remove from this day"
              className="shrink-0 rounded-full px-1.5 text-lg leading-none text-gray-400 hover:text-red-600 disabled:opacity-40 lg:absolute lg:top-2 lg:right-2 lg:bg-white/90 lg:shadow-sm"
            >
              ×
            </button>
          </li>
          ))}
        </ul>
      )}

      <button
        type="button"
        onClick={onAdd}
        aria-label={`Add an outfit to ${longLabel}`}
        className={`rounded-lg border border-dashed border-gray-300 px-2 py-1.5 text-sm text-gray-500 hover:border-gray-900 hover:text-gray-900 ${
          empty ? 'shrink-0 px-3 lg:px-2' : ''
        }`}
      >
        {/* Compact rows only have room for "+ Add"; the full text where there's space. */}
        {empty ? (
          <>
            + Add<span className="hidden lg:inline"> outfit</span>
          </>
        ) : (
          '+ Add outfit'
        )}
      </button>
    </section>
  )
}
