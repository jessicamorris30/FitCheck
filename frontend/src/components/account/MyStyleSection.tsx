import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { parseApiError } from '../../api/errors'
import { addStylePhoto, deleteStylePhoto, getStyle, saveStyle } from '../../api/style'
import type { Style } from '../../types'
import Button from '../ui/Button'

const MAX_BYTES = 10 * 1024 * 1024 // must match the backend's 10 MB limit
const NOTE_MAX = 1000
const ACCEPTED = 'image/jpeg,image/png,image/webp'

/**
 * Account page: "My style". Up to 5 photos and/or a few words describing the user's style, and
 * a switch for whether the AI stylist takes it into account. The stylist's own notes on the
 * style are private to the AI (never shown here, by design).
 */
export default function MyStyleSection({ emailVerified }: { emailVerified: boolean }) {
  const [style, setStyle] = useState<Style | null>(null)
  const [loadError, setLoadError] = useState<string | null>(null)
  const [note, setNote] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [savingNote, setSavingNote] = useState(false)
  const [noteSaved, setNoteSaved] = useState(false)
  const [switching, setSwitching] = useState(false)
  const [uploading, setUploading] = useState<{ done: number; total: number } | null>(null)
  const [removing, setRemoving] = useState<string | null>(null)
  const fileInput = useRef<HTMLInputElement>(null)

  useEffect(() => {
    let cancelled = false
    getStyle()
      .then((loaded) => {
        if (cancelled) return
        setStyle(loaded)
        setNote(loaded.note ?? '')
        // Arrived from the stylist's "Change / Add it" link: bring this section into view.
        if (window.location.hash === '#my-style') {
          requestAnimationFrame(() => document.getElementById('my-style')?.scrollIntoView({ behavior: 'smooth' }))
        }
      })
      .catch((err) => !cancelled && setLoadError(parseApiError(err).message))
    return () => {
      cancelled = true
    }
  }, [])

  if (loadError) {
    return (
      <section className="rounded-2xl border border-gray-200 bg-white p-6">
        <h2 className="text-lg font-semibold">My style</h2>
        <p role="alert" className="mt-2 text-sm text-red-700">
          {loadError}
        </p>
      </section>
    )
  }
  if (!style) {
    return <section className="h-64 animate-pulse rounded-2xl bg-gray-100" aria-busy="true" aria-label="My style" />
  }

  const current = style // narrowed for the handlers below
  const slotsLeft = current.maxPhotos - current.photos.length
  const noteChanged = note.trim() !== (current.note ?? '')
  const busy = uploading !== null || removing !== null

  async function toggleUseWithAi() {
    setError(null)
    setSwitching(true)
    try {
      // Saves the switch with the note as last saved (an unsaved draft stays a draft).
      setStyle(await saveStyle(current.note ?? '', !current.useWithAi))
    } catch (err) {
      setError(parseApiError(err).message)
    } finally {
      setSwitching(false)
    }
  }

  async function handleSaveNote(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setNoteSaved(false)
    setSavingNote(true)
    try {
      const saved = await saveStyle(note, current.useWithAi)
      setStyle(saved)
      setNote(saved.note ?? '')
      setNoteSaved(true)
    } catch (err) {
      const parsed = parseApiError(err)
      setError(parsed.fieldErrors.note ?? parsed.message)
    } finally {
      setSavingNote(false)
    }
  }

  async function handleFiles(e: ChangeEvent<HTMLInputElement>) {
    const picked = Array.from(e.target.files ?? [])
    e.target.value = '' // so picking the same file again still triggers a change
    if (picked.length === 0) return
    setError(null)

    // Checked here first so nothing is uploaded only to be refused.
    const tooBig = picked.filter((f) => f.size > MAX_BYTES)
    const usable = picked.filter((f) => f.size <= MAX_BYTES).slice(0, slotsLeft)
    const problems: string[] = []
    if (tooBig.length) problems.push(`${tooBig.length === 1 ? 'One photo is' : `${tooBig.length} photos are`} over 10 MB.`)
    const leftOut = picked.length - tooBig.length - usable.length
    if (leftOut > 0) {
      problems.push(
        `${leftOut === 1 ? '1 photo was' : `${leftOut} photos were`} left out: you can have ${current.maxPhotos} at most.`,
      )
    }

    setUploading({ done: 0, total: usable.length })
    for (const [i, file] of usable.entries()) {
      try {
        setStyle(await addStylePhoto(file)) // one at a time: the server keeps count of the 5
      } catch (err) {
        problems.push(`“${file.name}”: ${parseApiError(err).message}`)
      }
      setUploading({ done: i + 1, total: usable.length })
    }
    setUploading(null)
    if (problems.length) setError(problems.join(' '))
  }

  async function removePhoto(id: string) {
    setError(null)
    setRemoving(id)
    try {
      setStyle(await deleteStylePhoto(id))
    } catch (err) {
      setError(parseApiError(err).message)
    } finally {
      setRemoving(null)
    }
  }

  return (
    <section id="my-style" aria-labelledby="my-style-heading" className="scroll-mt-6 flex flex-col gap-5 rounded-2xl border border-gray-200 bg-white p-6">
      <div className="flex flex-wrap items-start justify-between gap-4">
        <div className="max-w-sm">
          <h2 id="my-style-heading" className="text-lg font-semibold">
            My style
          </h2>
          <p className="text-sm text-gray-500">
            Show the AI stylist what you like to wear: up to {current.maxPhotos} photos and/or a few words. It will keep
            your style in mind every time you ask for outfits.
          </p>
        </div>
        <label className="flex cursor-pointer items-center gap-3 text-sm font-medium">
          <span>Use with the AI stylist</span>
          <button
            type="button"
            role="switch"
            aria-checked={current.useWithAi}
            onClick={toggleUseWithAi}
            disabled={switching}
            className={`relative h-6 w-11 shrink-0 rounded-full transition disabled:opacity-60 ${
              current.useWithAi ? 'bg-gray-900' : 'bg-gray-300'
            }`}
          >
            <span
              className={`absolute top-0.5 left-0.5 h-5 w-5 rounded-full bg-white shadow transition ${
                current.useWithAi ? 'translate-x-5' : ''
              }`}
            />
          </button>
        </label>
      </div>

      {!emailVerified && (
        <p className="rounded-lg bg-amber-50 px-3 py-2 text-sm text-amber-800">
          Confirm your email so the stylist can learn your style. You can add photos and a description now.
        </p>
      )}
      {!current.useWithAi && (
        <p className="rounded-lg bg-gray-100 px-3 py-2 text-sm text-gray-600">
          Switched off: the stylist won't use your style until you turn it back on. Nothing is deleted.
        </p>
      )}
      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <div>
        <p className="mb-2 text-sm font-medium text-gray-700">
          Style photos{' '}
          <span className="font-normal text-gray-500">
            ({current.photos.length} of {current.maxPhotos})
          </span>
        </p>
        <ul className="grid grid-cols-3 gap-3 sm:grid-cols-5">
          {current.photos.map((photo, i) => (
            <li key={photo.id} className="relative">
              <a href={photo.imageUrl} target="_blank" rel="noreferrer" title="Open full size">
                <img
                  src={photo.thumbnailUrl}
                  alt={`Style photo ${i + 1}`}
                  className={`aspect-square w-full rounded-lg object-cover ${removing === photo.id ? 'opacity-40' : ''}`}
                />
              </a>
              <button
                type="button"
                onClick={() => removePhoto(photo.id)}
                disabled={busy}
                aria-label={`Remove style photo ${i + 1}`}
                className="absolute top-1 right-1 rounded-full bg-white/90 px-1.5 text-lg leading-none text-gray-600 shadow hover:text-red-600 disabled:opacity-40"
              >
                ×
              </button>
            </li>
          ))}
          {slotsLeft > 0 && (
            <li>
              <button
                type="button"
                onClick={() => fileInput.current?.click()}
                disabled={busy}
                className="flex aspect-square w-full flex-col items-center justify-center gap-1 rounded-lg border-2 border-dashed border-gray-300 text-sm text-gray-500 hover:border-gray-900 hover:text-gray-900 disabled:opacity-60"
              >
                {uploading ? (
                  <span>
                    Adding {uploading.done + 1 > uploading.total ? uploading.total : uploading.done + 1} of {uploading.total}…
                  </span>
                ) : (
                  <>
                    <span className="text-2xl leading-none">+</span>
                    <span>Add photos</span>
                  </>
                )}
              </button>
            </li>
          )}
        </ul>
        <input
          ref={fileInput}
          type="file"
          accept={ACCEPTED}
          multiple
          onChange={handleFiles}
          className="hidden"
          aria-label="Choose style photos"
        />
      </div>

      <form onSubmit={handleSaveNote} noValidate className="flex flex-col gap-2">
        <label htmlFor="style-note" className="text-sm font-medium text-gray-700">
          Describe your style <span className="font-normal text-gray-500">(optional)</span>
        </label>
        <textarea
          id="style-note"
          value={note}
          onChange={(e) => {
            setNote(e.target.value)
            setNoteSaved(false)
          }}
          maxLength={NOTE_MAX}
          rows={4}
          placeholder="e.g. Mostly neutrals and denim, relaxed fits, sneakers over heels, gold jewellery."
          className="rounded-lg border border-gray-300 px-3 py-2 text-gray-900 outline-none focus:border-gray-900 focus:ring-2 focus:ring-gray-900/20"
        />
        <div className="flex items-center justify-between gap-3">
          <span className="text-xs text-gray-500">
            {note.length} / {NOTE_MAX}
          </span>
          <div className="flex items-center gap-3">
            {noteSaved && (
              <span role="status" className="text-sm text-green-700">
                Saved ✓
              </span>
            )}
            <Button type="submit" loading={savingNote} disabled={!noteChanged}>
              Save description
            </Button>
          </div>
        </div>
      </form>
    </section>
  )
}
