import type { ButtonHTMLAttributes } from 'react'

interface ButtonProps extends ButtonHTMLAttributes<HTMLButtonElement> {
  /** Shows "Please wait…" and disables the button, so a form can't be submitted twice. */
  loading?: boolean
}

/** Primary button used across the app. */
export default function Button({ loading = false, disabled, children, className = '', ...props }: ButtonProps) {
  return (
    <button
      disabled={disabled || loading}
      className={`rounded-lg bg-gray-900 px-4 py-2 font-medium text-white transition hover:bg-gray-700 disabled:cursor-not-allowed disabled:opacity-60 ${className}`}
      {...props}
    >
      {loading ? 'Please wait…' : children}
    </button>
  )
}
