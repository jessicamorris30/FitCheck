import { useEffect, useState } from 'react'
import { useSearchParams } from 'react-router-dom'
import { listPlanned, planOutfit, removePlanned } from '../../api/calendar'
import { parseApiError } from '../../api/errors'
import { addDays, formatWeekRange, parseIsoDay, startOfWeek, toIsoDay, weekDays } from '../../lib/dates'
import type { Outfit, PlannedOutfit } from '../../types'
import DayColumn from './DayColumn'
import OutfitPickerDialog from './OutfitPickerDialog'
import PeriodNav from './PeriodNav'

/**
 * One week (Monday to Sunday) of planned outfits, where outfits are added and removed.
 * The week lives in the address (?week=2026-11-30); without it, the current week is shown.
 */
export default function WeekView({ today }: { today: string }) {
  const [searchParams, setSearchParams] = useSearchParams()

  // Any day in the address is snapped to its Monday, so ?week=2026-12-03 shows Nov 30 – Dec 6.
  const monday = startOfWeek(parseIsoDay(searchParams.get('week')) ?? parseIsoDay(today)!)
  const mondayIso = toIsoDay(monday)
  const days = weekDays(monday)
  const sundayIso = toIsoDay(days[6])
  const isThisWeek = mondayIso === toIsoDay(startOfWeek(parseIsoDay(today)!))

  const [loaded, setLoaded] = useState<{ week: string; entries: PlannedOutfit[]; error: string | null } | null>(null)
  const [actionError, setActionError] = useState<string | null>(null)
  const [pickerDay, setPickerDay] = useState<Date | null>(null)
  const [removing, setRemoving] = useState<Set<string>>(new Set())

  useEffect(() => {
    let cancelled = false
    listPlanned(mondayIso, sundayIso)
      .then((entries) => !cancelled && setLoaded({ week: mondayIso, entries, error: null }))
      .catch((err) => !cancelled && setLoaded({ week: mondayIso, entries: [], error: parseApiError(err).message }))
    return () => {
      cancelled = true
    }
  }, [mondayIso, sundayIso])

  // While the next week loads, don't show the previous week's outfits under the new dates.
  const current = loaded?.week === mondayIso ? loaded : null

  function goToWeek(newMonday: Date | null) {
    setActionError(null)
    setSearchParams(newMonday ? { week: toIsoDay(newMonday) } : {})
  }

  async function addToDay(day: Date, outfit: Outfit) {
    const entry = await planOutfit(toIsoDay(day), outfit.id) // errors are shown inside the picker
    setLoaded((prev) => (prev && prev.week === mondayIso ? { ...prev, entries: [...prev.entries, entry] } : prev))
    setPickerDay(null)
  }

  async function remove(entry: PlannedOutfit) {
    setActionError(null)
    setRemoving((prev) => new Set(prev).add(entry.id))
    try {
      await removePlanned(entry.id)
      setLoaded((prev) => (prev ? { ...prev, entries: prev.entries.filter((e) => e.id !== entry.id) } : prev))
    } catch (err) {
      setActionError(`Couldn't remove “${entry.outfit.name}”: ${parseApiError(err).message}`)
    } finally {
      setRemoving((prev) => {
        const next = new Set(prev)
        next.delete(entry.id)
        return next
      })
    }
  }

  const pickerDayIso = pickerDay ? toIsoDay(pickerDay) : null
  const entriesOn = (iso: string) => current?.entries.filter((e) => e.date === iso) ?? []

  return (
    <div className="flex flex-col gap-4">
      <PeriodNav
        label={formatWeekRange(monday)}
        unit="week"
        isCurrent={isThisWeek}
        onPrevious={() => goToWeek(addDays(monday, -7))}
        onCurrent={() => goToWeek(null)}
        onNext={() => goToWeek(addDays(monday, 7))}
      />

      {(current?.error || actionError) && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {current?.error ?? actionError}
        </p>
      )}

      {current && !current.error && current.entries.length === 0 && (
        <p className="text-sm text-gray-500">
          Nothing planned this week yet. Tap <span className="font-medium text-gray-700">+ Add</span> on any day, or use{' '}
          <span className="font-medium text-gray-700">Plan this outfit</span> on an outfit's page.
        </p>
      )}

      <div className="grid grid-cols-1 gap-3 sm:grid-cols-2 lg:grid-cols-7" aria-busy={current === null}>
        {days.map((day) => {
          const iso = toIsoDay(day)
          return current === null ? (
            <div key={iso} className="h-32 animate-pulse rounded-xl bg-gray-100" />
          ) : (
            <DayColumn
              key={iso}
              day={day}
              isToday={iso === today}
              entries={entriesOn(iso)}
              removing={removing}
              onAdd={() => setPickerDay(day)}
              onRemove={remove}
            />
          )
        })}
      </div>

      {pickerDay && pickerDayIso && (
        <OutfitPickerDialog
          dayLabel={pickerDay.toLocaleDateString(undefined, { weekday: 'long', day: 'numeric', month: 'long' })}
          alreadyPlanned={new Set(entriesOn(pickerDayIso).map((e) => e.outfit.id))}
          onPick={(outfit) => addToDay(pickerDay, outfit)}
          onClose={() => setPickerDay(null)}
        />
      )}
    </div>
  )
}
