import type { ThemeChoice } from '../../context/ThemeContext'
import { useTheme } from '../../hooks/useTheme'
import { ThemeIcon } from '../ui/ThemeToggle'

const OPTIONS: { value: ThemeChoice; label: string; hint: string }[] = [
  { value: 'system', label: 'System', hint: 'Follows your device' },
  { value: 'light', label: 'Light', hint: 'Always light' },
  { value: 'dark', label: 'Dark', hint: 'Always dark' },
]

/** Account page: light / dark mode. Saved on the account, so it follows you to other devices. */
export default function AppearanceSection() {
  const { choice, setChoice } = useTheme()

  return (
    <section aria-labelledby="appearance-heading" className="flex flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-6">
      <div>
        <h2 id="appearance-heading" className="text-lg font-semibold">
          Appearance
        </h2>
        <p className="text-sm text-gray-500">Saved to your account, so it follows you to your other devices.</p>
      </div>
      <div role="radiogroup" aria-labelledby="appearance-heading" className="grid grid-cols-3 gap-3">
        {OPTIONS.map((option) => {
          const selected = choice === option.value
          return (
            <button
              key={option.value}
              type="button"
              role="radio"
              aria-checked={selected}
              onClick={() => setChoice(option.value)}
              className={`flex flex-col items-center gap-1 rounded-xl border-2 px-2 py-3 text-sm transition ${
                selected ? 'border-gray-900 bg-gray-50 font-semibold' : 'border-gray-200 text-gray-600 hover:border-gray-400'
              }`}
            >
              <ThemeIcon choice={option.value} className="h-6 w-6" />
              <span>{option.label}</span>
              <span className="text-xs font-normal text-gray-500">{option.hint}</span>
            </button>
          )
        })}
      </div>
    </section>
  )
}
