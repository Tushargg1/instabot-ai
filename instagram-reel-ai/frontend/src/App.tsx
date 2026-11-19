import { Routes, Route, Navigate } from 'react-router-dom'
import { useAuth } from './hooks/useAuth'
import DashboardLayout from './layouts/DashboardLayout'
import LoginPage from './pages/LoginPage'
import OverviewPage from './pages/OverviewPage'
import ReelsPage from './pages/ReelsPage'
import ReelDetailPage from './pages/ReelDetailPage'
import JobsPage from './pages/JobsPage'
import InstagramPage from './pages/InstagramPage'
import SettingsPage from './pages/AISettingsPage'
import ResourcesPage from './pages/ResourcesPage'
import ManualActionsPage from './pages/ManualActionsPage'
import WorkersPage from './pages/WorkersPage'
import SubmissionLinksPage from './pages/SubmissionLinksPage'
import PublicSubmitPage from './pages/PublicSubmitPage'
import DmAutoReplyPage from './pages/DmAutoReplyPage'
import DmInboxPage from './pages/DmInboxPage'
import KnowledgeHubPage from './pages/KnowledgeHubPage'

export default function App() {
  const { isAuthenticated, username, role, login, register, demoLogin, logout } = useAuth()

  return (
    <Routes>
      {/* Public submission - no auth required */}
      <Route path="/submit/:token" element={<PublicSubmitPage />} />

      {/* Auth-protected routes */}
      {!isAuthenticated ? (
        <Route path="*" element={<LoginPage onLogin={login} onRegister={register} onDemoLogin={demoLogin} />} />
      ) : (
        <>
          <Route element={<DashboardLayout username={username} role={role} onLogout={logout} />}>
            <Route path="/" element={<OverviewPage />} />
            <Route path="/knowledge" element={<KnowledgeHubPage />} />
            <Route path="/reels" element={<ReelsPage />} />
            <Route path="/reels/:id" element={<ReelDetailPage />} />
            <Route path="/jobs" element={<JobsPage />} />
            <Route path="/resources" element={<ResourcesPage />} />
            <Route path="/manual-actions" element={<ManualActionsPage />} />
            <Route path="/workers" element={<WorkersPage />} />
            <Route path="/submission-links" element={<SubmissionLinksPage />} />
            <Route path="/instagram" element={<InstagramPage />} />
            <Route path="/settings" element={<SettingsPage />} />
            <Route path="/dm-auto-reply" element={<DmAutoReplyPage />} />
            <Route path="/dm-inbox" element={<DmInboxPage />} />
          </Route>
          <Route path="*" element={<Navigate to="/" replace />} />
        </>
      )}
    </Routes>
  )
}
