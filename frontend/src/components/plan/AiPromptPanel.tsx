import { useEffect, useRef, useState, type ChangeEvent, type FormEvent } from 'react'
import { getAiUsage, suggestOutfits } from '../../api/ai'
import { resendVerificationEmail } from '../../api/auth'
import { parseApiError } from '../../api/errors'
import type { AiUsage, OutfitSuggestion } from '../../types'
import Button from '../ui/Button'
import SuggestionList from './SuggestionList'

const ACCEPTED_TYPES = ['image/jpeg', 'image/png', 'image/webp']
const MAX_BYTES = 10 * 1024 * 1024
const MAX_PROMPT = 500

interface AiPromptPanelProps {
  onClose: () => void
  /** Passed through to the suggestion cards' "Use this outfit" button (step 4.7). */
  onUse?: (suggestion: OutfitSuggestion, prompt: string) => void
}

/**
 * "Ask the AI stylist": describe a look and/or add an inspiration photo, get 1-3 outfits made
 * from your own wardrobe. Shows how many requests are left today, a privacy note, a progress
 * state for the 10-40 second wait, and a clear message for each kind of failure.
 */
export default function AiPromptPanel({ onClose, onUse }: AiPromptPanelProps) {
  const [prompt, setPrompt] = useState('')
  const [file, setFile] = useState<File | null>(null)
  const [preview, setPreview] = useState<string | null>(null)
  const [fileError, setFileError] = useState<string | null>(null)

  const [usage, setUsage] = useState<AiUsage | null>(null)
  const [loading, setLoading] = useState(false)
  const [seconds, setSeconds] = useState(0)
  const [error, setError] = useState<{ status: number; message: string } | null>(null)
  const [suggestions, setSuggestions] = useState<OutfitSuggestion[] | null>(null)
  const [resent, setResent] = useState(false)

  // How many requests are left today (free call, no Claude involved).
  const refreshUsage = () =>
    getAiUsage()
      .then(setUsage)
      .catch(() => setUsage(null))
  useEffect(() => {
    refreshUsage()
  }, [])

  // A ticking "12 s" while waiting, so a long wait doesn't look like a freeze.
  useEffect(() => {
    if (!loading) return
    const started = Date.now()
    const timer = setInterval(() => setSeconds(Math.round((Date.now() - started) / 1000)), 1000)
    return () => clearInterval(timer)
  }, [loading])

  // Free the photo preview's temporary blob: URL when the panel closes.
  const previewRef = useRef<string | null>(null)
  useEffect(() => () => {
    if (previewRef.current) URL.revokeObjectURL(previewRef.current)
  }, [])

  function handleFile(e: ChangeEvent<HTMLInputElement>) {
    const picked = e.target.files?.[0] ?? null
    e.target.value = '' // lets the same file be picked again after removing it
    setFileError(null)
    if (picked && !ACCEPTED_TYPES.includes(picked.type)) {
      setFileError('Please choose a JPEG, PNG or WebP image')
      return
    }
    if (picked && picked.size > MAX_BYTES) {
      setFileError('That photo is too large (max 10 MB)')
      return
    }
    setPhoto(picked)
  }

  function setPhoto(picked: File | null) {
    if (previewRef.current) URL.revokeObjectURL(previewRef.current)
    previewRef.current = picked ? URL.createObjectURL(picked) : null
    setPreview(previewRef.current)
    setFile(picked)
  }

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setSeconds(0)
    setLoading(true)
    try {
      const result = await suggestOutfits({ prompt: prompt.trim() || undefined, image: file ?? undefined })
      setSuggestions(result.suggestions)
    } catch (err) {
      const { status, message } = parseApiError(err)
      setError({ status, message })
    } finally {
      setLoading(false)
      refreshUsage()
    }
  }

  async function handleResend() {
    try {
      await resendVerificationEmail()
      setResent(true)
    } catch (err) {
      setError({ status: 0, message: parseApiError(err).message })
    }
  }

  const canSubmit = !loading && (prompt.trim().length > 0 || file !== null) && usage?.remaining !== 0

  return (
    <section
      aria-label="AI stylist"
      className="flex flex-col gap-5 rounded-2xl border border-violet-200 bg-gradient-to-br from-violet-50 to-white p-5"
    >
      <header className="flex items-start justify-between gap-4">
        <div>
          <h2 className="text-lg font-semibold">✨ Ask the AI stylist</h2>
          <p className="text-sm text-gray-600">
            Describe a look, add an inspiration photo, or both. You'll get up to 3 outfits made from your own wardrobe.
          </p>
        </div>
        <button
          type="button"
          onClick={onClose}
          disabled={loading}
          aria-label="Close the AI stylist"
          className="text-2xl leading-none text-gray-400 hover:text-gray-900 disabled:opacity-40"
        >
          ×
        </button>
      </header>

      <form onSubmit={handleSubmit} className="flex flex-col gap-4 sm:flex-row" noValidate>
        <div className="flex flex-1 flex-col gap-1">
          <label htmlFor="ai-prompt" className="text-sm font-medium text-gray-700">
            What are you going for?
          </label>
          <textarea
            id="ai-prompt"
            value={prompt}
            onChange={(e) => setPrompt(e.target.value)}
            maxLength={MAX_PROMPT}
            rows={3}
            disabled={loading}
            placeholder="e.g. smart-casual for a rainy autumn day at the office"
            className="rounded-lg border border-gray-300 bg-white px-3 py-2 outline-none focus:border-gray-900 focus:ring-2 focus:ring-gray-900/20"
          />
          <p className="text-right text-xs text-gray-400">
            {prompt.length} / {MAX_PROMPT}
          </p>
        </div>

        <div className="flex flex-col gap-1 sm:w-40">
          <span className="text-sm font-medium text-gray-700">Inspiration photo</span>
          {preview ? (
            <div className="relative">
              <img src={preview} alt="Your inspiration" className="aspect-[3/4] w-full rounded-lg object-cover" />
              <button
                type="button"
                onClick={() => setPhoto(null)}
                disabled={loading}
                aria-label="Remove the inspiration photo"
                className="absolute top-1.5 right-1.5 flex h-6 w-6 items-center justify-center rounded-full bg-white/90 text-gray-700 hover:text-red-600"
              >
                ×
              </button>
            </div>
          ) : (
            <label className="flex aspect-[3/4] cursor-pointer flex-col items-center justify-center rounded-lg border-2 border-dashed border-gray-300 bg-white text-center text-xs text-gray-500 hover:border-gray-900">
              <span className="text-2xl">🖼️</span>
              Optional
              <input
                type="file"
                accept={ACCEPTED_TYPES.join(',')}
                onChange={handleFile}
                disabled={loading}
                className="sr-only"
              />
            </label>
          )}
          {fileError && <p className="text-xs text-red-600">{fileError}</p>}
        </div>

        <div className="flex flex-col justify-end gap-2 sm:w-48">
          <Button type="submit" disabled={!canSubmit} loading={loading}>
            Get suggestions
          </Button>
          {usage && (
            <p className={`text-center text-xs ${usage.remaining === 0 ? 'font-medium text-amber-700' : 'text-gray-500'}`}>
              {usage.remaining} of {usage.limit} left today
            </p>
          )}
        </div>
      </form>

      <p className="text-xs text-gray-500">
        🔒 Your description, inspiration photo and wardrobe (item details and small photos) are sent to Anthropic's
        Claude only when you press Get suggestions. The inspiration photo isn't saved.
      </p>

      {loading && (
        <div role="status" className="flex items-center gap-3 rounded-xl bg-white p-4 text-sm text-gray-700">
          <span className="h-5 w-5 animate-spin rounded-full border-2 border-violet-200 border-t-violet-600" />
          Styling your outfits… {seconds}s
          <span className="text-gray-400">(this usually takes 10–40 seconds)</span>
        </div>
      )}

      {error && !loading && (
        <div role="alert" className="rounded-xl border border-red-200 bg-red-50 p-4 text-sm text-red-800">
          <p>{error.message}</p>
          {error.status === 403 &&
            (resent ? (
              <p className="mt-2 font-medium">Confirmation email sent ✓ Check your inbox, then try again.</p>
            ) : (
              <button type="button" onClick={handleResend} className="mt-2 font-medium underline">
                Resend the confirmation email
              </button>
            ))}
          {error.status === 429 && usage && (
            <p className="mt-1 text-red-700">
              Resets at {new Date(usage.resetsAt).toLocaleTimeString(undefined, { hour: 'numeric', minute: '2-digit' })}.
            </p>
          )}
        </div>
      )}

      {suggestions && !loading && (
        <SuggestionList
          suggestions={suggestions}
          // Remember what was asked, so the saved outfit can show where it came from.
          onUse={onUse && ((s) => onUse(s, prompt.trim() || '(from an inspiration photo)'))}
        />
      )}
    </section>
  )
}
