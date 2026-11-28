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

/**
 * The week as a date range in the reader's own style, e.g. "Nov 23 – 29, 2026" (US) or
 * "23–29 Nov 2026" (UK). formatRange leaves out repeated parts (same month, same year) itself.
 */
export function formatWeekRange(monday: Date): string {
  const format = new Intl.DateTimeFormat(undefined, { day: 'numeric', month: 'short', year: 'numeric' })
  return format.formatRange(monday, addDays(monday, 6))
}
