import { Link, useParams } from 'react-router-dom'
import FullPageSpinner from '../components/ui/FullPageSpinner'
import { useOutfit } from '../hooks/useOutfit'
import PlanPage from './PlanPage'

/**
 * /outfits/:id/edit: loads the outfit, then hands it to the same two-step Plan flow used for
 * creating, pre-filled. One editor for both jobs, so they always behave the same.
 */
export default function EditOutfitPage() {
  const { id = '' } = useParams()
  const { loading, outfit, status, error } = useOutfit(id)

  if (loading) return <FullPageSpinner />

  if (!outfit) {
    return (
      <div className="rounded-xl border border-dashed border-gray-300 p-12 text-center">
        <p className="font-medium">{status === 404 ? 'This outfit could not be found.' : error}</p>
        <Link to="/outfits" className="mt-3 inline-block text-sm underline">
          Back to your outfits
        </Link>
      </div>
    )
  }

  // key: if the id in the URL changes, start a brand-new editor instead of reusing the old one's state.
  return <PlanPage key={outfit.id} existing={outfit} />
}
