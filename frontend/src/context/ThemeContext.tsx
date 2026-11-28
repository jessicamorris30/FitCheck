import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import { updatePreferences } from '../api/auth'
import { useAuth } from '../hooks/useAuth'

export type ThemeChoice = 'system' | 'light' | 'dark'

export interface ThemeContextValue {
  /** What the user picked ("system" = follow the device). */
  choice: ThemeChoice
  /** What is showing right now. */
  resolved: 'light' | 'dark'
  setChoice: (choice: ThemeChoice) => void
}

// oxlint-disable-next-line react/only-export-components
export const ThemeContext = createContext<ThemeContextValue | null>(null)

const STORAGE_KEY = 'fitcheck-theme' // same key as public/theme-init.js
const DARK_QUERY = '(prefers-color-scheme: dark)'

function readStored(): ThemeChoice {
  try {
    const value = localStorage.getItem(STORAGE_KEY)
    return value === 'light' || value === 'dark' ? value : 'system'
  } catch {
    return 'system'
  }
}

function store(choice: ThemeChoice) {
  try {
    localStorage.setItem(STORAGE_KEY, choice)
  } catch {
    // Storage blocked: the choice still applies until the page is closed.
  }
}

/**
 * Light / dark mode for the whole app (step 7.6).
 *
 * The choice is kept in two places:
 *  - on this device (localStorage), so public/theme-init.js can apply it before the page is drawn;
 *  - on the account (PUT /api/users/me/preferences), so it follows the user to their other devices.
 *    When someone logs in, the account's choice wins and is copied to the device.
 *
 * "system" follows the device's own setting, live: switching the phone to dark mode at sunset
 * switches FitCheck too.
 */
export function ThemeProvider({ children }: { children: ReactNode }) {
  const { user, updateUser } = useAuth()
  const [choice, setChoiceState] = useState<ThemeChoice>(readStored)
  const [systemDark, setSystemDark] = useState(() => window.matchMedia(DARK_QUERY).matches)

  // Follow the device's setting while "system" is chosen.
  useEffect(() => {
    const media = window.matchMedia(DARK_QUERY)
    const onChange = (e: MediaQueryListEvent) => setSystemDark(e.matches)
    media.addEventListener('change', onChange)
    return () => media.removeEventListener('change', onChange)
  }, [])

  // Logged in (or switched account): the account's saved choice wins, and this device remembers it.
  // Only reacts when the ACCOUNT's value changes, so a choice made here isn't undone by a stale copy.
  const accountTheme = user?.theme ?? null
  const [seenAccountTheme, setSeenAccountTheme] = useState<string | null>(null)
  if (accountTheme !== seenAccountTheme) {
    // Adjusting state while rendering (React's recommended alternative to an effect for this).
    setSeenAccountTheme(accountTheme)
    if (accountTheme) setChoiceState(accountTheme as ThemeChoice)
  }

  const resolved: 'light' | 'dark' = choice === 'dark' || (choice === 'system' && systemDark) ? 'dark' : 'light'

  // Apply it: the "dark" class on <html> is what the CSS (index.css) follows.
  useEffect(() => {
    document.documentElement.classList.toggle('dark', resolved === 'dark')
    store(choice)
  }, [resolved, choice])

  const loggedIn = user !== null
  const setChoice = useCallback(
    (next: ThemeChoice) => {
      setChoiceState(next)
      if (loggedIn) {
        // Save to the account in the background; if it fails, this device still has it.
        updatePreferences({ theme: next })
          .then(updateUser)
          .catch(() => {})
      }
    },
    [loggedIn, updateUser],
  )

  const value = useMemo(() => ({ choice, resolved, setChoice }), [choice, resolved, setChoice])
  return <ThemeContext.Provider value={value}>{children}</ThemeContext.Provider>
}
