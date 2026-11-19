# FitCheck frontend

React + TypeScript + Tailwind CSS v4, built with Vite.

```bash
npm install
npm run dev     # http://localhost:5173 (proxies /api to the backend on :8080)
npm run build   # type-check + production build
npm run lint    # oxlint
```

Source layout:

| Folder | Purpose |
|---|---|
| `src/api/` | axios client and one module per backend resource |
| `src/context/`, `src/hooks/` | auth state (`AuthProvider`, `useAuth`) |
| `src/routes/` | route guards |
| `src/pages/` | one component per route |
| `src/components/` | layout, wardrobe and plan-mode components |
| `src/types/` | shared API types (mirror the backend DTOs) |

See [../docs/](../docs/) for architecture, API and auth docs.
