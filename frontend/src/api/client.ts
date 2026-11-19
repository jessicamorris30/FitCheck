import axios, { type InternalAxiosRequestConfig } from 'axios'
import type { AuthResponse } from '../types'

/**
 * Shared axios instance for all backend calls.
 *
 * Auth model (docs/AUTH.md):
 *  - The short-lived access token lives only in memory (never localStorage).
 *  - The refresh token is an HttpOnly cookie the browser sends automatically to /api/auth/*.
 */
export const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE_URL ?? '/api',
  withCredentials: true,
})

// ---- access token (memory only) ----

let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

// ---- session refresh ----

/**
 * The refresh call currently in progress, if any.
 *
 * Why this matters: each refresh token works exactly once (rotation), and the backend treats a
 * second use as theft and logs the user out. If three requests fail with 401 at the same moment,
 * or React StrictMode runs the startup effect twice, we must still call /auth/refresh only ONCE.
 * Everyone who needs a refresh waits on this same promise.
 */
let refreshInFlight: Promise<AuthResponse> | null = null

/** Gets a new access token from the refresh cookie. Concurrent callers share one request. */
export function refreshSession(): Promise<AuthResponse> {
  if (!refreshInFlight) {
    refreshInFlight = api
      .post<AuthResponse>('/auth/refresh')
      .then(({ data }) => {
        setAccessToken(data.accessToken)
        return data
      })
      .finally(() => {
        refreshInFlight = null
      })
  }
  return refreshInFlight
}

/** AuthProvider registers this so the app can react (log the user out) when a session dies. */
let onSessionExpired: () => void = () => {}

export function setSessionExpiredHandler(handler: () => void) {
  onSessionExpired = handler
}

// ---- interceptors ----

// Before every request: attach the access token, if we have one.
api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

/** Marks a request we've already retried once, so a second 401 doesn't loop forever. */
interface RetriableConfig extends InternalAxiosRequestConfig {
  _retried?: boolean
}

// After every response: on 401, refresh the session once and replay the original request.
api.interceptors.response.use(
  (response) => response,
  async (error) => {
    const original = error.config as RetriableConfig | undefined
    const isAuthCall = original?.url?.startsWith('/auth/') ?? false

    // Only handle a 401 from a normal API call that we haven't already retried.
    // (A 401 from /auth/login or /auth/refresh means "bad credentials" or "no session",
    // and trying to refresh in response would just loop.)
    if (error.response?.status !== 401 || !original || original._retried || isAuthCall) {
      return Promise.reject(error)
    }

    original._retried = true
    try {
      const { accessToken: fresh } = await refreshSession()
      original.headers.Authorization = `Bearer ${fresh}`
      return api(original) // replay the request; the caller never knows it failed
    } catch {
      setAccessToken(null)
      onSessionExpired()
      return Promise.reject(error)
    }
  },
)
