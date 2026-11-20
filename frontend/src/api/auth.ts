import type { AuthResponse, User } from '../types'
import { api } from './client'

// Endpoints under /api/auth (docs/API.md). Refreshing lives in client.ts (refreshSession),
// because it must be shared with the 401 interceptor.

export async function register(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/register', { email, password })
  return data
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', { email, password })
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
