import type { AuthResponse } from '../types'
import { api } from './client'

// TODO: implement once the backend /api/auth endpoints exist (docs/API.md#auth).

export async function register(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/register', { email, password })
  return data
}

export async function login(email: string, password: string): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/login', { email, password })
  return data
}

/** Exchanges the refresh-token cookie for a new access token (and a rotated cookie). */
export async function refresh(): Promise<AuthResponse> {
  const { data } = await api.post<AuthResponse>('/auth/refresh')
  return data
}

export async function logout(): Promise<void> {
  await api.post('/auth/logout')
}
