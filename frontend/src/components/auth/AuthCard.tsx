import type { ReactNode } from 'react'

interface AuthCardProps {
  title: string
  subtitle: string
  children: ReactNode
  footer: ReactNode
}

/** Centered card layout shared by the Login and Register pages. */
export default function AuthCard({ title, subtitle, children, footer }: AuthCardProps) {
  return (
    <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
      <div className="w-full max-w-sm">
        <p className="mb-8 text-center text-2xl font-bold tracking-tight">FitCheck</p>
        <div className="rounded-2xl border border-gray-200 bg-white p-8 shadow-sm">
          <h1 className="text-xl font-semibold">{title}</h1>
          <p className="mt-1 mb-6 text-sm text-gray-500">{subtitle}</p>
          {children}
        </div>
        <p className="mt-6 text-center text-sm text-gray-600">{footer}</p>
      </div>
    </div>
  )
}
