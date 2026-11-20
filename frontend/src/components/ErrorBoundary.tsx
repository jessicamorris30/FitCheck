import { Component, type ErrorInfo, type ReactNode } from 'react'

interface State {
  crashed: boolean
}

/**
 * A safety net: if any component throws while rendering (a bug), React would otherwise show a
 * blank white page. This catches it and shows a friendly message with a way out instead.
 *
 * It's a class component because "error boundaries" are the one React feature that still needs
 * one: function components can't catch errors thrown while their children render.
 */
export default class ErrorBoundary extends Component<{ children: ReactNode }, State> {
  state: State = { crashed: false }

  static getDerivedStateFromError(): State {
    return { crashed: true }
  }

  componentDidCatch(error: Error, info: ErrorInfo) {
    // In production this is where you'd report to a monitoring service (step 5.10).
    console.error('FitCheck crashed while rendering', error, info.componentStack)
  }

  render() {
    if (!this.state.crashed) return this.props.children
    return (
      <div className="flex min-h-screen items-center justify-center bg-gray-50 px-4">
        <div role="alert" className="max-w-sm rounded-2xl border border-gray-200 bg-white p-8 text-center shadow-sm">
          <p className="text-3xl">🧵</p>
          <h1 className="mt-2 text-xl font-semibold">Something came apart at the seams</h1>
          <p className="mt-2 text-sm text-gray-600">
            Sorry, something went wrong on this page. Your clothes and outfits are safe.
          </p>
          <a
            href="/wardrobe"
            className="mt-6 inline-block rounded-lg bg-gray-900 px-4 py-2 font-medium text-white hover:bg-gray-700"
          >
            Back to your wardrobe
          </a>
        </div>
      </div>
    )
  }
}
