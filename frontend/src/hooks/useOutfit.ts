import { useEffect, useState } from 'react'
import { parseApiError } from '../api/errors'
import { getOutfit } from '../api/outfits'
import type { Outfit } from '../types'

/**
 * Loads one outfit by id. Shared by the outfit page and the edit page.
 *
 * Returns { loading, outfit, status, error }. A "custom hook" is just a function whose name
 * starts with "use" and that calls other hooks; it lets two pages share the same loading
 * logic instead of copying it. Same "result remembers its request" pattern as elsewhere.
 */
export function useOutfit(id: string) {
  const [result, setResult] = useState<{ id: string; outfit: Outfit | null; status: number; error: string | null } | null>(
    null,
  )

  useEffect(() => {
    let cancelled = false
    getOutfit(id)
      .then((outfit) => !cancelled && setResult({ id, outfit, status: 200, error: null }))
      .catch((err) => {
        const { status, message } = parseApiError(err)
        if (!cancelled) setResult({ id, outfit: null, status, error: message })
      })
    return () => {
      cancelled = true
    }
  }, [id])

  const loading = result?.id !== id
  return {
    loading,
    outfit: loading ? null : (result?.outfit ?? null),
    status: result?.status ?? 0,
    error: result?.error ?? null,
  }
}
