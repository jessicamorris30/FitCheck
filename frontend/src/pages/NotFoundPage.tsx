import { Link } from 'react-router-dom'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

/** Shown for any address that doesn't match a page. */
export default function NotFoundPage() {
  useDocumentTitle('Page not found')
  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="max-w-sm text-center">
        <p className="text-6xl font-bold text-gray-300">404</p>
        <h1 className="mt-2 text-xl font-semibold">This page isn't in the wardrobe</h1>
        <p className="mt-2 text-sm text-gray-600">The link may be old, or the address may have a typo.</p>
        <Link
          to="/wardrobe"
          className="mt-6 inline-block rounded-lg bg-gray-900 px-4 py-2 font-medium text-white hover:bg-gray-700"
        >
          Go to your wardrobe
        </Link>
      </div>
    </div>
  )
}
