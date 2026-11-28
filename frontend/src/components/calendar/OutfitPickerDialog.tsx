import { useEffect, useRef, useState, type MouseEvent } from 'react'
import { Link } from 'react-router-dom'
import { parseApiError } from '../../api/errors'
import { listOutfits } from '../../api/outfits'
import type { Outfit } from '../../types'
import OutfitThumbnail from '../plan/OutfitThumbnail'

interface OutfitPickerDialogProps {
  /** Shown in the title, e.g. "Friday 4 December". */
  dayLabel: string
  /** Outfits already on this day: shown, but can't be picked again. */
  alreadyPlanned: Set<string>
  /** Called with the chosen outfit; rejects (with the API error) if it couldn't be added. */
  onPick: (outfit: Outfit) => Promise<void>
  onClose: () => void
}

/**
 * "Add an outfit to this day": your saved outfits as a grid, tap one to add it.
 * A browser <dialog> like the upload dialog: dimmed backdrop, Esc closes, focus stays inside.
 */
export default function OutfitPickerDialog({ dayLabel, alreadyPlanned, onPick, onClose }: OutfitPickerDialogProps) {
  const dialogRef = useRef<HTMLDialogElement>(null)
  const [outfits, setOutfits] = useState<Outfit[] | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [adding, setAdding] = useState<string | null>(null) // id of the outfit being added

  useEffect(() => {
    dialogRef.current?.showModal()
  }, [])

  useEffect(() => {
    let cancelled = false
    listOutfits()
      .then((list) => !cancelled && setOutfits(list))
      .catch((err) => {
        if (cancelled) return
        setOutfits([])
        setError(parseApiError(err).message)
      })
    return () => {
      cancelled = true
    }
  }, [])

  async function pick(outfit: Outfit) {
    setAdding(outfit.id)
    setError(null)
    try {
      await onPick(outfit) // the page closes the dialog when it worked
    } catch (err) {
      setError(parseApiError(err).message)
      setAdding(null)
    }
  }

  function handleBackdropClick(e: MouseEvent<HTMLDialogElement>) {
    if (e.target === dialogRef.current && !adding) onClose()
  }

  return (
    <dialog
      ref={dialogRef}
      onCancel={(e) => {
        e.preventDefault()
        if (!adding) onClose()
      }}
      onClick={handleBackdropClick}
      aria-labelledby="picker-title"
      className="m-auto w-[calc(100%-2rem)] max-w-2xl rounded-2xl p-0 shadow-xl backdrop:bg-black/40"
    >
      <div className="flex max-h-[85vh] flex-col gap-4 p-6">
        <header className="flex items-center justify-between gap-4">
          <h2 id="picker-title" className="text-lg font-semibold">
            Add an outfit to {dayLabel}
          </h2>
          <button
            type="button"
            onClick={onClose}
            disabled={adding !== null}
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

        <div className="overflow-y-auto">
          {outfits === null && (
            <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3" aria-busy="true">
              {Array.from({ length: 3 }, (_, i) => (
                <li key={i} className="aspect-square animate-pulse rounded-xl bg-gray-100" />
              ))}
            </ul>
          )}

          {outfits?.length === 0 && !error && (
            <div className="rounded-xl border border-dashed border-gray-300 p-8 text-center text-gray-500">
              <p>You haven't saved any outfits yet.</p>
              <Link to="/plan" className="mt-3 inline-block font-medium text-gray-900 underline">
                Plan your first outfit
              </Link>
            </div>
          )}

          {outfits && outfits.length > 0 && (
            <ul className="grid grid-cols-2 gap-4 sm:grid-cols-3">
              {outfits.map((outfit) => {
                const planned = alreadyPlanned.has(outfit.id)
                return (
                  <li key={outfit.id}>
                    <button
                      type="button"
                      onClick={() => pick(outfit)}
                      disabled={planned || adding !== null}
                      className="group flex w-full flex-col gap-2 text-left disabled:cursor-not-allowed"
                    >
                      <div className={`relative transition ${planned ? 'opacity-40' : 'group-hover:opacity-90'}`}>
                        <OutfitThumbnail items={outfit.items} />
                        {adding === outfit.id && (
                          <span className="absolute inset-0 flex items-center justify-center rounded-lg bg-white/70 text-sm font-medium">
                            Adding…
                          </span>
                        )}
                      </div>
                      <span className="truncate text-sm font-medium">{outfit.name}</span>
                      {planned && <span className="-mt-2 text-xs text-gray-500">Already on this day</span>}
                    </button>
                  </li>
                )
              })}
            </ul>
          )}
        </div>
      </div>
    </dialog>
  )
}
