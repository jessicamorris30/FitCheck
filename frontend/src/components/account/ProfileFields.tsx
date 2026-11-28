import type { ProfileInput } from '../../api/auth'
import { useUsernameCheck } from '../../hooks/useUsernameCheck'
import TextField from '../ui/TextField'

interface ProfileFieldsProps {
  value: ProfileInput
  onChange: (value: ProfileInput) => void
  /** Errors from the server (fieldErrors.username / firstName / lastName). */
  fieldErrors: Record<string, string>
  /** The user's current username when editing (keeping it isn't "taken"). */
  ownUsername?: string | null
}

/**
 * First name, last name and username, with a live "is this username free?" check.
 * Shared by sign-up, "Finish your profile" and the Account page, so all three behave the same.
 */
export default function ProfileFields({ value, onChange, fieldErrors, ownUsername }: ProfileFieldsProps) {
  const status = useUsernameCheck(value.username, ownUsername)

  // The server's answer on submit wins; otherwise show what the live check found.
  const usernameError =
    fieldErrors.username ?? (status.state === 'unavailable' ? status.message : undefined)
  const usernameHint =
    status.state === 'checking' ? (
      'Checking…'
    ) : status.state === 'available' ? (
      <span className="text-green-700">✓ Available</span>
    ) : (
      'Letters, numbers, dots and underscores. You can log in with it too.'
    )

  return (
    <>
      <div className="grid grid-cols-1 gap-4 sm:grid-cols-2">
        <TextField
          label="First name"
          autoComplete="given-name"
          value={value.firstName}
          onChange={(e) => onChange({ ...value, firstName: e.target.value })}
          error={fieldErrors.firstName}
          maxLength={50}
          required
        />
        <TextField
          label="Last name"
          autoComplete="family-name"
          value={value.lastName}
          onChange={(e) => onChange({ ...value, lastName: e.target.value })}
          error={fieldErrors.lastName}
          maxLength={50}
          required
        />
      </div>
      <TextField
        label="Username"
        autoComplete="username"
        autoCapitalize="none"
        spellCheck={false}
        value={value.username}
        onChange={(e) => onChange({ ...value, username: e.target.value })}
        error={usernameError}
        hint={usernameHint}
        maxLength={30}
        placeholder="e.g. jess.styles"
        required
      />
    </>
  )
}
