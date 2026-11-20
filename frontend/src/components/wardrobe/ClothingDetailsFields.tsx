import { useId } from 'react'
import { CATEGORIES, categoryLabel, isCategory } from '../../lib/categories'
import { tagsError, type DetailsFormValues } from '../../lib/clothingForm'
import TextField from '../ui/TextField'

interface ClothingDetailsFieldsProps {
  values: DetailsFormValues
  onChange: (values: DetailsFormValues) => void
  /** Field errors from the backend (name, category, color, tags...). */
  errors: Record<string, string>
  disabled?: boolean
}

/**
 * Name, category, color and tags inputs. Shared by the upload dialog and the edit form on the
 * detail page, so both always look and validate the same way.
 *
 * It's a "controlled" component: it doesn't keep its own state. The parent passes the current
 * values in and gets every change back through onChange.
 */
export default function ClothingDetailsFields({ values, onChange, errors, disabled }: ClothingDetailsFieldsProps) {
  const categoryId = useId()
  const set = (patch: Partial<DetailsFormValues>) => onChange({ ...values, ...patch })

  return (
    <div className="flex flex-col gap-4">
      <TextField
        label="Name"
        value={values.name}
        onChange={(e) => set({ name: e.target.value })}
        error={errors.name}
        placeholder="e.g. Blue jeans"
        maxLength={120}
        disabled={disabled}
      />

      <div className="flex flex-col gap-1">
        <label htmlFor={categoryId} className="text-sm font-medium text-gray-700">
          Category
        </label>
        <select
          id={categoryId}
          value={values.category}
          onChange={(e) => set({ category: isCategory(e.target.value) ? e.target.value : '' })}
          disabled={disabled}
          className={`rounded-lg border bg-white px-3 py-2 text-gray-900 ${
            errors.category ? 'border-red-400' : 'border-gray-300'
          }`}
        >
          <option value="">Choose…</option>
          {CATEGORIES.map((c) => (
            <option key={c.value} value={c.value}>
              {categoryLabel(c.value)}
            </option>
          ))}
        </select>
        {errors.category && <p className="text-sm text-red-600">{errors.category}</p>}
      </div>

      <TextField
        label="Color (optional)"
        value={values.color}
        onChange={(e) => set({ color: e.target.value })}
        error={errors.color}
        placeholder="e.g. navy"
        maxLength={40}
        disabled={disabled}
      />

      <div className="flex flex-col gap-1">
        <TextField
          label="Tags (optional)"
          value={values.tags}
          onChange={(e) => set({ tags: e.target.value })}
          error={tagsError(errors)}
          placeholder="casual, summer, work"
          disabled={disabled}
        />
        <p className="text-xs text-gray-500">Separate tags with commas.</p>
      </div>
    </div>
  )
}
