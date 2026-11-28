import { useContext } from 'react'
import { ThemeContext, type ThemeContextValue } from '../context/ThemeContext'

/** Light / dark mode: the user's choice, what's showing, and a way to change it. */
export function useTheme(): ThemeContextValue {
  const value = useContext(ThemeContext)
  if (!value) throw new Error('useTheme must be used inside <ThemeProvider>')
  return value
}
