import { Outlet } from 'react-router-dom'
import Navbar from './Navbar'
import VerifyEmailBanner from './VerifyEmailBanner'

// App shell for signed-in pages.
export default function Layout() {
  return (
    <div className="min-h-screen bg-white text-gray-900">
      {/* Invisible until focused: keyboard users press Tab once and can jump past the navbar. */}
      <a
        href="#main"
        className="sr-only focus:not-sr-only focus:absolute focus:top-2 focus:left-2 focus:z-50 focus:rounded-lg focus:bg-gray-900 focus:px-4 focus:py-2 focus:text-white"
      >
        Skip to content
      </a>
      <Navbar />
      <VerifyEmailBanner />
      {/* pb-10: room at the very bottom so the fixed version tag never sits on top of content. */}
      <main id="main" tabIndex={-1} className="mx-auto max-w-6xl px-4 pt-6 pb-10 outline-none">
        <Outlet />
      </main>
    </div>
  )
}
