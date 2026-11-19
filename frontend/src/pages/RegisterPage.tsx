import { useState, type FormEvent } from 'react'
import { Link, Navigate, useNavigate } from 'react-router-dom'
import { parseApiError } from '../api/errors'
import AuthCard from '../components/auth/AuthCard'
import Button from '../components/ui/Button'
import FullPageSpinner from '../components/ui/FullPageSpinner'
import TextField from '../components/ui/TextField'
import { useAuth } from '../hooks/useAuth'

/** Account creation. The backend logs the new user in straight away. */
export default function RegisterPage() {
  const { user, loading, register } = useAuth()
  const navigate = useNavigate()

  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [confirm, setConfirm] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  if (loading) return <FullPageSpinner />
  if (user) return <Navigate to="/wardrobe" replace />

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)

    // Checked here only: "passwords match" is about typing, the server never needs it.
    // Every real rule (valid email, 8-72 characters, email not taken) is enforced by the backend.
    if (password !== confirm) {
      setFieldErrors({ confirm: 'Passwords do not match' })
      return
    }

    setFieldErrors({})
    setSubmitting(true)
    try {
      await register(email, password)
      navigate('/wardrobe', { replace: true })
    } catch (err) {
      const parsed = parseApiError(err)
      setError(parsed.message)
      setFieldErrors(parsed.fieldErrors)
    } finally {
      setSubmitting(false)
    }
  }

  return (
    <AuthCard
      title="Create your account"
      subtitle="Start digitizing your wardrobe."
      footer={
        <>
          Already have an account?{' '}
          <Link to="/login" className="font-medium text-gray-900 underline">
            Log in
          </Link>
        </>
      }
    >
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
        <TextField
          label="Password"
          type="password"
          autoComplete="new-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          placeholder="At least 8 characters"
          required
        />
        <TextField
          label="Confirm password"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          error={fieldErrors.confirm}
          required
        />
        <Button type="submit" loading={submitting} className="mt-2">
          Create account
        </Button>
      </form>
    </AuthCard>
  )
}
