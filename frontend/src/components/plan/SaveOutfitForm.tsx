import { useId, type FormEvent } from 'react'
import Button from '../ui/Button'
import TextField from '../ui/TextField'

interface SaveOutfitFormProps {
  name: string
  notes: string
  onNameChange: (name: string) => void
  onNotesChange: (notes: string) => void
  onSubmit: () => void
  /** How many items the outfit has; saving needs at least one. */
  itemCount: number
  saving: boolean
  /** General error (e.g. "One or more items aren't in your wardrobe"). */
  error: string | null
  /** Field errors from the backend: name, notes, itemIds. */
  fieldErrors: Record<string, string>
  /** Text of the button ("Save outfit" now, "Save changes" when editing in step 3.6). */
  submitLabel?: string
}

/**
 * Name + notes + Save button, shown under the outfit canvas. Like ClothingDetailsFields, it's
 * controlled: PlanPage owns the values and does the actual saving.
 */
export default function SaveOutfitForm({
  name,
  notes,
  onNameChange,
  onNotesChange,
  onSubmit,
  itemCount,
  saving,
  error,
  fieldErrors,
  submitLabel = 'Save outfit',
}: SaveOutfitFormProps) {
  const notesId = useId()

  function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    onSubmit()
  }

  return (
    <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-4">
      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      )}

      <TextField
        label="Outfit name"
        value={name}
        onChange={(e) => onNameChange(e.target.value)}
        error={fieldErrors.name}
        placeholder="e.g. Friday office look"
        maxLength={120}
        disabled={saving}
      />

      <div className="flex flex-col gap-1">
        <label htmlFor={notesId} className="text-sm font-medium text-gray-700">
          Notes (optional)
        </label>
        <textarea
          id={notesId}
          value={notes}
          onChange={(e) => onNotesChange(e.target.value)}
          rows={2}
          maxLength={1000}
          disabled={saving}
          placeholder="When to wear it, what to swap if it's cold…"
          className={`rounded-lg border px-3 py-2 text-gray-900 outline-none focus:ring-2 focus:ring-gray-900/20 ${
            fieldErrors.notes ? 'border-red-400' : 'border-gray-300 focus:border-gray-900'
          }`}
        />
        {fieldErrors.notes && <p className="text-sm text-red-600">{fieldErrors.notes}</p>}
      </div>

      {fieldErrors.itemIds && <p className="text-sm text-red-600">{fieldErrors.itemIds}</p>}

      <Button type="submit" loading={saving} disabled={itemCount === 0}>
        {submitLabel}
      </Button>
      {itemCount === 0 && <p className="-mt-2 text-center text-xs text-gray-500">Add at least one item to save.</p>}
    </form>
  )
}
