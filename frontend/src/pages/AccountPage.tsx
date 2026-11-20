import { useState, type FormEvent } from 'react'
import { parseApiError } from '../api/errors'
import DeleteAccountSection from '../components/account/DeleteAccountSection'
import Button from '../components/ui/Button'
import TextField from '../components/ui/TextField'
import { useAuth } from '../hooks/useAuth'

/**
 * /account: your account details and settings: change password, delete account.
 */
export default function AccountPage() {
  const { user, changePassword } = useAuth()

  const [current, setCurrent] = useState('')
  const [next, setNext] = useState('')
  const [confirm, setConfirm] = useState('')
  const [saving, setSaving] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})
  const [done, setDone] = useState(false)

  if (!user) return null // ProtectedRoute guarantees a user; this keeps TypeScript happy

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setDone(false)
    if (next !== confirm) {
      setFieldErrors({ confirm: 'Passwords do not match' })
      return
    }
    setFieldErrors({})
    setSaving(true)
    try {
      await changePassword(current, next)
      setCurrent('')
      setNext('')
      setConfirm('')
      setDone(true)
    } catch (err) {
      const parsed = parseApiError(err)
      setFieldErrors(parsed.fieldErrors)
      // Field problems are shown under the fields; only show the banner for other errors.
      if (Object.keys(parsed.fieldErrors).length === 0) setError(parsed.message)
    } finally {
      setSaving(false)
    }
  }

  return (
    <section className="mx-auto flex max-w-xl flex-col gap-8">
      <header>
        <h1 className="text-2xl font-semibold">Your account</h1>
        <p className="mt-1 text-gray-600">
          {user.email}{' '}
          {user.emailVerified ? (
            <span className="text-sm text-green-700">· confirmed ✓</span>
          ) : (
            <span className="text-sm text-amber-700">· not confirmed yet</span>
          )}
        </p>
        <p className="text-sm text-gray-500">
          Member since {new Date(user.createdAt).toLocaleDateString(undefined, { dateStyle: 'long' })}
        </p>
      </header>

      <form
        onSubmit={handleSubmit}
        noValidate
        className="flex flex-col gap-4 rounded-2xl border border-gray-200 bg-white p-6"
        aria-labelledby="change-password-heading"
      >
        <div>
          <h2 id="change-password-heading" className="text-lg font-semibold">
            Change password
          </h2>
          <p className="text-sm text-gray-500">You'll stay logged in here. Your other devices will be signed out.</p>
        </div>

        {done && (
          <p role="status" className="rounded-lg bg-green-50 px-3 py-2 text-sm text-green-800">
            Password changed ✓ Any other devices have been signed out.
          </p>
        )}
        {error && (
          <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
            {error}
          </p>
        )}

        <TextField
          label="Current password"
          type="password"
          autoComplete="current-password"
          value={current}
          onChange={(e) => setCurrent(e.target.value)}
          error={fieldErrors.currentPassword}
          disabled={saving}
        />
        <TextField
          label="New password"
          type="password"
          autoComplete="new-password"
          placeholder="At least 8 characters"
          value={next}
          onChange={(e) => setNext(e.target.value)}
          error={fieldErrors.newPassword}
          disabled={saving}
        />
        <TextField
          label="Confirm new password"
          type="password"
          autoComplete="new-password"
          value={confirm}
          onChange={(e) => setConfirm(e.target.value)}
          error={fieldErrors.confirm}
          disabled={saving}
        />
        <Button type="submit" loading={saving} disabled={!current || !next || !confirm} className="self-start">
          Change password
        </Button>
      </form>

      <DeleteAccountSection />
    </section>
  )
}
