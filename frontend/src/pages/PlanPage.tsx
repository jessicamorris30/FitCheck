import { useCallback, useMemo, useState } from 'react'
import { useNavigate } from 'react-router-dom'
import { parseApiError } from '../api/errors'
import { createOutfit, MAX_OUTFIT_ITEMS } from '../api/outfits'
import OutfitCanvas from '../components/plan/OutfitCanvas'
import OutfitItemPicker from '../components/plan/OutfitItemPicker'
import SaveOutfitForm from '../components/plan/SaveOutfitForm'
import SelectionBar from '../components/plan/SelectionBar'
import type { ClothingItem } from '../types'

type Stage = 'choose' | 'details'

/**
 * Plan mode, as a two-step flow:
 *   1. Choose clothes: your wardrobe fills the page; picks collect in a bar at the bottom → Next.
 *   2. Details: arrange the order, name it, add notes, save → you land on the Outfits tab.
 *
 * Both steps share ONE piece of state, the ordered list of chosen items, held here
 * ("lifting state up"). Going Back keeps your picks because the list lives in this page, not
 * inside either step's components.
 */
export default function PlanPage() {
  const navigate = useNavigate()
  const [stage, setStage] = useState<Stage>('choose')

  const [items, setItems] = useState<ClothingItem[]>([])
  const [name, setName] = useState('')
  const [notes, setNotes] = useState('')

  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

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

  async function save() {
    setSaving(true)
    setError(null)
    setFieldErrors({})
    try {
      const saved = await createOutfit({ name, notes, itemIds: items.map((i) => i.id) })
      // Off to the Outfits tab, which shows a "saved ✓" message for this outfit.
      navigate('/outfits', { state: { justSaved: saved.name } })
    } catch (err) {
      const parsed = parseApiError(err)
      setError(parsed.message)
      setFieldErrors(parsed.fieldErrors)
      setSaving(false)
    }
  }

  if (stage === 'choose') {
    return (
      // pb-28: leave room so the last row of clothes isn't hidden behind the bottom bar.
      <section className="flex flex-col gap-6 pb-28">
        <header>
          <p className="text-sm font-medium text-gray-500">Step 1 of 2</p>
          <h1 className="text-2xl font-semibold">Choose clothes</h1>
          <p className="text-sm text-gray-500">Click items to add them to your outfit. Click again to remove.</p>
        </header>
        <OutfitItemPicker selectedIds={selectedIds} onToggle={toggle} full={full} />
        <SelectionBar items={items} onClear={() => setItems([])} onNext={() => goTo('details')} />
      </section>
    )
  }

  return (
    <section className="flex flex-col gap-6">
      <header className="flex flex-col gap-2">
        <button
          type="button"
          onClick={() => goTo('choose')}
          disabled={saving}
          className="self-start text-sm text-gray-500 hover:text-gray-900"
        >
          ← Back to choosing clothes
        </button>
        <p className="text-sm font-medium text-gray-500">Step 2 of 2</p>
        <h1 className="text-2xl font-semibold">Arrange, name and save</h1>
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
        />
      </div>
    </section>
  )
}
