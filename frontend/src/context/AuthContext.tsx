import { createContext, useCallback, useMemo, useState, type ReactNode } from 'react'
import * as authApi from '../api/auth'
import { setAccessToken } from '../api/client'
import type { AuthResponse, User } from '../types'

export interface AuthContextValue {
  /** The logged-in user, or null when logged out. */
  user: User | null
  /** True while the app checks for an existing session on first load (step 7). */
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
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
 * Until step 7, reloading the page forgets the in-memory token, so you appear logged out.
 */
export function AuthProvider({ children }: { children: ReactNode }) {
  const [user, setUser] = useState<User | null>(null)
  const [loading] = useState(false)

  /** Called after any successful login/register/refresh. */
  const startSession = useCallback((response: AuthResponse) => {
    setAccessToken(response.accessToken)
    setUser(response.user)
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

  // useMemo: only hand children a new object when something actually changed,
  // so components using useAuth() don't re-render for no reason.
  const value = useMemo(
    () => ({ user, loading, login, register, logout }),
    [user, loading, login, register, logout],
  )

  return <AuthContext.Provider value={value}>{children}</AuthContext.Provider>
}
