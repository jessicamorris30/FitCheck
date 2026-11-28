// Applies the saved light/dark choice BEFORE the page is drawn, so dark-mode users never see a
// white flash while the app loads. Loaded from <head> in index.html, ahead of everything else.
// (A separate file rather than inline <script>, so a strict Content-Security-Policy can allow it.)
// The app takes over from here: see src/context/ThemeContext.tsx.
;(function () {
  try {
    var choice = localStorage.getItem('fitcheck-theme') || 'system'
    var dark = choice === 'dark' || (choice === 'system' && window.matchMedia('(prefers-color-scheme: dark)').matches)
    document.documentElement.classList.toggle('dark', dark)
  } catch {
    // Storage blocked (e.g. strict privacy settings): the app still starts, in light mode.
  }
})()
