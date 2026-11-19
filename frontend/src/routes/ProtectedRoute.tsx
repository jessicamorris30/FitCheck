import { Outlet } from 'react-router-dom'
import { useAuth } from '../hooks/useAuth'

/**
 * Guards routes that require a signed-in user.
 * TODO: once auth works, redirect to /login when `user` is null:
 *   if (!user) return <Navigate to="/login" replace state={{ from: location }} />
 * Left open for now so placeholder pages are reachable during development.
 */
export default function ProtectedRoute() {
  const { loading } = useAuth()
  if (loading) return <p className="p-8 text-gray-500">Loading…</p>
  return <Outlet />
}
