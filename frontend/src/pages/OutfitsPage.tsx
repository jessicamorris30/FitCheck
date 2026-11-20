import { Link, useLocation } from 'react-router-dom'
import SavedOutfitsList from '../components/plan/SavedOutfitsList'

/**
 * The Outfits tab: browse everything you've saved.
 * After saving in Plan mode you land here, and the page shows which outfit was just saved
 * (passed along in the navigation "state", not the URL, so a reload doesn't repeat it).
 */
export default function OutfitsPage() {
  const location = useLocation()
  const state = location.state as { justSaved?: string; justDeleted?: string } | null
  const justSaved = state?.justSaved
  const justDeleted = state?.justDeleted

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-wrap items-end justify-between gap-2">
        <h1 className="text-2xl font-semibold">Your outfits</h1>
        <Link to="/plan" className="rounded-lg bg-gray-900 px-4 py-2 font-medium text-white hover:bg-gray-700">
          + New outfit
        </Link>
      </header>

      {justSaved && (
        <p role="status" className="rounded-lg bg-green-50 px-3 py-2 text-sm text-green-800">
          “{justSaved}” saved ✓
        </p>
      )}
      {justDeleted && (
        <p role="status" className="rounded-lg bg-gray-100 px-3 py-2 text-sm text-gray-700">
          “{justDeleted}” deleted.
        </p>
      )}

      <SavedOutfitsList />
    </section>
  )
}
