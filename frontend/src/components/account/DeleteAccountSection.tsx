import { useState, type FormEvent } from 'react'
import { useNavigate } from 'react-router-dom'
import { parseApiError } from '../../api/errors'
import { useAuth } from '../../hooks/useAuth'
import TextField from '../ui/TextField'

/**
 * "Danger zone" on the Account page. Closed by default; opening it explains exactly what will be
 * deleted, then asks for the password and an explicit "I understand" before the red button works.
 */
export default function DeleteAccountSection() {
  const { deleteAccount } = useAuth()
  const navigate = useNavigate()

  const [open, setOpen] = useState(false)
  const [password, setPassword] = useState('')
  const [understood, setUnderstood] = useState(false)
  const [deleting, setDeleting] = useState(false)
  const [error, setError] = useState<string | null>(null)
  const [fieldErrors, setFieldErrors] = useState<Record<string, string>>({})

  async function handleSubmit(e: FormEvent<HTMLFormElement>) {
    e.preventDefault()
    setError(null)
    setFieldErrors({})
    setDeleting(true)
    try {
      await deleteAccount(password)
      // replace: Back shouldn't return to the account page of an account that no longer exists.
      navigate('/login', { replace: true, state: { accountDeleted: true } })
    } catch (err) {
      const parsed = parseApiError(err)
      setFieldErrors(parsed.fieldErrors)
      if (!parsed.fieldErrors.password) setError(parsed.message)
      setDeleting(false)
    }
  }

  function cancel() {
    setOpen(false)
    setPassword('')
    setUnderstood(false)
    setError(null)
    setFieldErrors({})
  }

  return (
    <section className="flex flex-col gap-4 rounded-2xl border border-red-200 bg-white p-6" aria-labelledby="delete-heading">
      <div>
        <h2 id="delete-heading" className="text-lg font-semibold text-red-800">
          Delete account
        </h2>
        <p className="text-sm text-gray-600">Permanently remove your account and everything in it.</p>
      </div>

      {!open ? (
        <button
          type="button"
          onClick={() => setOpen(true)}
          className="self-start rounded-lg border border-red-300 px-4 py-2 font-medium text-red-700 hover:bg-red-50"
        >
          Delete my account…
        </button>
      ) : (
        <form onSubmit={handleSubmit} noValidate className="flex flex-col gap-4">
          <div className="rounded-lg bg-red-50 p-4 text-sm text-red-900">
            <p className="font-medium">This will permanently delete:</p>
            <ul className="mt-2 list-disc pl-5">
              <li>your account and login</li>
              <li>every clothing item and all of your photos</li>
              <li>all of your saved outfits and your calendar</li>
            </ul>
            <p className="mt-2 font-medium">This can't be undone.</p>
          </div>

          {error && (
            <p role="alert" className="rounded-lg bg-red-50 px-3 py-2 text-sm text-red-700">
              {error}
            </p>
          )}

          <TextField
            label="Your password"
            type="password"
            autoComplete="current-password"
            value={password}
            onChange={(e) => setPassword(e.target.value)}
            error={fieldErrors.password}
            disabled={deleting}
          />
          <label className="flex items-start gap-2 text-sm text-gray-700">
            <input
              type="checkbox"
              checked={understood}
              onChange={(e) => setUnderstood(e.target.checked)}
              disabled={deleting}
              className="mt-0.5"
            />
            I understand my account, clothes, photos and outfits will be deleted for good.
          </label>

          <div className="flex gap-3">
            <button
              type="submit"
              disabled={!password || !understood || deleting}
              className="rounded-lg bg-red-600 px-4 py-2 font-medium text-snow hover:bg-red-700 dark:hover:bg-red-400 disabled:cursor-not-allowed disabled:opacity-50"
            >
              {deleting ? 'Deleting…' : 'Delete my account'}
            </button>
            <button
              type="button"
              onClick={cancel}
              disabled={deleting}
              className="rounded-lg px-4 py-2 font-medium text-gray-700 hover:bg-gray-100 disabled:opacity-40"
            >
              Keep my account
            </button>
          </div>
        </form>
      )}
    </section>
  )
}
