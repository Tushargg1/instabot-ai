import { Link, Outlet, useLocation } from 'react-router-dom'
import { LayoutDashboard, Film, Cog, Instagram, Activity, LogOut, Box, HandMetal, Server, Link2, MessageSquare, Inbox } from 'lucide-react'

interface Props {
  username: string
  role: string
  onLogout: () => void
}

const navItems = [
  { path: '/', label: 'Overview', icon: LayoutDashboard },
  { path: '/reels', label: 'Reel Knowledge', icon: Film },
  { path: '/resources', label: 'Resources', icon: Box },
  { path: '/manual-actions', label: 'Manual Actions', icon: HandMetal },
  { path: '/dm-inbox', label: 'DM Inbox', icon: Inbox },
  { path: '/dm-auto-reply', label: 'DM Auto-Reply', icon: MessageSquare },
  { path: '/jobs', label: 'Processing Jobs', icon: Activity, adminOnly: true },
  { path: '/workers', label: 'Workers', icon: Server, adminOnly: true },
  { path: '/submission-links', label: 'Submission Links', icon: Link2 },
  { path: '/instagram', label: 'Main Instagram', icon: Instagram, adminOnly: true },
  { path: '/settings', label: 'Settings', icon: Cog, adminOnly: true },
]

export default function DashboardLayout({ username, role, onLogout }: Props) {
  const location = useLocation()

  return (
    <div className="flex h-screen bg-gray-50">
      {/* Sidebar */}
      <aside className="w-64 bg-white border-r border-gray-200 flex flex-col">
        <div className="p-6 border-b border-gray-200">
          <h1 className="text-xl font-bold text-gray-900">📱 Reel Intelligence</h1>
          <p className="text-sm text-gray-500 mt-1">Process once · reuse many</p>
        </div>

        <nav className="flex-1 p-4 space-y-1">
          {navItems.filter(item => !item.adminOnly || role === 'ADMIN').map(item => {
            const Icon = item.icon
            const isActive = location.pathname === item.path ||
              (item.path !== '/' && location.pathname.startsWith(item.path))
            return (
              <Link
                key={item.path}
                to={item.path}
                className={`flex items-center gap-3 px-3 py-2 rounded-lg text-sm font-medium transition-colors ${
                  isActive
                    ? 'bg-blue-50 text-blue-700'
                    : 'text-gray-600 hover:bg-gray-100 hover:text-gray-900'
                }`}
              >
                <Icon size={18} />
                {item.label}
              </Link>
            )
          })}
        </nav>

        <div className="p-4 border-t border-gray-200">
          <div className="flex items-center justify-between">
            <span className="text-sm text-gray-600">{username}</span>
            <button
              onClick={onLogout}
              className="p-2 text-gray-400 hover:text-gray-600 rounded"
              aria-label="Logout"
            >
              <LogOut size={16} />
            </button>
          </div>
        </div>
      </aside>

      {/* Main content */}
      <main className="flex-1 overflow-auto">
        <div className="p-8">
          <Outlet />
        </div>
      </main>
    </div>
  )
}
