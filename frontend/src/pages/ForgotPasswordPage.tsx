import { useState, type FormEvent } from 'react'
import { Link } from 'react-router-dom'
import { forgotPassword } from '../api/auth'
import { parseApiError } from '../api/errors'
import AuthCard from '../components/auth/AuthCard'
import Button from '../components/ui/Button'
import TextField from '../components/ui/TextField'

/**
 * /forgot-password: ask for a reset link by email.
 *
 * The confirmation message is worded "if an account exists…" on purpose: the backend gives the
 * same answer for every address, so this page can't be used to find out who has an account.
 */
export default function ForgotPasswordPage() {
  const [email, setEmail] = useState('')
  const [sentTo, setSentTo] = useState<string | null>(null)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      await forgotPassword(email)
      setSentTo(email.trim())
    } catch (err) {
      const parsed = parseApiError(err)
      setError(Object.keys(parsed.fieldErrors).length ? null : parsed.message)
      setFieldErrors(parsed.fieldErrors)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCard
      title="Forgot your password?"
      subtitle={sentTo ? 'Check your inbox.' : "Enter your email and we'll send you a link to choose a new one."}
      footer={
        <Link to="/login" className="font-medium text-gray-900 underline">
          Back to log in
        </Link>
      }
    >
      {sentTo ? (
        <div role="status" className="flex flex-col gap-3 text-sm text-gray-700">
          <p>
            If an account exists for <strong>{sentTo}</strong>, we've sent it a reset link. It works once and expires
            in 1 hour.
          </p>
          <p className="text-gray-500">Nothing there after a few minutes? Check your spam folder, or try again.</p>
          <button type="button" onClick={() => setSentTo(null)} className="self-start font-medium underline">
            Use a different email
          </button>
        </div>
      ) : (
        <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
          {error && (
            <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </p>
          )}
          <TextField
            label="Email"
            type="email"
            autoComplete="email"
            value={email}
            onChange={(e) => setEmail(e.target.value)}
            error={fieldErrors.email}
            required
          />
          <Button type="submit" loading={submitting} disabled={!email.trim()}>
            Send reset link
          </Button>
        </form>
      )}
    </AuthCard>
  )
}
