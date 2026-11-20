import { useState, type FormEvent } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { resetPassword } from '../api/auth'
import { parseApiError } from '../api/errors'
import AuthCard from '../components/auth/AuthCard'
import Button from '../components/ui/Button'
import TextField from '../components/ui/TextField'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

/**
 * /reset-password?token=…: the page the reset email links to. Choose a new password, then log in.
 * Public: the person opening the link isn't logged in (that's why they need it).
 */
export default function ResetPasswordPage() {
  useDocumentTitle('Reset password')
  const [params] = useSearchParams()
  const token = params.get('token')

  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)
  const [done, setDone] = useState(false)

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    if (password !== confirm) {
      setFieldErrors({ confirm: 'Passwords do not match' })
      return
    }
    setFieldErrors({})
    setSubmitting(true)
    try {
      await resetPassword(token ?? '', password)
      setDone(true)
    } catch (err) {
      const parsed = parseApiError(err)
      setFieldErrors(parsed.fieldErrors)
      if (!parsed.fieldErrors.newPassword) setError(parsed.message)
    } finally {
      setSubmitting(false)
    }
  }

  if (!token) {
    return (
      <AuthCard
        title="Link not valid"
        subtitle="This reset link is missing its code."
        footer={
          <Link to="/forgot-password" className="font-medium text-gray-900 underline">
            Request a new link
          </Link>
        }
      >
        {null}
      </AuthCard>
    )
  }

  if (done) {
    return (
      <AuthCard
        title="Password changed ✓"
        subtitle="You've been signed out everywhere. Log in with your new password."
        footer={null}
      >
        <Link
          to="/login"
          className="block rounded-lg bg-gray-900 px-4 py-2 text-center font-medium text-white hover:bg-gray-700"
        >
          Log in
        </Link>
      </AuthCard>
    )
  }

  return (
    <AuthCard
      title="Choose a new password"
      subtitle="Pick something you haven't used here before."
      footer={
        <Link to="/login" className="font-medium text-gray-900 underline">
          Back to log in
        </Link>
      }
    >
      <form onSubmit={handleSubmit} className="flex flex-col gap-4" noValidate>
        {error && (
          <div role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            <p>{error}</p>
            <Link to="/forgot-password" className="mt-1 inline-block font-medium underline">
              Request a new link
            </Link>
          </div>
        )}
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          placeholder="At least 8 characters"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.newPassword}
        />
        <TextField
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          error={fieldErrors.confirm}
        />
        <Button type="submit" loading={submitting} disabled={!password || !confirm}>
          Set new password
        </Button>
      </form>
    </AuthCard>
  )
}
