import { useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { parseApiError } from '../api/errors'
import AuthCard from '../components/auth/AuthCard'
import Button from '../components/ui/Button'
import FullPageSpinner from '../components/ui/FullPageSpinner'
import TextField from '../components/ui/TextField'
import { useAuth } from '../hooks/useAuth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

/**
 * Sign-in with email or username + password. On success, go back to the page the user originally asked for
 * (ProtectedRoute stores it in `location.state.from`), or to the wardrobe.
 */
export default function LoginPage() {
  useDocumentTitle('Log in')
  const { user, loading, login } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const navState = location.state as { from?: { pathname: string }; accountDeleted?: boolean } | null
  const from = navState?.from?.pathname ?? '/wardrobe'
  const accountDeleted = navState?.accountDeleted === true

  // "Controlled" inputs: React state is the source of truth for what's typed.
  const [loginName, setLoginName] = useState('') // email or username
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
      await login(loginName, password)
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
        {accountDeleted && (
          <p role="status" className="rounded-lg bg-gray-100 px-3 py-2 text-sm text-gray-700">
            Your account and everything in it have been deleted. Sorry to see you go!
          </p>
        )}
        {error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}
        <TextField
          label="Email or username"
          type="text"
          autoComplete="username"
          autoCapitalize="none"
          spellCheck={false}
          value={loginName}
          onChange={(e) => setLoginName(e.target.value)}
          error={fieldErrors.login}
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
