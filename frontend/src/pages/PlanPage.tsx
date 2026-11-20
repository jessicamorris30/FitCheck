import { useCallback, useEffect, useMemo, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import { getAiStatus } from '../api/ai'
import { parseApiError } from '../api/errors'
import AiPromptPanel from '../components/plan/AiPromptPanel'
import { createOutfit, MAX_OUTFIT_ITEMS, updateOutfit } from '../api/outfits'
import OutfitCanvas from '../components/plan/OutfitCanvas'
import OutfitItemPicker from '../components/plan/OutfitItemPicker'
import SaveOutfitForm from '../components/plan/SaveOutfitForm'
import SelectionBar from '../components/plan/SelectionBar'
import type { ClothingItem, Outfit, OutfitSuggestion } from '../types'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

type Stage = 'choose' | 'details'

interface PlanPageProps {
  /**
   * When editing (route /outfits/:id/edit), the saved outfit to start from. The form, the chosen
   * items and the step all start from it, and saving updates it instead of creating a new one.
   * EditOutfitPage loads it first, so this page never has to wait for data.
   */
  existing?: Outfit
}

/**
 * Plan mode, as a two-step flow:
 *   1. Choose clothes: your wardrobe fills the page; picks collect in a bar at the bottom → Next.
 *   2. Details: arrange the order, name it, add notes, save.
 *
 * Both steps share ONE piece of state, the ordered list of chosen items, held here
 * ("lifting state up"). Going Back keeps your picks because the list lives in this page, not
 * inside either step's components.
 */
export default function PlanPage({ existing }: PlanPageProps) {
  useDocumentTitle(existing ? 'Edit outfit' : 'Plan')
  const navigate = useNavigate()
  const editing = existing !== undefined

  // useState(initialValue) only uses the value on the first render, which is exactly what we
  // want: start from the saved outfit, then let the user change things freely.
  const [stage, setStage] = useState<Stage>(editing ? 'details' : 'choose')
  const [items, setItems] = useState<ClothingItem[]>(existing?.items ?? [])
  const [name, setName] = useState(existing?.name ?? '')
  const [notes, setNotes] = useState(existing?.notes ?? '')
  /** Set when the outfit came from an AI suggestion; saved with it and shown as "✨ AI". */
  const [aiPrompt, setAiPrompt] = useState<string | null>(existing?.aiPrompt ?? null)

  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  // The AI stylist: only offered when the server has it switched on (an API key is set).
  const [aiAvailable, setAiAvailable] = useState(false)
  const [aiOpen, setAiOpen] = useState(false)
  useEffect(() => {
    let cancelled = false
    getAiStatus()
      .then((s) => !cancelled && setAiAvailable(s.configured))
      .catch(() => {})
    return () => {
      cancelled = true
    }
  }, [])

  const selectedIds = useMemo(() => new Set(items.map((i) => i.id)), [items])
  const full = items.length >= MAX_OUTFIT_ITEMS

  // Updates use the "functional" form setItems(prev => ...) so they always start from the
  // latest list, even if several clicks happen before React re-renders.
  const toggle = useCallback((item: ClothingItem) => {
    setItems((prev) => {
      if (prev.some((i) => i.id === item.id)) return prev.filter((i) => i.id !== item.id)
      if (prev.length >= MAX_OUTFIT_ITEMS) return prev
      return [...prev, item]
    })
  }, [])

  const remove = useCallback((itemId: string) => {
    setItems((prev) => prev.filter((i) => i.id !== itemId))
  }, [])

  const move = useCallback((index: number, direction: -1 | 1) => {
    setItems((prev) => {
      const target = index + direction
      if (target < 0 || target >= prev.length) return prev
      const next = [...prev] // never change the old array: React compares by reference
      ;[next[index], next[target]] = [next[target], next[index]]
      return next
    })
  }, [])

  function goTo(next: Stage) {
    setStage(next)
    window.scrollTo({ top: 0 }) // each step starts at the top of the page
  }

  /**
   * "Use this outfit" on an AI suggestion: its items, title and reasoning become the outfit in
   * progress, and you jump to step 2 to adjust and save it like any other outfit.
   * (When editing an existing outfit, its name is kept: you asked for new clothes, not a new name.)
   */
  function applySuggestion(suggestion: OutfitSuggestion, prompt: string) {
    setItems(suggestion.items.slice(0, MAX_OUTFIT_ITEMS))
    if (!existing) setName(suggestion.title)
    setNotes(suggestion.reasoning)
    setAiPrompt(prompt)
    setAiOpen(false)
    goTo('details')
  }

  async function save() {
    setSaving(true)
    setError(null)
    setFieldErrors({})
    const input = { name, notes, itemIds: items.map((i) => i.id), aiPrompt }
    try {
      if (existing) {
        const saved = await updateOutfit(existing.id, input)
        // replace: Back from the outfit page shouldn't return to this finished edit form.
        navigate(`/outfits/${saved.id}`, { replace: true, state: { justSaved: saved.name } })
      } else {
        const saved = await createOutfit(input)
        navigate('/outfits', { state: { justSaved: saved.name } })
      }
    } catch (err) {
      const parsed = parseApiError(err)
      setError(parsed.message)
      setFieldErrors(parsed.fieldErrors)
      setSaving(false)
    }
  }

  const title = editing ? `Edit “${existing.name}”` : null

  if (stage === 'choose') {
    return (
      // pb-28: leave room so the last row of clothes isn't hidden behind the bottom bar.
      <section className="flex flex-col gap-6 pb-28">
        <header className="flex flex-wrap items-end justify-between gap-3">
          <div>
            <p className="text-sm font-medium text-gray-500">{title ? `${title} · ` : ''}Step 1 of 2</p>
            <h1 className="text-2xl font-semibold">Choose clothes</h1>
            <p className="text-sm text-gray-500">Click items to add them to your outfit. Click again to remove.</p>
          </div>
          {aiAvailable && !aiOpen && (
            <button
              type="button"
              onClick={() => setAiOpen(true)}
              className="rounded-lg border border-violet-300 bg-violet-50 px-4 py-2 font-medium text-violet-900 hover:bg-violet-100"
            >
              ✨ Ask the AI stylist
            </button>
          )}
        </header>
        {aiOpen && <AiPromptPanel onClose={() => setAiOpen(false)} onUse={applySuggestion} />}
        <OutfitItemPicker selectedIds={selectedIds} onToggle={toggle} full={full} />
        <SelectionBar items={items} onClear={() => setItems([])} onNext={() => goTo('details')} />
      </section>
    )
  }

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <div className="flex flex-wrap gap-4 text-sm text-gray-500">
          <button type="button" onClick={() => goTo('choose')} disabled={saving} className="hover:text-gray-900">
            ← {editing ? 'Add or remove clothes' : 'Back to choosing clothes'}
          </button>
          {editing && (
            <Link to={`/outfits/${existing.id}`} className="hover:text-gray-900">
              Cancel editing
            </Link>
          )}
        </div>
        <p className="text-sm font-medium text-gray-500">{title ? `${title} · ` : ''}Step 2 of 2</p>
        <h1 className="text-2xl font-semibold">Arrange, name and save</h1>
        {aiPrompt && (
          <p className="flex flex-wrap items-center gap-2 rounded-lg bg-violet-50 px-3 py-2 text-sm text-violet-900">
            <span>✨ Suggested by the AI stylist for “{aiPrompt}”.</span>
            <span className="text-violet-700">Change anything you like before saving.</span>
          </p>
        )}
      </header>

      <div className="grid items-start gap-6 lg:grid-cols-[1fr_minmax(300px,380px)]">
        <OutfitCanvas items={items} onRemove={remove} onMove={move} />
        <SaveOutfitForm
          name={name}
          notes={notes}
          onNameChange={setName}
          onNotesChange={setNotes}
          onSubmit={save}
          itemCount={items.length}
          saving={saving}
          error={error}
          fieldErrors={fieldErrors}
          submitLabel={editing ? 'Save changes' : 'Save outfit'}
        />
      </div>
    </section>
  )
}
