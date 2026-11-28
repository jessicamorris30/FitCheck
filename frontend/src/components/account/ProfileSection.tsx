import { useState, type FormEvent } from 'react'
import type { ProfileInput } from '../../api/auth'
import { parseApiError } from '../../api/errors'
import { useAuth } from '../../hooks/useAuth'
import type { User } from '../../types'
import Button from '../ui/Button'
import ProfileFields from './ProfileFields'

const fromUser = (user: User): ProfileInput => ({
  firstName: user.firstName ?? '',
  lastName: user.lastName ?? '',
  username: user.username ?? '',
})

/** Account page: change your name or username. */
export default function ProfileSection({ user }: { user: User }) {
  const { updateProfile } = useAuth()
  const [profile, setProfile] = useState<ProfileInput>(() => fromUser(user))
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [error, setError] = useState<string | null>(null)
  const [saving, setSaving] = useState(false)
  const [saved, setSaved] = useState(false)

  const unchanged =
    profile.firstName.trim() === (user.firstName ?? '') &&
    profile.lastName.trim() === (user.lastName ?? '') &&
    profile.username.trim().toLowerCase() === (user.username ?? '')

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setSaved(false)
    setSaving(true)
    try {
      await updateProfile(profile)
      setSaved(true)
    } catch (err) {
      const parsed = parseApiError(err)
      setFieldErrors(parsed.fieldErrors)
      if (Object.keys(parsed.fieldErrors).length === 0) setError(parsed.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <form
      onSubmit={handleSubmit}
      noValidate
      className="flex flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-6"
      aria-labelledby="profile-heading"
    >
      <div>
        <h2 id="profile-heading" className="text-lg font-semibold">
          Profile
        </h2>
        <p className="text-sm text-gray-500">Your name and username. You can log in with your username or your email.</p>
      </div>

      {error && (
        <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
          {error}
        </p>
      )}
      {saved && (
        <p role="status" className="rounded-lg bg-green-50 px-3 py-2 text-sm text-green-800">
          Profile saved ✓
        </p>
      )}

      <ProfileFields
        value={profile}
        onChange={(next) => {
          setProfile(next)
          setSaved(false)
          if (fieldErrors.username && next.username !== profile.username) {
            setFieldErrors(({ username: _old, ...rest }) => rest)
          }
        }}
        fieldErrors={fieldErrors}
        ownUsername={user.username}
      />

      <Button type="submit" loading={saving} disabled={unchanged} className="self-start">
        Save profile
      </Button>
    </form>
  )
}
