import { useEffect, useState, type FormEvent } from 'react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { deleteClothingItem, getClothingItem, updateClothingItem } from '../api/clothing'
import { parseApiError } from '../api/errors'
import Button from '../components/ui/Button'
import ClothingDetailsFields from '../components/wardrobe/ClothingDetailsFields'
import { categoryLabel } from '../lib/categories'
import { formValuesFromItem, toClothingDetails, type DetailsFormValues } from '../lib/clothingForm'
import type { ClothingItem } from '../types'

type Mode = 'view' | 'edit' | 'confirm-delete'

/**
 * /wardrobe/:id: one item with a large photo, its details, and Edit / Delete.
 *
 * The page switches between three modes instead of opening pop-ups:
 * view → edit (inline form) and view → confirm-delete (inline "are you sure?").
 */
export default function ClothingDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const location = useLocation()
  const backTo = (location.state as { from?: string } | null)?.from ?? '/wardrobe'

  // Same pattern as WardrobePage: each result remembers which item id it was for, and
  // "loading" is derived from that instead of being stored separately.
  const [result, setResult] = useState<{ id: string; item: ClothingItem | null; status: number; error: string | null } | null>(null)
  const loading = result?.id !== id

  useEffect(() => {
    let cancelled = false
    getClothingItem(id)
      .then((item) => {
        if (!cancelled) setResult({ id, item, status: 200, error: null })
      })
      .catch((err) => {
        const { status, message } = parseApiError(err)
        if (!cancelled) setResult({ id, item: null, status, error: message })
      })
    return () => {
      cancelled = true
    }
  }, [id])

  const [mode, setMode] = useState<Mode>('view')
  const [form, setForm] = useState<DetailsFormValues | null>(null)
  const [busy, setBusy] = useState(false)
  const [actionError, setActionError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  const backLink = (
    <Link to={backTo} className="text-sm text-gray-500 hover:text-gray-900">
      ← Back to wardrobe
    </Link>
  )

  if (loading) {
    return (
      <section className="flex flex-col gap-6" aria-busy="true">
        {backLink}
        <div className="grid animate-pulse gap-8 md:grid-cols-2">
          <div className="aspect-[3/4] rounded-2xl bg-gray-100" />
          <div className="space-y-3">
            <div className="h-8 w-2/3 rounded bg-gray-100" />
            <div className="h-4 w-1/3 rounded bg-gray-100" />
          </div>
        </div>
      </section>
    )
  }

  const item = result?.item
  if (!item) {
    // 404 covers both "doesn't exist" and "isn't yours": the backend deliberately doesn't say which.
    return (
      <section className="flex flex-col gap-6">
        {backLink}
        <div className="rounded-xl border border-dashed border-gray-300 p-12 text-center">
          <p className="font-medium">{result?.status === 404 ? 'This item could not be found.' : result?.error}</p>
          <p className="mt-1 text-sm text-gray-500">It may have been deleted.</p>
        </div>
      </section>
    )
  }

  function startEdit() {
    setForm(formValuesFromItem(item!))
    setFieldErrors({})
    setActionError(null)
    setMode('edit')
  }

  async function handleSave(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!form) return
    setBusy(true)
    setActionError(null)
    setFieldErrors({})
    try {
      const updated = await updateClothingItem(item!.id, toClothingDetails(form))
      setResult({ id, item: updated, status: 200, error: null })
      setMode('view')
    } catch (err) {
      const parsed = parseApiError(err)
      setActionError(parsed.message)
      setFieldErrors(parsed.fieldErrors)
    } finally {
      setBusy(false)
    }
  }

  async function handleDelete() {
    setBusy(true)
    setActionError(null)
    try {
      await deleteClothingItem(item!.id)
      // replace: the deleted item's page shouldn't be reachable with the Back button.
      navigate(backTo, { replace: true })
    } catch (err) {
      setActionError(parseApiError(err).message)
      setBusy(false)
    }
  }

  return (
    <section className="flex flex-col gap-6">
      {backLink}

      <div className="grid gap-8 md:grid-cols-2">
        <div className="overflow-hidden rounded-2xl border border-gray-200 bg-gray-100">
          <img src={item.imageUrl} alt={item.name} className="aspect-[3/4] w-full object-cover" />
        </div>

        <div className="flex flex-col gap-6">
          {actionError && (
            <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
              {actionError}
            </p>
          )}

          {mode === 'edit' && form ? (
            <form onSubmit={handleSave} noValidate className="flex flex-col gap-6">
              <h1 className="text-2xl font-semibold">Edit item</h1>
              <ClothingDetailsFields values={form} onChange={setForm} errors={fieldErrors} disabled={busy} />
              <div className="flex gap-3">
                <Button type="submit" loading={busy}>
                  Save changes
                </Button>
                <button
                  type="button"
                  onClick={() => setMode('view')}
                  disabled={busy}
                  className="rounded-lg px-4 py-2 font-medium text-gray-700 hover:bg-gray-100 disabled:opacity-40"
                >
                  Cancel
                </button>
              </div>
            </form>
          ) : (
            <>
              <div>
                <h1 className="text-3xl font-semibold">{item.name}</h1>
                <p className="mt-1 text-gray-500">
                  {categoryLabel(item.category)}
                  {item.color && ` · ${item.color}`}
                </p>
              </div>

              {item.tags.length > 0 && (
                <ul className="flex flex-wrap gap-2" aria-label="Tags">
                  {item.tags.map((tag) => (
                    <li key={tag} className="rounded-full bg-gray-100 px-3 py-1 text-sm text-gray-700">
                      {tag}
                    </li>
                  ))}
                </ul>
              )}

              <p className="text-sm text-gray-500">
                Added {new Date(item.createdAt).toLocaleDateString(undefined, { dateStyle: 'long' })}
              </p>

              {mode === 'confirm-delete' ? (
                <div role="alertdialog" aria-label="Confirm delete" className="rounded-xl border border-red-200 bg-red-50 p-4">
                  <p className="font-medium text-red-800">Delete “{item.name}”?</p>
                  <p className="mt-1 text-sm text-red-700">The photo will be removed too. This can't be undone.</p>
                  <div className="mt-4 flex gap-3">
                    <button
                      onClick={handleDelete}
                      disabled={busy}
                      className="rounded-lg bg-red-600 px-4 py-2 font-medium text-white hover:bg-red-700 disabled:opacity-60"
                    >
                      {busy ? 'Deleting…' : 'Delete'}
                    </button>
                    <button
                      onClick={() => setMode('view')}
                      disabled={busy}
                      className="rounded-lg px-4 py-2 font-medium text-gray-700 hover:bg-white disabled:opacity-40"
                    >
                      Keep it
                    </button>
                  </div>
                </div>
              ) : (
                <div className="flex gap-3">
                  <Button onClick={startEdit}>Edit</Button>
                  <button
                    onClick={() => setMode('confirm-delete')}
                    className="rounded-lg border border-red-200 px-4 py-2 font-medium text-red-700 hover:bg-red-50"
                  >
                    Delete
                  </button>
                </div>
              )}
            </>
          )}
        </div>
      </div>
    </section>
  )
}
