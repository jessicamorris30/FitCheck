import { Outlet } from 'react-router-dom'
import Navbar from './Navbar'

// App shell for signed-in pages.
export default function Layout() {
  return (
    <div className="min-h-screen bg-white text-gray-900">
      <Navbar />
      <main className="mx-auto max-w-6xl px-4 py-6">
        <Outlet />
      </main>
    </div>
  )
}
