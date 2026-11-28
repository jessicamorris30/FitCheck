import { useState } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { parseApiError } from '../api/errors'
import { deleteOutfit } from '../api/outfits'
import OutfitCalendarPanel from '../components/calendar/OutfitCalendarPanel'
import Button from '../components/ui/Button'
import ClothingGrid, { ClothingGridSkeleton } from '../components/wardrobe/ClothingGrid'
import { useOutfit } from '../hooks/useOutfit'

/**
 * /outfits/:id: one saved outfit with its items in order, when it's on the calendar
 * ("Plan this outfit"), plus Edit and Delete.
 * Delete asks for confirmation inside the page (same pattern as the clothing detail page).
 */
export default function OutfitDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const justSaved = (location.state as { justSaved?: string } | null)?.justSaved

  const { loading, outfit, status, error } = useOutfit(id)
  const [confirming, setConfirming] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [deleteError, setDeleteError] = useState<string | null>(null)

  const backLink = (
    <Link to="/outfits" className="text-sm text-gray-500 hover:text-gray-900">
      ← Back to outfits
    </Link>
  )

  if (loading) {
    return (
      <section className="flex flex-col gap-6" aria-busy="true">
        {backLink}
        <div className="h-8 w-1/3 animate-pulse rounded bg-gray-100" />
        <ClothingGridSkeleton count={4} />
      </section>
    )
  }

  if (!outfit) {
    // 404 covers "doesn't exist" and "isn't yours": the backend deliberately doesn't say which.
    return (
      <section className="flex flex-col gap-6">
        {backLink}
        <div className="rounded-xl border border-dashed border-gray-300 p-12 text-center">
          <p className="font-medium">{status === 404 ? 'This outfit could not be found.' : error}</p>
          <p className="mt-1 text-sm text-gray-500">It may have been deleted.</p>
        </div>
      </section>
    )
  }

  async function handleDelete() {
    setDeleting(true)
    setDeleteError(null)
    try {
      await deleteOutfit(outfit!.id)
      // replace: the deleted outfit's page shouldn't be reachable with the Back button.
      navigate('/outfits', { replace: true, state: { justDeleted: outfit!.name } })
    } catch (err) {
      setDeleteError(parseApiError(err).message)
      setDeleting(false)
    }
  }

  return (
    <section className="flex flex-col gap-6">
      {backLink}

      {justSaved && (
        <p role="status" className="rounded-lg bg-green-50 px-3 py-2 text-sm text-green-800">
          Changes to “{justSaved}” saved ✓
        </p>
      )}

      <header className="flex flex-wrap items-start justify-between gap-4">
        <div>
          <h1 className="text-3xl font-semibold">{outfit.name}</h1>
          <p className="mt-1 text-sm text-gray-500">
            {outfit.items.length} {outfit.items.length === 1 ? 'item' : 'items'} · Updated{' '}
            {new Date(outfit.updatedAt).toLocaleDateString(undefined, { dateStyle: 'long' })}
          </p>
          {outfit.aiPrompt && (
            <p className="mt-2 inline-block rounded-lg bg-violet-50 px-3 py-1 text-sm text-violet-900">
              ✨ Suggested by the AI stylist for “{outfit.aiPrompt}”
            </p>
          )}
          {outfit.notes && <p className="mt-3 max-w-prose whitespace-pre-line text-gray-700">{outfit.notes}</p>}
        </div>

        {!confirming && (
          <div className="flex gap-3">
            <Button onClick={() => navigate(`/outfits/${outfit.id}/edit`)}>Edit</Button>
            <button
              onClick={() => setConfirming(true)}
              className="rounded-lg border border-red-200 px-4 py-2 font-medium text-red-700 hover:bg-red-50"
            >
              Delete
            </button>
          </div>
        )}
      </header>

      {confirming && (
        <div role="alertdialog" aria-label="Confirm delete" className="rounded-xl border border-red-200 bg-red-50 p-4">
          <p className="font-medium text-red-800">Delete the outfit “{outfit.name}”?</p>
          <p className="mt-1 text-sm text-red-700">Your clothes stay in your wardrobe. Only this outfit is removed.</p>
          {deleteError && <p className="mt-2 text-sm text-red-800">{deleteError}</p>}
          <div className="mt-4 flex gap-3">
            <button
              onClick={handleDelete}
              disabled={deleting}
              className="rounded-lg bg-red-600 px-4 py-2 font-medium text-white hover:bg-red-700 disabled:opacity-60"
            >
              {deleting ? 'Deleting…' : 'Delete outfit'}
            </button>
            <button
              onClick={() => setConfirming(false)}
              disabled={deleting}
              className="rounded-lg px-4 py-2 font-medium text-gray-700 hover:bg-white disabled:opacity-40"
            >
              Keep it
            </button>
          </div>
        </div>
      )}

      <OutfitCalendarPanel outfitId={outfit.id} />

      {/* The items in the outfit's order. Each card opens that piece of clothing. */}
      <ClothingGrid items={outfit.items} />
    </section>
  )
}
