import { NavLink } from 'react-router-dom'

// Top navigation: Wardrobe, Plan, account menu.
// TODO: add logout button and signed-in user email.
export default function Navbar() {
  const link = ({ isActive }: { isActive: boolean }) =>
    isActive ? 'font-semibold text-gray-900' : 'text-gray-500 hover:text-gray-900'

  return (
    <nav className="flex items-center gap-6 border-b border-gray-200 px-6 py-3">
      <span className="text-lg font-bold">FitCheck</span>
      <NavLink to="/wardrobe" className={link}>
        Wardrobe
      </NavLink>
      <NavLink to="/plan" className={link}>
        Plan
      </NavLink>
    </nav>
  )
}
