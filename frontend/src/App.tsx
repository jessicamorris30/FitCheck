import { Navigate, Route, Routes } from 'react-router-dom'
import Layout from './components/layout/Layout.tsx'
import ClothingDetailPage from './pages/ClothingDetailPage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import NotFoundPage from './pages/NotFoundPage.tsx'
import PlanPage from './pages/PlanPage.tsx'
import RegisterPage from './pages/RegisterPage.tsx'
import VerifyEmailPage from './pages/VerifyEmailPage.tsx'
import WardrobePage from './pages/WardrobePage.tsx'
import ProtectedRoute from './routes/ProtectedRoute.tsx'

export default function App() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      {/* Public: the email link may be opened on a device where the user isn't logged in. */}
      <Route path="/verify-email" element={<VerifyEmailPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route index element={<Navigate to="/wardrobe" replace />} />
          <Route path="/wardrobe" element={<WardrobePage />} />
          <Route path="/wardrobe/:id" element={<ClothingDetailPage />} />
          <Route path="/plan" element={<PlanPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
