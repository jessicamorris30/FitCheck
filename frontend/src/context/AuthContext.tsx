import { createContext, useCallback, useEffect, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import { refreshSession, setAccessToken, setSessionExpiredHandler } from '../api/client'
import type { AuthResponse, User } from '../types'

export interface AuthContextValue {
  /** The logged-in user, or null when logged out. */
  user: User | null
  /** True while the app checks for an existing session on first load. */
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
  /** Replace the stored user, e.g. after the email is confirmed. Ignored if a different user. */
  updateUser: (user: User) => void
  /** Change the password; this device stays logged in with the fresh session the server returns. */
  changePassword: (currentPassword: string, newPassword: string) => Promise<void>
}

// oxlint-disable-next-line react/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null)

/**
 * Holds "who is logged in" for the whole app. Any component can read it with useAuth().
 *
 * Where each token lives:
 *  - access token → only in memory, inside api/client.ts (setAccessToken). Never localStorage,
 *    so a malicious script can't steal a stored copy.
 *  - refresh token → HttpOnly cookie set by the backend. This code never sees it.
 *
 * Because memory is wiped on page reload, the app restores the session on startup by
 * asking the backend for a fresh access token using the refresh cookie ("silent refresh").
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading, setLoading] = useState(true)

  /** Called after any successful login/register/refresh. */
  const startSession = useCallback((response: AuthResponse) => {
    setAccessToken(response.accessToken)
    setUser(response.user)
  }, [])

  // On first load: try to restore the session from the refresh cookie.
  useEffect(() => {
    let cancelled = false
    refreshSession()
      .then((response) => {
        if (!cancelled) startSession(response)
      })
      .catch(() => {
        // No cookie, or it expired/was revoked: simply logged out. Not an error.
      })
      .finally(() => {
        if (!cancelled) setLoading(false)
      })
    // In development React StrictMode runs this effect twice on purpose. Both runs share the
    // same refreshSession() request (see client.ts), so the cookie is still only used once.
    return () => {
      cancelled = true
    }
  }, [startSession])

  // If a refresh fails mid-session (expired after 14 days, revoked, logged out elsewhere),
  // the API client calls this and we drop the user. ProtectedRoute then redirects to /login.
  useEffect(() => {
    setSessionExpiredHandler(() => setUser(null))
    return () => setSessionExpiredHandler(() => {})
  }, [])

  const login = useCallback(
    async (email: string, password: string) => {
      startSession(await authApi.login(email, password))
    },
    [startSession],
  )

  const register = useCallback(
    async (email: string, password: string) => {
      startSession(await authApi.register(email, password))
    },
    [startSession],
  )

  const logout = useCallback(async () => {
    try {
      await authApi.logout() // revokes the session on the server and clears the cookie
    } finally {
      // Forget the user locally even if the server call failed.
      setAccessToken(null)
      setUser(null)
    }
  }, [])

  const updateUser = useCallback((updated: User) => {
    // Only update if it's the same person who is logged in (the link may be opened while
    // logged in as someone else, or not logged in at all).
    setUser((current) => (current && current.id === updated.id ? updated : current))
  }, [])

  const changePassword = useCallback(
    async (currentPassword: string, newPassword: string) => {
      // The old session was revoked on the server; switch to the new one it handed back.
      startSession(await authApi.changePassword(currentPassword, newPassword))
    },
    [startSession],
  )

  // useMemo: only hand children a new object when something actually changed,
  // so components using useAuth() don't re-render for no reason.
  const value = useMemo(
    () => ({ user, loading, login, register, logout, updateUser, changePassword }),
    [user, loading, login, register, logout, updateUser, changePassword],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
