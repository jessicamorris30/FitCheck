import { Navigate, Route, Routes } from 'react-router-dom'
import AccountPage from './pages/AccountPage.tsx'
import Layout from './components/layout/Layout.tsx'
import ClothingDetailPage from './pages/ClothingDetailPage.tsx'
import ForgotPasswordPage from './pages/ForgotPasswordPage.tsx'
import LoginPage from './pages/LoginPage.tsx'
import ResetPasswordPage from './pages/ResetPasswordPage.tsx'
import NotFoundPage from './pages/NotFoundPage.tsx'
import EditOutfitPage from './pages/EditOutfitPage.tsx'
import OutfitDetailPage from './pages/OutfitDetailPage.tsx'
import OutfitsPage from './pages/OutfitsPage.tsx'
import PlanPage from './pages/PlanPage.tsx'
import RegisterPage from './pages/RegisterPage.tsx'
import VerifyEmailPage from './pages/VerifyEmailPage.tsx'
import WardrobePage from './pages/WardrobePage.tsx'
import ProtectedRoute from './routes/ProtectedRoute.tsx'
import VersionTag from './components/layout/VersionTag.tsx'

export default function App() {
  return (
    <>
      <AppRoutes />
      <VersionTag />
    </>
  )
}

function AppRoutes() {
  return (
    <Routes>
      <Route path="/login" element={<LoginPage />} />
      <Route path="/register" element={<RegisterPage />} />
      {/* Public: the email link may be opened on a device where the user isn't logged in. */}
      <Route path="/verify-email" element={<VerifyEmailPage />} />
      <Route path="/forgot-password" element={<ForgotPasswordPage />} />
      <Route path="/reset-password" element={<ResetPasswordPage />} />

      <Route element={<ProtectedRoute />}>
        <Route element={<Layout />}>
          <Route index element={<Navigate to="/wardrobe" replace />} />
          <Route path="/wardrobe" element={<WardrobePage />} />
          <Route path="/wardrobe/:id" element={<ClothingDetailPage />} />
          <Route path="/plan" element={<PlanPage />} />
          <Route path="/outfits" element={<OutfitsPage />} />
          <Route path="/account" element={<AccountPage />} />
          <Route path="/outfits/:id" element={<OutfitDetailPage />} />
          <Route path="/outfits/:id/edit" element={<EditOutfitPage />} />
        </Route>
      </Route>

      <Route path="*" element={<NotFoundPage />} />
    </Routes>
  )
}
