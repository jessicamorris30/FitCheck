import { useState } from 'react'
import { resendVerificationEmail } from '../../api/auth'
import { parseApiError } from '../../api/errors'
import { useAuth } from '../../hooks/useAuth'

/** A reminder strip shown to logged-in users who haven't confirmed their email yet. */
export default function VerifyEmailBanner() {
  const { user } = useAuth()
  const [state, setState] = useState<'idle' | 'sending' | 'sent'>('idle')
  const [error, setError] = useState<string | null>(null)

  if (!user || user.emailVerified) return null

  async function handleResend() {
    setState('sending')
    setError(null)
    try {
      await resendVerificationEmail()
      setState('sent')
    } catch (err) {
      setError(parseApiError(err).message)
      setState('idle')
    }
  }

  return (
    <div className="flex flex-wrap items-center gap-x-3 gap-y-1 border-b border-amber-200 bg-amber-50 px-6 py-2 text-sm text-amber-900">
      <span>
        Please confirm your email. We sent a link to <strong>{user.email}</strong>.
      </span>
      {state === 'sent' ? (
        <span className="font-medium">New email sent ✓</span>
      ) : (
        <button
          onClick={handleResend}
          disabled={state === 'sending'}
          className="font-medium underline disabled:opacity-60"
        >
          {state === 'sending' ? 'Sending…' : 'Resend email'}
        </button>
      )}
      {error && <span className="text-red-700">{error}</span>}
    </div>
  )
}
