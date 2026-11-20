import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { verifyEmail } from '../api/auth'
import { parseApiError } from '../api/errors'
import AuthCard from '../components/auth/AuthCard'
import { useAuth } from '../hooks/useAuth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

type Status = 'verifying' | 'success' | 'error'

/**
 * The page the confirmation email links to: /verify-email?token=...
 *
 * It sends the token to the backend as soon as it opens. The link points at this page rather
 * than straight at the API so the user sees a proper result screen, and the actual change
 * happens through a POST request rather than just by visiting a URL.
 */
export default function VerifyEmailPage() {
  useDocumentTitle('Confirm email')
  const [params] = useSearchParams()
  const token = params.get('token')
  const { user, updateUser } = useAuth()

  const [status, setStatus] = useState<Status>(token ? 'verifying' : 'error')
  const [message, setMessage] = useState(token ? '' : 'This confirmation link is missing its token.')

  useEffect(() => {
    if (!token) return
    let cancelled = false
    verifyEmail(token)
      .then((verified) => {
        if (cancelled) return
        updateUser(verified) // removes the "please confirm" banner if this user is logged in here
        setStatus('success')
      })
      .catch((err) => {
        if (cancelled) return
        setStatus('error')
        setMessage(parseApiError(err).message)
      })
    // StrictMode may run this twice in development. That's fine: the backend treats a second
    // click of a valid link as success.
    return () => {
      cancelled = true
    }
  }, [token, updateUser])

  const next = user ? { to: '/wardrobe', label: 'Go to your wardrobe' } : { to: '/login', label: 'Log in' }

  return (
    <AuthCard
      title={status === 'success' ? 'Email confirmed 🎉' : status === 'error' ? 'Link not valid' : 'Confirming…'}
      subtitle={
        status === 'success'
          ? 'Thanks! Your FitCheck account is all set.'
          : status === 'error'
            ? message
            : 'One moment while we confirm your email.'
      }
      footer={
        status === 'error' && user ? 'You can request a new link from the banner at the top of the app.' : null
      }
    >
      {status !== 'verifying' && (
        <Link
          to={next.to}
          className="block rounded-lg bg-gray-900 px-4 py-2 text-center font-medium text-white hover:bg-gray-700"
        >
          {next.label}
        </Link>
      )}
    </AuthCard>
  )
}
