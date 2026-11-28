import { useState, type FormEvent } from 'react'
import { Navigate, useLocation, useNavigate } from 'react-router-dom'
import type { ProfileInput } from '../api/auth'
import { parseApiError } from '../api/errors'
import ProfileFields from '../components/account/ProfileFields'
import AuthCard from '../components/auth/AuthCard'
import Button from '../components/ui/Button'
import { useAuth } from '../hooks/useAuth'
import { useDocumentTitle } from '../hooks/useDocumentTitle'

/**
 * A username to suggest from an email: "Jessica.M+news@gmail.com" → "jessica.m".
 * Only a suggestion (the live check says if it's free); the user can change it.
 */
function suggestUsername(email: string): string {
  const local = email.split('@')[0].split('+')[0].toLowerCase()
  const cleaned = local.replace(/[^a-z0-9._]/g, '').replace(/^[._]+/, '')
  return cleaned.length >= 3 ? cleaned.slice(0, 30) : ''
}

/**
 * /finish-profile: accounts made before usernames existed land here after logging in, and can't
 * use the rest of the app until they've picked a username and given their name (ProtectedRoute).
 * Afterwards they continue to wherever they were going.
 */
export default function FinishProfilePage() {
  useDocumentTitle('Finish your profile')
  const { user, updateProfile, logout } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const from = (location.state as { from?: { pathname: string; search?: string } } | null)?.from
  const destination = from ? from.pathname + (from.search ?? '') : '/wardrobe'

  const [profile, setProfile] = useState<ProfileInput>(() => ({
    firstName: user?.firstName ?? '',
    lastName: user?.lastName ?? '',
    username: user?.username ?? suggestUsername(user?.email ?? ''),
  }))
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)

  if (!user) return null // ProtectedRoute guarantees a user
  if (user.profileComplete) return <Navigate to={destination} replace />

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSaving(true)
    try {
      await updateProfile(profile)
      navigate(destination, { replace: true })
    } catch (err) {
      const parsed = parseApiError(err)
      setFieldErrors(parsed.fieldErrors)
      if (Object.keys(parsed.fieldErrors).length === 0) setError(parsed.message)
      setSaving(false)
    }
  }

  return (
    <AuthCard
      title="Finish your profile"
      subtitle="FitCheck now has usernames. Pick yours and tell us your name to continue."
      footer={
        <>
          Signed in as {user.email} ·{' '}
          <button type="button" onClick={() => logout()} className="font-medium text-gray-900 underline">
            Log out
          </button>
        </>
      }
    >
      <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
        {error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}
        <ProfileFields
          value={profile}
          onChange={(next) => {
            setProfile(next)
            if (fieldErrors.username && next.username !== profile.username) {
              setFieldErrors(({ username: _old, ...rest }) => rest)
            }
          }}
          fieldErrors={fieldErrors}
        />
        <Button type="submit" loading={saving} className="mt-2">
          Continue
        </Button>
      </form>
    </AuthCard>
  )
}
