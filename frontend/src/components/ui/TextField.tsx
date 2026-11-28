import { useId, type InputHTMLAttributes, type ReactNode } from 'react'

interface TextFieldProps extends InputHTMLAttributes<HTMLInputElement> {
  label: string
  /** Error text shown under the input (e.g. from the backend's fieldErrors). */
  error?: string
  /** Helpful text under the input when there's no error (e.g. "✓ Available"). */
  hint?: ReactNode
}

/** A labeled input with an optional error or hint below it. Reused by every form. */
export default function TextField({ label, error, hint, className = '', ...inputProps }: TextFieldProps) {
  const id = useId() // unique id so the <label> is linked to its <input> (accessibility)
  const errorId = `${id}-error`
  const hintId = `${id}-hint`

  return (
    <div className="flex flex-col gap-1">
      <label htmlFor={id} className="text-sm font-medium text-gray-700">
        {label}
      </label>
      <input
        id={id}
        aria-invalid={error ? true : undefined}
        aria-describedby={error ? errorId : hint ? hintId : undefined}
        className={`rounded-lg border px-3 py-2 text-gray-900 outline-none transition focus:ring-2 focus:ring-gray-900/20 ${
          error ? 'border-red-400' : 'border-gray-300 focus:border-gray-900'
        } ${className}`}
        {...inputProps}
      />
      {error ? (
        <p id={errorId} className="text-sm text-red-600">
          {error}
        </p>
      ) : (
        hint && (
          // aria-live: screen readers announce "Available" / "Checking…" as it changes.
          <p id={hintId} aria-live="polite" className="text-sm text-gray-500">
            {hint}
          </p>
        )
      )}
    </div>
  )
}
