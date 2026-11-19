/** Shown while the app checks whether you're already logged in. */
export default function FullPageSpinner() {
  return (
    <div className="flex min-h-screen items-center justify-center" role="status" aria-label="Loading">
      <div className="h-8 w-8 animate-spin rounded-full border-4 border-gray-200 border-t-gray-900" />
    </div>
  )
}
