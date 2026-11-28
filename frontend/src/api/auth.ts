import type { AuthResponse, User } from '../types'
import { api } from './client'

// Endpoints under /api/auth (docs/API.md). Refreshing lives in client.ts (refreshSession),
// because it must be shared with the 401 interceptor.

/** Username and name: what sign-up, "finish your profile" and the Account page all send. */
export interface ProfileInput {
  username: string
  firstName: string
  lastName: string
}

export interface RegisterInput extends ProfileInput {
  email: string
  password: string
}

export async function register(input: RegisterInput): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/register', input)
  return data
}

/** @param login an email or a username (the backend tells them apart by the '@') */
export async function login(login: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', { login, password })
  return data
}

export interface UsernameCheck {
  /** As it would be stored: trimmed, lower case. */
  username: string
  available: boolean
  /** Why not, when it isn't available. */
  message: string | null
}

/** The live "is this username free?" check while typing. */
export async function checkUsername(username: string): Promise<UsernameCheck> {
  const { data } = await api.post<UsernameCheck>('/auth/check-username', { username })
  return data
}

/** Sets or changes the logged-in user's username and name. */
export async function updateProfile(input: ProfileInput): Promise<User> {
  const { data } = await api.put<User>('/users/me/profile', input)
  return data
}

export async function logout(): Promise<void> {
  await api.post('/auth/logout')
}

/**
 * Changes the password. Every other device is signed out; this device gets a fresh session
 * (new access token in the response, new refresh cookie set by the server).
 */
export async function changePassword(currentPassword: string, newPassword: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/change-password', { currentPassword, newPassword })
  return data
}

/** Permanently deletes the logged-in account and everything in it. Needs the password. */
export async function deleteAccount(password: string): Promise<void> {
  await api.delete('/users/me', { data: { password } })
}

/** Asks for a reset link by email. Always succeeds, whether or not the email has an account. */
export async function forgotPassword(email: string): Promise<void> {
  await api.post('/auth/forgot-password', { email })
}

/** Sets a new password using the token from the reset email. Afterwards, log in normally. */
export async function resetPassword(token: string, newPassword: string): Promise<void> {
  await api.post('/auth/reset-password', { token, newPassword })
}

/** Confirms an email address using the token from the link in the confirmation email. */
export async function verifyEmail(token: string): Promise<User> {
  const { data } = await api.post<User>('/auth/verify-email', { token })
  return data
}

/** Sends the confirmation email again (logged-in user only). */
export async function resendVerificationEmail(): Promise<void> {
  await api.post('/users/me/verification-email')
}
