import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { parseApiError } from '../api/errors'
import AuthCard from '../components/auth/AuthCard'
import Button from '../components/ui/Button'
import FullPageSpinner from '../components/ui/FullPageSpinner'
import TextField from '../components/ui/TextField'
import { useAuth } from '../hooks/useAuth'

/**
 * Email + password sign-in. On success, go back to the page the user originally asked for
 * (ProtectedRoute stores it in `location.state.from`), or to the wardrobe.
 */
export default function LoginPage() {
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string } } | null)?.from?.pathname ?? '/wardrobe'

  // "Controlled" inputs: React state is the source of truth for what's typed.
  const [email, setEmail] = useState('')
  const [password, setPassword] = useState('')
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [submitting, setSubmitting] = useState(false)

  // Still checking for a saved session? Wait instead of flashing the form.
  if (loading) return <FullPageSpinner />
  // Already logged in? No reason to see this page.
  if (user) return <Navigate to={from} replace />

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault() // stop the browser's default full-page form submit
    setError(null)
    setFieldErrors({})
    setSubmitting(true)
    try {
      await login(email, password)
      navigate(from, { replace: true })
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
      title="Welcome back"
      subtitle="Log in to your wardrobe."
      footer={
        <>
          New to FitCheck?{' '}
          <Link to="/register" className="font-medium text-gray-900 underline">
            Create an account
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
          autoComplete="current-password"
          value={password}
          onChange={(e) => setPassword(e.target.value)}
          error={fieldErrors.password}
          required
        />
        <Link to="/forgot-password" className="-mt-2 self-end text-sm text-gray-600 underline hover:text-gray-900">
          Forgot password?
        </Link>
        <Button type="submit" loading={submitting}>
          Log in
        </Button>
      </form>
    </AuthCard>
  )
}
