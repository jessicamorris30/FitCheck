import axios from 'axios'

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

let accessToken: string | null = null

export function setAccessToken(token: string | null) {
  accessToken = token
}

export function getAccessToken() {
  return accessToken
}

api.interceptors.request.use((config) => {
  if (accessToken) {
    config.headers.Authorization = `Bearer ${accessToken}`
  }
  return config
})

// TODO: response interceptor: on 401, call POST /auth/refresh once (dedupe concurrent
// refreshes), store the new access token, and retry the original request. On refresh
// failure, clear the token and send the user to /login.
