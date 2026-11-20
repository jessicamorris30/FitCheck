import { isAxiosError } from 'axios'
import type { ApiError } from '../types'

/**
 * Turns anything thrown by an API call into something a page can show.
 *
 * The backend always answers errors with the same ApiError JSON, so we read `message` and
 * `fieldErrors` from it. `status` lets a page react to specific cases (e.g. 404 → "not found").
 * If there's no response at all (backend down, no network) we say so, with status 0.
 */
export function parseApiError(err: unknown): {
  status: number
  message: string
  fieldErrors: Record<string, string>
} {
  if (isAxiosError<ApiError>(err)) {
    if (!err.response) {
      return { status: 0, message: "Can't reach the server. Is the backend running?", fieldErrors: {} }
    }
    const data = err.response.data
    return {
      status: err.response.status,
      message: data?.message ?? 'Something went wrong',
      fieldErrors: data?.fieldErrors ?? {},
    }
  }
  return { status: 0, message: 'Something went wrong', fieldErrors: {} }
}
