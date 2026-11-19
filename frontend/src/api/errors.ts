import { isAxiosError } from 'axios'
import type { ApiError } from '../types'

/**
 * Turns anything thrown by an API call into something a form can show.
 *
 * The backend always answers errors with the same ApiError JSON, so we read `message` and
 * `fieldErrors` from it. If there's no response at all (backend down, no network) we say so.
 */
export function parseApiError(err: unknown): { message: string; fieldErrors: Record<string, string> } {
  if (isAxiosError<ApiError>(err)) {
    if (!err.response) {
      return { message: "Can't reach the server. Is the backend running?", fieldErrors: {} }
    }
    const data = err.response.data
    return {
      message: data?.message ?? 'Something went wrong',
      fieldErrors: data?.fieldErrors ?? {},
    }
  }
  return { message: 'Something went wrong', fieldErrors: {} }
}
