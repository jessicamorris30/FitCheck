import { useEffect, useState } from 'react'
import { checkUsername, type UsernameCheck } from '../api/auth'

export type UsernameStatus =
  | { state: 'idle' } // empty, or your own current username
  | { state: 'checking' }
  | { state: 'available' }
  | { state: 'unavailable'; message: string }
  | { state: 'unknown' } // the check itself failed (offline, too many checks): the server decides on save

/** How long to wait after the last keystroke before asking the server. */
const DEBOUNCE_MS = 400

/**
 * Checks, while you type, whether a username can be used.
 *
 * Waits until typing pauses ("debouncing"), so "jessica" is one request, not seven. Each answer
 * remembers which username it was for, so a slow answer for "jes" can never be shown as the
 * answer for "jessica".
 *
 * @param ownUsername the user's current username (editing your profile): keeping it is fine
 */
export function useUsernameCheck(value: string, ownUsername?: string | null): UsernameStatus {
  const username = value.trim().toLowerCase()
  const skip = username === '' || username === ownUsername
  const [result, setResult] = useState<(UsernameCheck & { failed?: boolean }) | null>(null)

  useEffect(() => {
    if (skip) return
    let cancelled = false
    const timer = setTimeout(() => {
      checkUsername(username)
        .then((answer) => !cancelled && setResult(answer))
        .catch(() => !cancelled && setResult({ username, available: false, message: null, failed: true }))
    }, DEBOUNCE_MS)
    return () => {
      cancelled = true
      clearTimeout(timer)
    }
  }, [username, skip])

  if (skip) return { state: 'idle' }
  if (result?.username !== username) return { state: 'checking' }
  if (result.failed) return { state: 'unknown' }
  return result.available ? { state: 'available' } : { state: 'unavailable', message: result.message ?? 'Not available' }
}
