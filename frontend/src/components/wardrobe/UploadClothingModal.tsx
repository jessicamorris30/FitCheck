import { useEffect, useRef, useState, type ChangeEvent, type FormEvent, type MouseEvent } from 'react'
import { uploadClothingItem } from '../../api/clothing'
import { parseApiError } from '../../api/errors'
import { EMPTY_DETAILS, toClothingDetails, type DetailsFormValues } from '../../lib/clothingForm'
import type { ClothingItem } from '../../types'
import Button from '../ui/Button'
import ClothingDetailsFields from './ClothingDetailsFields'

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_BYTES = 10 * 1024 * 1024 // must match the backend's 10 MB limit

interface UploadClothingModalProps {
  onClose: () => void
  /** Called with the saved item after a successful upload. */
  onUploaded: (item: ClothingItem) => void
}

/**
 * "Add item" dialog: pick a photo, see a preview, fill in details, upload.
 *
 * Uses the browser's built-in <dialog> element with showModal(), which gives us for free:
 * a dimmed backdrop, Esc to close, keyboard focus kept inside the dialog, and screen-reader
 * support. The parent only renders this component while it's open, so every opening starts
 * with an empty form.
 */
export default function UploadClothingModal({ onClose, onUploaded }: UploadClothingModalProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)

  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [details, setDetails] = useState<DetailsFormValues>(EMPTY_DETAILS)

  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [progress, setProgress] = useState<number | null>(null) // null = not uploading

  // Open as a modal when mounted. (Syncing React with a browser API is what effects are for.)
  useEffect(() => {
    dialogRef.current?.showModal()
  }, [])

  // The preview is a temporary "blob:" URL pointing at the file in memory. Free it when the
  // dialog closes so the browser can release the image.
  const previewRef = useRef<string | null>(null)
  useEffect(() => () => {
    if (previewRef.current) URL.revokeObjectURL(previewRef.current)
  }, [])

  const uploading = progress !== null

  function handleFileChange(e: ChangeEvent<HTMLInputElement>) {
    const picked = e.target.files?.[0] ?? null
    setFieldErrors((errs) => ({ ...errs, image: '' }))

    // Quick checks so the user gets instant feedback. The backend checks again (and properly,
    // by reading the file's bytes), because anything in the browser can be bypassed.
    if (picked && !ACCEPTED_TYPES.includes(picked.type)) {
      setFieldErrors((errs) => ({ ...errs, image: 'Please choose a JPEG, PNG or WebP image' }))
      return
    }
    if (picked && picked.size > MAX_BYTES) {
      setFieldErrors((errs) => ({ ...errs, image: 'That file is too large (max 10 MB)' }))
      return
    }

    if (previewRef.current) URL.revokeObjectURL(previewRef.current)
    previewRef.current = picked ? URL.createObjectURL(picked) : null
    setPreview(previewRef.current)
    setFile(picked)

    // Suggest a name from the file name: "blue-jeans_2.jpg" → "Blue jeans 2".
    if (picked && !details.name) setDetails((d) => ({ ...d, name: nameFromFile(picked.name) }))
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    if (!file) {
      setFieldErrors({ image: 'Please choose a photo' })
      return
    }
    setError(null)
    setFieldErrors({})
    setProgress(0)
    try {
      const item = await uploadClothingItem(file, toClothingDetails(details), setProgress)
      onUploaded(item)
      onClose()
    } catch (err) {
      const parsed = parseApiError(err)
      setError(parsed.message)
      setFieldErrors(parsed.fieldErrors)
      setProgress(null)
    }
  }

  // Click on the dimmed backdrop (outside the panel) closes the dialog, unless mid-upload.
  function handleBackdropClick(e: MouseEvent<HTMLDialogElement>) {
    if (e.target === dialogRef.current && !uploading) onClose()
  }

  return (
    <dialog
      ref={dialogRef}
      onCancel={(e) => {
        e.preventDefault() // we close via React state instead of letting the browser do it
        if (!uploading) onClose()
      }}
      onClick={handleBackdropClick}
      className="m-auto w-[calc(100%-2rem)] max-w-2xl rounded-2xl p-0 shadow-xl backdrop:bg-black/40"
    >
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-6 p-6">
        <header className="flex items-center justify-between">
          <h2 className="text-lg font-semibold">Add to your wardrobe</h2>
          <button
            type="button"
            onClick={onClose}
            disabled={uploading}
            aria-label="Close"
            className="rounded-full p-1 text-2xl leading-none text-gray-400 hover:text-gray-900 disabled:opacity-40"
          >
            ×
          </button>
        </header>

        {error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <div className="grid gap-6 sm:grid-cols-2">
          {/* Photo picker + preview. The <label> makes the whole box clickable. */}
          <div className="flex flex-col gap-1">
            <label
              className={`flex aspect-[3/4] cursor-pointer items-center justify-center overflow-hidden rounded-xl border-2 border-dashed text-center text-sm transition ${
                fieldErrors.image ? 'border-red-400' : 'border-gray-300 hover:border-gray-900'
              }`}
            >
              {preview ? (
                <img src={preview} alt="Preview of the selected photo" className="h-full w-full object-cover" />
              ) : (
                <span className="px-4 text-gray-500">
                  <span className="block text-3xl">📷</span>
                  Click to choose a photo
                  <span className="mt-1 block text-xs">JPEG, PNG or WebP, up to 10 MB</span>
                </span>
              )}
              <input
                type="file"
                accept={ACCEPTED_TYPES.join(',')}
                onChange={handleFileChange}
                disabled={uploading}
                className="sr-only"
              />
            </label>
            {fieldErrors.image && <p className="text-sm text-red-600">{fieldErrors.image}</p>}
            {preview && !uploading && <p className="text-center text-xs text-gray-500">Click the photo to change it</p>}
          </div>

          <ClothingDetailsFields values={details} onChange={setDetails} errors={fieldErrors} disabled={uploading} />
        </div>

        <footer className="flex flex-col gap-3">
          {uploading && (
            <div className="h-2 overflow-hidden rounded-full bg-gray-100" aria-label="Upload progress">
              <div className="h-full bg-gray-900 transition-all" style={{ width: `${progress}%` }} />
            </div>
          )}
          <div className="flex justify-end gap-3">
            <button
              type="button"
              onClick={onClose}
              disabled={uploading}
              className="rounded-lg px-4 py-2 font-medium text-gray-700 hover:bg-gray-100 disabled:opacity-40"
            >
              Cancel
            </button>
            <Button type="submit" loading={uploading}>
              Upload
            </Button>
          </div>
        </footer>
      </form>
    </dialog>
  )
}

/** "blue-jeans_2.jpg" → "Blue jeans 2" */
function nameFromFile(fileName: string): string {
  const base = fileName.replace(/\.[^.]+$/, '').replace(/[-_]+/g, ' ').trim()
  return base ? base.charAt(0).toUpperCase() + base.slice(1) : ''
}
