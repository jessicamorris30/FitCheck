import { Navigate, Outlet, useLocation } from 'react-router-dom'
import FullPageSpinner from '../components/ui/FullPageSpinner'
import { useAuth } from '../hooks/useAuth'

/**
 * Wraps every page that needs a logged-in user (see App.tsx).
 *
 *  - Still checking for a saved session? Show a spinner, not the login page. Otherwise a
 *    logged-in user would see the login form flash on every reload.
 *  - Not logged in? Go to /login, remembering where they were headed (`state.from`) so the
 *    login page can send them back there afterwards.
 *  - Logged in? Render the requested page (<Outlet />).
 */
export default function ProtectedRoute() {
  const { user, loading } = useAuth()
  const location = useLocation()

  if (loading) return <FullPageSpinner />
  if (!user) return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}
