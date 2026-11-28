import type { ThemeChoice } from '../../context/ThemeContext'
import { useTheme } from '../../hooks/useTheme'

const NEXT: Record<ThemeChoice, ThemeChoice> = { system: 'light', light: 'dark', dark: 'system' }
const LABEL: Record<ThemeChoice, string> = { system: 'System', light: 'Light', dark: 'Dark' }

/** Small icons drawn with lines, so they take the text colour in both modes. */
export function ThemeIcon({ choice, className = 'h-5 w-5' }: { choice: ThemeChoice; className?: string }) {
  const common = {
    className,
    viewBox: '0 0 24 24',
    fill: 'none',
    stroke: 'currentColor',
    strokeWidth: 1.8,
    strokeLinecap: 'round' as const,
    strokeLinejoin: 'round' as const,
    'aria-hidden': true,
  }
  if (choice === 'light') {
    return (
      <svg {...common}>
        <circle cx="12" cy="12" r="4" />
        <path d="M12 2v2M12 20v2M4.9 4.9l1.4 1.4M17.7 17.7l1.4 1.4M2 12h2M20 12h2M4.9 19.1l1.4-1.4M17.7 6.3l1.4-1.4" />
      </svg>
    )
  }
  if (choice === 'dark') {
    return (
      <svg {...common}>
        <path d="M21 12.8A9 9 0 1 1 11.2 3a7 7 0 0 0 9.8 9.8z" />
      </svg>
    )
  }
  return (
    <svg {...common}>
      <rect x="3" y="4" width="18" height="12" rx="2" />
      <path d="M8 20h8M12 16v4" />
    </svg>
  )
}

/**
 * One button that cycles System → Light → Dark. In the top bar and on the logged-out pages.
 * (The Account page has the same choice as three buttons.)
 */
export default function ThemeToggle() {
  const { choice, setChoice } = useTheme()
  const next = NEXT[choice]
  return (
    <button
      type="button"
      onClick={() => setChoice(next)}
      title={`Theme: ${LABEL[choice]} (click for ${LABEL[next]})`}
      aria-label={`Theme: ${LABEL[choice]}. Switch to ${LABEL[next]}`}
      className="rounded-lg p-1.5 text-gray-500 hover:bg-gray-100 hover:text-gray-900"
    >
      <ThemeIcon choice={choice} />
    </button>
  )
}
