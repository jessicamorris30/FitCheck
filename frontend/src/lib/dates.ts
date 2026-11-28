// Calendar days as plain "YYYY-MM-DD" strings, the same format the backend uses.
//
// Why not just Date? A Date is a moment in time, and `toISOString()` converts it to UTC: at 8 pm
// in Toronto, "today" would already be tomorrow. These helpers only ever use the LOCAL year,
// month and day, so the calendar always shows the day you think it is.

/** A day as "YYYY-MM-DD", from the local date of `date`. */
export function toIsoDay(date: Date): string {
  const y = date.getFullYear()
  const m = String(date.getMonth() + 1).padStart(2, '0')
  const d = String(date.getDate()).padStart(2, '0')
  return `${y}-${m}-${d}`
}

/** "YYYY-MM-DD" → a Date at local midnight that day, or null if it isn't a real day. */
export function parseIsoDay(day: string | null | undefined): Date | null {
  const match = /^(\d{4})-(\d{2})-(\d{2})$/.exec(day ?? '')
  if (!match) return null
  const [y, m, d] = match.slice(1).map(Number)
  const date = new Date(y, m - 1, d)
  // new Date(2026, 1, 30) quietly becomes 2 March; refuse that instead.
  return date.getFullYear() === y && date.getMonth() === m - 1 && date.getDate() === d ? date : null
}

/** A new Date `days` days later (or earlier, if negative). Safe across daylight-saving changes. */
export function addDays(date: Date, days: number): Date {
  return new Date(date.getFullYear(), date.getMonth(), date.getDate() + days)
}

/** The Monday on or before `date` (weeks run Monday to Sunday, like most planners). */
export function startOfWeek(date: Date): Date {
  const daysSinceMonday = (date.getDay() + 6) % 7 // getDay(): Sunday = 0, Monday = 1, ...
  return addDays(date, -daysSinceMonday)
}

/** The 7 days of the week starting at `monday`. */
export function weekDays(monday: Date): Date[] {
  return Array.from({ length: 7 }, (_, i) => addDays(monday, i))
}

/** A month as "YYYY-MM" (local). */
export function toIsoMonth(date: Date): string {
  return toIsoDay(date).slice(0, 7)
}

/** "YYYY-MM" → the 1st of that month (local midnight), or null if it isn't a real month. */
export function parseIsoMonth(month: string | null | undefined): Date | null {
  return parseIsoDay(`${month ?? ''}-01`)
}

/** The 1st of the month `months` months after `firstOfMonth` (negative = earlier). */
export function addMonths(firstOfMonth: Date, months: number): Date {
  return new Date(firstOfMonth.getFullYear(), firstOfMonth.getMonth() + months, 1)
}

/**
 * The days a month view shows: whole weeks (Monday to Sunday) from the week of the 1st to the
 * week of the last day. 4 to 6 weeks, so 28 to 42 days.
 */
export function monthGridDays(firstOfMonth: Date): Date[] {
  const lastOfMonth = new Date(firstOfMonth.getFullYear(), firstOfMonth.getMonth() + 1, 0)
  const start = startOfWeek(firstOfMonth)
  const end = addDays(startOfWeek(lastOfMonth), 6)
  const days: Date[] = []
  for (let d = start; d <= end; d = addDays(d, 1)) days.push(d)
  return days
}

/** "December 2026", in the reader's language. */
export function formatMonth(firstOfMonth: Date): string {
  return firstOfMonth.toLocaleDateString(undefined, { month: 'long', year: 'numeric' })
}

/** "Fri, Dec 4" (or "Fri 4 Dec", depending on the reader's settings) from "YYYY-MM-DD". */
export function formatDayShort(isoDay: string): string {
  const date = parseIsoDay(isoDay)
  return date ? date.toLocaleDateString(undefined, { weekday: 'short', day: 'numeric', month: 'short' }) : isoDay
}

/**
 * The week as a date range in the reader's own style, e.g. "Nov 23 – 29, 2026" (US) or
 * "23–29 Nov 2026" (UK). formatRange leaves out repeated parts (same month, same year) itself.
 */
export function formatWeekRange(monday: Date): string {
  const format = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
  return format.formatRange(monday, addDays(monday, 6))
}
