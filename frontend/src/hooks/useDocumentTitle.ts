import { useEffect } from 'react'

/**
 * Sets the browser tab's title, e.g. "Wardrobe · FitCheck".
 * Helps people with many tabs open, and screen readers announce the title when a page changes.
 */
export function useDocumentTitle(title: string) {
  useEffect(() => {
    document.title = title ? `${title} · FitCheck` : 'FitCheck'
  }, [title])
}
