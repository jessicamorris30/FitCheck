import { createContext, useState, type ReactNode } from 'react'
import type { User } from '../types'

export interface AuthContextValue {
  user: User | null
  /** True while the app tries a silent refresh on first load. */
  loading: boolean
  login: (email: string, password: string) => Promise<void>
  register: (email: string, password: string) => Promise<void>
  logout: () => Promise<void>
}

// oxlint-disable-next-line react/only-export-components
export const AuthContext = createContext<AuthContextValue | null>(null)

export function AuthProvider({ children }: { children: ReactNode }) {
  const [user] = useState<User | null>(null)
  const [loading] = useState(false)

  // TODO: on mount, call api/auth.refresh() to restore the session from the refresh cookie.
  // TODO: implement login/register/logout with api/auth.ts and setAccessToken() from api/client.ts.
  const notImplemented = async () => {
    throw new Error('Auth not implemented yet')
  }

  return (
    <AuthContext.Provider
      value={{ user, loading, login: notImplemented, register: notImplemented, logout: notImplemented }}
    >
      {children}
    </AuthContext.Provider>
  )
}
