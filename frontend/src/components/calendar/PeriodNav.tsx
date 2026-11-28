interface PeriodNavProps {
  /** e.g. "Nov 30 – Dec 6, 2026" or "December 2026". */
  label: string
  unit: 'week' | 'month'
  /** True when already showing this week/month (its button is then greyed out). */
  isCurrent: boolean
  onPrevious: () => void
  onCurrent: () => void
  onNext: () => void
}

/** "Nov 30 – Dec 6, 2026   [←] [This week] [→]": shared by the week and month views. */
export default function PeriodNav({ label, unit, isCurrent, onPrevious, onCurrent, onNext }: PeriodNavProps) {
  return (
    <div className="flex flex-wrap items-center justify-between gap-3">
      <p className="text-lg font-medium text-gray-700" aria-live="polite">
        {label}
      </p>
      <nav aria-label={`Change ${unit}`} className="flex items-center gap-2">
        <button
          type="button"
          onClick={onPrevious}
          aria-label={`Previous ${unit}`}
          className="rounded-lg border border-gray-300 px-3 py-1.5 hover:bg-gray-50"
        >
          ←
        </button>
        <button
          type="button"
          onClick={onCurrent}
          disabled={isCurrent}
          className="rounded-lg border border-gray-300 px-3 py-1.5 text-sm font-medium hover:bg-gray-50 disabled:opacity-40"
        >
          This {unit}
        </button>
        <button
          type="button"
          onClick={onNext}
          aria-label={`Next ${unit}`}
          className="rounded-lg border border-gray-300 px-3 py-1.5 hover:bg-gray-50"
        >
          →
        </button>
      </nav>
    </div>
  )
}
