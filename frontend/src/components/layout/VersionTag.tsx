/** "1.0.0" → "v1.0"; a patch release like "1.0.1" shows in full. */
export const appVersion = 'v' + __APP_VERSION__.replace(/\.0$/, '')

/**
 * The app's version, small and faint in the bottom-right corner of every page.
 * pointer-events-none: it can never block a click on whatever is underneath.
 */
export default function VersionTag() {
  return (
    <div
      className="pointer-events-none fixed right-3 bottom-2 z-0 text-xs text-gray-400 select-none"
      aria-label={`FitCheck version ${appVersion.slice(1)}`}
    >
      FitCheck {appVersion}
    </div>
  )
}
