import { useEffect, useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { listOutfitDays, planOutfit } from '../../api/calendar'
import { parseApiError } from '../../api/errors'
import { formatDayShort, parseIsoDay, startOfWeek, toIsoDay } from '../../lib/dates'
import type { OutfitDay } from '../../types'

/** At most this many upcoming days are listed; the calendar has the rest. */
const SHOWN_UPCOMING = 6

/** The calendar week (in the Calendar tab) that a "YYYY-MM-DD" day falls in. */
const weekLinkFor = (isoDay: string) => `/calendar?week=${toIsoDay(startOfWeek(parseIsoDay(isoDay)!))}`

/**
 * "On your calendar" on an outfit's page: when it's planned next, when it was last worn, and a
 * "Plan this outfit" button that puts it on a chosen day.
 */
export default function OutfitCalendarPanel({ outfitId }: { outfitId: string }) {
  const [today] = useState(() => toIsoDay(new Date()))
  const [days, setDays] = useState<OutfitDay[] | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)

  const [planning, setPlanning] = useState(false)
  const [date, setDate] = useState(today)
  const [saving, setSaving] = useState(false)
  const [formError, setFormError] = useState<string | null>(null)
  const [justAdded, setJustAdded] = useState<string | null>(null)

  useEffect(() => {
    let cancelled = false
    listOutfitDays(outfitId)
      .then((list) => !cancelled && setDays(list))
      .catch((err) => !cancelled && setLoadError(parseApiError(err).message))
    return () => {
      cancelled = true
    }
  }, [outfitId])

  async function handleSubmit(e: FormEvent) {
    e.preventDefault()
    if (!parseIsoDay(date)) {
      setFormError('Choose a date')
      return
    }
    setSaving(true)
    setFormError(null)
    try {
      const entry = await planOutfit(date, outfitId)
      setDays((prev) => [...(prev ?? []), { id: entry.id, date: entry.date }].sort((a, b) => a.date.localeCompare(b.date)))
      setJustAdded(entry.date)
      setPlanning(false)
    } catch (err) {
      setFormError(parseApiError(err).message)
    } finally {
      setSaving(false)
    }
  }

  // "YYYY-MM-DD" strings sort and compare like dates, so plain string comparison works here.
  const upcoming = days?.filter((d) => d.date >= today) ?? []
  const lastWorn = days?.filter((d) => d.date < today).at(-1)

  return (
    <section aria-labelledby="outfit-calendar-title" className="flex flex-col gap-3 rounded-xl border border-gray-200 p-4">
      <div className="flex flex-wrap items-center justify-between gap-3">
        <h2 id="outfit-calendar-title" className="font-semibold">
          On your calendar
        </h2>
        {!planning && (
          <button
            type="button"
            onClick={() => {
              setPlanning(true)
              setJustAdded(null)
              setFormError(null)
            }}
            className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium hover:bg-gray-50"
          >
            📅 Plan this outfit
          </button>
        )}
      </div>

      {planning && (
        <form onSubmit={handleSubmit} noValidate className="flex flex-wrap items-end gap-3">
          <label className="flex flex-col gap-1 text-sm">
            <span className="font-medium">Wear it on</span>
            <input
              type="date"
              value={date}
              min="2000-01-01"
              max="2100-12-31"
              onChange={(e) => setDate(e.target.value)}
              className="rounded-lg border border-gray-300 px-3 py-1.5"
              required
            />
          </label>
          <button
            type="submit"
            disabled={saving}
            className="rounded-lg bg-gray-900 px-4 py-1.5 font-medium text-white hover:bg-gray-700 disabled:opacity-60"
          >
            {saving ? 'Adding…' : 'Add to calendar'}
          </button>
          <button
            type="button"
            onClick={() => setPlanning(false)}
            disabled={saving}
            className="px-2 py-1.5 text-sm font-medium text-gray-600 hover:text-gray-900"
          >
            Cancel
          </button>
          {formError && (
            <p role="alert" className="w-full text-sm text-red-700">
              {formError}
            </p>
          )}
        </form>
      )}

      {justAdded && (
        <p role="status" className="rounded-lg bg-green-50 px-3 py-2 text-sm text-green-800">
          Added to {formatDayShort(justAdded)} ✓{' '}
          <Link to={weekLinkFor(justAdded)} className="font-medium underline">
            See that week
          </Link>
        </p>
      )}

      {loadError && <p className="text-sm text-red-700">{loadError}</p>}

      {days === null && !loadError && <div className="h-5 w-1/2 animate-pulse rounded bg-gray-100" />}

      {days !== null && (
        <div className="flex flex-col gap-2 text-sm">
          {upcoming.length > 0 ? (
            <div className="flex flex-wrap items-center gap-2">
              <span className="text-gray-500">Planned:</span>
              {upcoming.slice(0, SHOWN_UPCOMING).map((d) => (
                <Link
                  key={d.id}
                  to={weekLinkFor(d.date)}
                  className="rounded-full bg-gray-100 px-2.5 py-0.5 font-medium hover:bg-gray-200"
                >
                  {d.date === today ? 'Today' : formatDayShort(d.date)}
                </Link>
              ))}
              {upcoming.length > SHOWN_UPCOMING && (
                <span className="text-gray-500">and {upcoming.length - SHOWN_UPCOMING} more</span>
              )}
            </div>
          ) : (
            <p className="text-gray-500">Not planned for any upcoming day.</p>
          )}
          {lastWorn && (
            <p className="text-gray-500">
              Last worn:{' '}
              <Link to={weekLinkFor(lastWorn.date)} className="font-medium text-gray-700 hover:underline">
                {formatDayShort(lastWorn.date)}
              </Link>
            </p>
          )}
        </div>
      )}
    </section>
  )
}
