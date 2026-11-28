import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { listPlanned } from '../../api/calendar'
import { parseApiError } from '../../api/errors'
import {
  addMonths,
  formatMonth,
  monthGridDays,
  parseIsoDay,
  parseIsoMonth,
  startOfWeek,
  toIsoDay,
  toIsoMonth,
} from '../../lib/dates'
import type { PlannedOutfit } from '../../types'
import OutfitThumbnail from '../plan/OutfitThumbnail'
import PeriodNav from './PeriodNav'

/** How many outfit photos fit in one day square; the rest show as "+2". */
const SHOWN_PER_DAY = 2

/**
 * A whole month at a glance (?view=month&month=2026-12). Each day shows what's planned; tapping
 * a day opens its week, where outfits are added and removed.
 */
export default function MonthView({ today }: { today: string }) {
  const [searchParams, setSearchParams] = useSearchParams()

  const firstOfMonth = parseIsoMonth(searchParams.get('month')) ?? parseIsoMonth(today.slice(0, 7))!
  const monthIso = toIsoMonth(firstOfMonth)
  const days = monthGridDays(firstOfMonth)
  const fromIso = toIsoDay(days[0])
  const toIso = toIsoDay(days[days.length - 1])

  const [loaded, setLoaded] = useState<{ month: string; entries: PlannedOutfit[]; error: string | null } | null>(null)

  useEffect(() => {
    let cancelled = false
    listPlanned(fromIso, toIso)
      .then((entries) => !cancelled && setLoaded({ month: monthIso, entries, error: null }))
      .catch((err) => !cancelled && setLoaded({ month: monthIso, entries: [], error: parseApiError(err).message }))
    return () => {
      cancelled = true
    }
  }, [monthIso, fromIso, toIso])

  const current = loaded?.month === monthIso ? loaded : null

  function goToMonth(month: Date | null) {
    setSearchParams(month ? { view: 'month', month: toIsoMonth(month) } : { view: 'month' })
  }

  // Weekday names for the header row, Monday first, in the reader's language.
  const weekdayNames = days.slice(0, 7).map((d) => ({
    short: d.toLocaleDateString(undefined, { weekday: 'short' }),
    narrow: d.toLocaleDateString(undefined, { weekday: 'narrow' }),
  }))

  return (
    <div className="flex flex-col gap-4">
      <PeriodNav
        label={formatMonth(firstOfMonth)}
        unit="month"
        isCurrent={monthIso === today.slice(0, 7)}
        onPrevious={() => goToMonth(addMonths(firstOfMonth, -1))}
        onCurrent={() => goToMonth(null)}
        onNext={() => goToMonth(addMonths(firstOfMonth, 1))}
      />

      {current?.error && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {current.error}
        </p>
      )}

      <div className="grid grid-cols-7 gap-1 sm:gap-2" aria-busy={current === null}>
        {weekdayNames.map((name) => (
          <div key={name.short} className="pb-1 text-center text-xs font-medium text-gray-500" aria-hidden="true">
            <span className="sm:hidden">{name.narrow}</span>
            <span className="hidden sm:inline">{name.short}</span>
          </div>
        ))}

        {days.map((day) => {
          const iso = toIsoDay(day)
          const inMonth = day.getMonth() === firstOfMonth.getMonth()
          const isToday = iso === today
          const entries = current?.entries.filter((e) => e.date === iso) ?? []
          const label =
            day.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' }) +
            (isToday ? ', today' : '') +
            (entries.length ? `: ${entries.map((e) => e.outfit.name).join(', ')}` : ': nothing planned')

          return (
            <Link
              key={iso}
              to={`/calendar?week=${toIsoDay(startOfWeek(parseIsoDay(iso)!))}`}
              aria-label={label}
              className={`flex min-h-16 flex-col gap-1 rounded-lg border p-1 transition hover:border-gray-900 sm:min-h-28 sm:p-1.5 ${
                isToday ? 'border-gray-900 bg-gray-50' : 'border-gray-200'
              } ${inMonth ? '' : 'opacity-40'}`}
            >
              <span
                className={`self-start rounded-full px-1.5 text-xs sm:text-sm ${
                  isToday ? 'bg-gray-900 font-semibold text-white' : 'font-medium text-gray-700'
                }`}
              >
                {day.getDate()}
              </span>

              {current === null ? (
                <span className="h-4 animate-pulse rounded bg-gray-100" />
              ) : (
                entries.length > 0 && (
                  <>
                    {/* Phone: the squares are tiny, so just a dot per outfit. */}
                    <span className="flex flex-wrap gap-0.5 sm:hidden" aria-hidden="true">
                      {entries.map((e) => (
                        <span key={e.id} className="h-1.5 w-1.5 rounded-full bg-gray-900" />
                      ))}
                    </span>
                    {/* Wider screens: the outfits' photos. */}
                    <span className="hidden grid-cols-2 gap-1 sm:grid" aria-hidden="true">
                      {entries.slice(0, SHOWN_PER_DAY).map((e) => (
                        <OutfitThumbnail key={e.id} items={e.outfit.items} />
                      ))}
                    </span>
                    {entries.length > SHOWN_PER_DAY && (
                      <span className="hidden text-xs text-gray-500 sm:inline" aria-hidden="true">
                        +{entries.length - SHOWN_PER_DAY} more
                      </span>
                    )}
                  </>
                )
              )}
            </Link>
          )
        })}
      </div>
    </div>
  )
}
