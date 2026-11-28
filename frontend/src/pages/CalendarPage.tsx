import { useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import MonthView from '../components/calendar/MonthView'
import WeekView from '../components/calendar/WeekView'
import { useDocumentTitle } from '../hooks/useDocumentTitle'
import { addDays, parseIsoDay, parseIsoMonth, startOfWeek, toIsoDay, toIsoMonth } from '../lib/dates'

/**
 * The Calendar tab: planned outfits as a week (default) or a month.
 *
 * Everything about what's shown lives in the address, so reloading, sharing a link or pressing
 * Back keeps your place:
 *   /calendar                         this week
 *   /calendar?week=2026-11-30         that week
 *   /calendar?view=month&month=2026-12  that month
 */
export default function CalendarPage() {
  useDocumentTitle('Calendar')
  const [searchParams] = useSearchParams()
  // "Today" is read once, when the page opens (rendering itself must not depend on the clock).
  const [today] = useState(() => toIsoDay(new Date()))

  const view = searchParams.get('view') === 'month' ? 'month' : 'week'

  // Switching views keeps you near the same dates:
  // week → month: the month most of the shown week falls in (its Thursday's month);
  // month → week: this week if today is in that month, otherwise the month's first week.
  const shownMonday = startOfWeek(parseIsoDay(searchParams.get('week')) ?? parseIsoDay(today)!)
  const monthLink = `/calendar?view=month&month=${toIsoMonth(addDays(shownMonday, 3))}`
  const shownMonth = parseIsoMonth(searchParams.get('month')) ?? parseIsoMonth(today.slice(0, 7))!
  const weekLink =
    toIsoMonth(shownMonth) === today.slice(0, 7) ? '/calendar' : `/calendar?week=${toIsoDay(startOfWeek(shownMonth))}`

  const tab = (active: boolean) =>
    `rounded-md px-3 py-1 text-sm font-medium ${active ? 'bg-white text-gray-900 shadow-sm' : 'text-gray-500 hover:text-gray-900'}`

  return (
    <section className="flex flex-col gap-4">
      <header className="flex flex-wrap items-center justify-between gap-3">
        <h1 className="text-2xl font-semibold">Calendar</h1>
        <nav aria-label="Calendar view" className="flex rounded-lg bg-gray-100 p-1">
          {view === 'week' ? (
            <span aria-current="page" className={tab(true)}>
              Week
            </span>
          ) : (
            <Link to={weekLink} className={tab(false)}>
              Week
            </Link>
          )}
          {view === 'month' ? (
            <span aria-current="page" className={tab(true)}>
              Month
            </span>
          ) : (
            <Link to={monthLink} className={tab(false)}>
              Month
            </Link>
          )}
        </nav>
      </header>

      {view === 'week' ? <WeekView today={today} /> : <MonthView today={today} />}
    </section>
  )
}
