import { NavLink, useNavigate } from 'react-router-dom'
import { useAuth } from '../../hooks/useAuth'

/** Top navigation: Wardrobe, Plan, Outfits, Calendar, and the signed-in user with a logout button. */
export default function Navbar() {
  const { user, logout } = useAuth()
  const navigate = useNavigate()

  const link = ({ isActive }: { isActive: boolean }) =>
    isActive ? 'font-semibold text-gray-900' : 'text-gray-500 hover:text-gray-900'

  async function handleLogout() {
    await logout()
    navigate('/login', { replace: true })
  }

  return (
    <nav className="flex flex-wrap items-center gap-x-6 gap-y-2 border-b border-gray-200 px-6 py-3">
      <span className="text-lg font-bold">FitCheck</span>
      <NavLink to="/wardrobe" className={link}>
        Wardrobe
      </NavLink>
      <NavLink to="/plan" className={link}>
        Plan
      </NavLink>
      <NavLink to="/outfits" className={link}>
        Outfits
      </NavLink>
      <NavLink to="/calendar" className={link}>
        Calendar
      </NavLink>

      <div className="ml-auto flex items-center gap-4 text-sm">
        {user && (
          <NavLink to="/account" title={`Your account (${user.email})`} className={link}>
            {user.username ? `@${user.username}` : user.email}
          </NavLink>
        )}
        {user && (
          <button onClick={handleLogout} className="font-medium text-gray-900 hover:underline">
            Log out
          </button>
        )}
      </div>
    </nav>
  )
}
