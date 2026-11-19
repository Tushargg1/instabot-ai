import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import {
  TrendingUp,
  Users,
  UserCheck,
  Award,
  Sparkles,
  BarChart3,
  Video,
  Clock,
  RefreshCw,
  Mail
} from 'lucide-react'

interface ConversionStats {
  totalRequests: number
  totalUniqueUsers: number
  nonFollowerTeasersSent: number
  convertedToFollowers: number
  conversionRatePercentage: number
  totalLeadsCaptured: number
}

interface TrendingTopic {
  category: string
  subCategory?: string
  count: number
  percentage: number
}

interface Superfan {
  rank: number
  senderId: string
  username: string | null
  name: string | null
  profilePic: string | null
  isFollowing: boolean | null
  totalRequests: number
  lastActiveAt: string | null
}

interface AnalyticsData {
  conversionStats: ConversionStats
  trendingTopics: TrendingTopic[]
  superfans: Superfan[]
}

function formatRelativeTime(iso: string | null) {
  if (!iso) return 'Unknown'
  const d = new Date(iso)
  const now = new Date()
  const diffMs = now.getTime() - d.getTime()
  const diffMins = Math.floor(diffMs / 60000)
  if (diffMins < 1) return 'just now'
  if (diffMins < 60) return `${diffMins}m ago`
  const diffHours = Math.floor(diffMins / 60)
  if (diffHours < 24) return `${diffHours}h ago`
  const diffDays = Math.floor(diffHours / 24)
  if (diffDays < 7) return `${diffDays}d ago`
  return d.toLocaleDateString(undefined, { month: 'short', day: 'numeric' })
}

export default function AnalyticsPage() {
  const { data, isLoading, refetch, isFetching } = useQuery<AnalyticsData>({
    queryKey: ['analytics'],
    queryFn: () => apiClient.getAnalytics(),
    staleTime: 30000,
  })

  const conversion = data?.conversionStats
  const topics = data?.trendingTopics || []
  const superfans = data?.superfans || []

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <BarChart3 className="w-7 h-7 text-indigo-600" />
            Audience Intelligence & Analytics
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            Real-time conversion tracking, audience interest topics, and superfans leaderboard
          </p>
        </div>
        <button
          onClick={() => refetch()}
          disabled={isFetching}
          className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 shadow-xs transition-colors self-start sm:self-auto disabled:opacity-50"
        >
          <RefreshCw className={`w-4 h-4 ${isFetching ? 'animate-spin' : ''}`} />
          Refresh
        </button>
      </div>

      {isLoading ? (
        <div className="flex justify-center items-center py-24 text-gray-400">
          <RefreshCw className="w-8 h-8 animate-spin" />
        </div>
      ) : (
        <>
          {/* Top Metric Cards */}
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-4 gap-4">
            <div className="bg-white rounded-xl border border-gray-200 p-5 shadow-xs">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Conversion Rate
                </span>
                <span className="p-2 rounded-lg bg-emerald-50 text-emerald-600">
                  <TrendingUp className="w-5 h-5" />
                </span>
              </div>
              <div className="mt-3">
                <span className="text-3xl font-extrabold text-gray-900">
                  {conversion?.conversionRatePercentage ?? 0}%
                </span>
                <p className="text-xs text-gray-500 mt-1">
                  Non-followers who followed after teaser
                </p>
              </div>
              <div className="mt-3 pt-3 border-t border-gray-100 flex justify-between text-xs text-gray-600">
                <span>{conversion?.convertedToFollowers ?? 0} converted</span>
                <span>{conversion?.nonFollowerTeasersSent ?? 0} teasers sent</span>
              </div>
            </div>

            <div className="bg-white rounded-xl border border-gray-200 p-5 shadow-xs">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Leads Captured
                </span>
                <span className="p-2 rounded-lg bg-indigo-50 text-indigo-600">
                  <Mail className="w-5 h-5" />
                </span>
              </div>
              <div className="mt-3">
                <span className="text-3xl font-extrabold text-gray-900">
                  {conversion?.totalLeadsCaptured ?? 0}
                </span>
                <p className="text-xs text-gray-500 mt-1">
                  Verified emails collected in DMs
                </p>
              </div>
              <div className="mt-3 pt-3 border-t border-gray-100 flex justify-between text-xs text-gray-600">
                <span className="text-indigo-600 font-medium">VIP List</span>
                <span>Saved to database</span>
              </div>
            </div>

            <div className="bg-white rounded-xl border border-gray-200 p-5 shadow-xs">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Total Requesters
                </span>
                <span className="p-2 rounded-lg bg-blue-50 text-blue-600">
                  <Users className="w-5 h-5" />
                </span>
              </div>
              <div className="mt-3">
                <span className="text-3xl font-extrabold text-gray-900">
                  {conversion?.totalUniqueUsers ?? 0}
                </span>
                <p className="text-xs text-gray-500 mt-1">
                  Unique creators & followers served
                </p>
              </div>
              <div className="mt-3 pt-3 border-t border-gray-100 flex justify-between text-xs text-gray-600">
                <span>{conversion?.totalRequests ?? 0} total requests</span>
                <span>Active DMs</span>
              </div>
            </div>

            <div className="bg-white rounded-xl border border-gray-200 p-5 shadow-xs">
              <div className="flex items-center justify-between">
                <span className="text-xs font-semibold text-gray-500 uppercase tracking-wider">
                  Audience Growth
                </span>
                <span className="p-2 rounded-lg bg-purple-50 text-purple-600">
                  <UserCheck className="w-5 h-5" />
                </span>
              </div>
              <div className="mt-3">
                <span className="text-3xl font-extrabold text-gray-900">
                  +{conversion?.convertedToFollowers ?? 0}
                </span>
                <p className="text-xs text-gray-500 mt-1">
                  New followers unlocked via bot
                </p>
              </div>
              <div className="mt-3 pt-3 border-t border-gray-100 flex justify-between text-xs text-gray-600">
                <span>@unireelverse.in</span>
                <span className="text-emerald-600 font-medium">Organic</span>
              </div>
            </div>
          </div>

          <div className="grid grid-cols-1 lg:grid-cols-2 gap-6">
            {/* Trending Topics & Categories */}
            <div className="bg-white rounded-xl border border-gray-200 p-6 shadow-xs space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
                    <Sparkles className="w-5 h-5 text-amber-500" />
                    Top Trending Topics
                  </h2>
                  <p className="text-xs text-gray-500 mt-0.5">
                    What your audience is requesting and extracting most
                  </p>
                </div>
                <span className="text-xs font-semibold text-gray-400">
                  {topics.length} Categories
                </span>
              </div>

              {topics.length === 0 ? (
                <div className="text-center py-10 text-gray-400 text-sm">
                  No topic extractions recorded yet. Share reels to generate topics!
                </div>
              ) : (
                <div className="space-y-4 pt-2">
                  {topics.map((t, idx) => (
                    <div key={`${t.category}-${t.subCategory}-${idx}`} className="space-y-1.5">
                      <div className="flex items-center justify-between text-sm">
                        <span className="font-medium text-gray-800">
                          {t.category}{t.subCategory ? ` › ${t.subCategory}` : ''}
                        </span>
                        <div className="flex items-center gap-2">
                          <span className="text-xs text-gray-500 font-medium">
                            {t.count} items
                          </span>
                          <span className="text-sm font-bold text-indigo-600">
                            {t.percentage}%
                          </span>
                        </div>
                      </div>
                      <div className="w-full bg-gray-100 rounded-full h-2.5 overflow-hidden">
                        <div
                          className="bg-gradient-to-r from-indigo-500 to-purple-600 h-2.5 rounded-full transition-all duration-500"
                          style={{ width: `${Math.min(100, Math.max(4, t.percentage))}%` }}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>

            {/* Superfans Leaderboard */}
            <div className="bg-white rounded-xl border border-gray-200 p-6 shadow-xs space-y-4">
              <div className="flex items-center justify-between">
                <div>
                  <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
                    <Award className="w-5 h-5 text-amber-500" />
                    Superfans Leaderboard
                  </h2>
                  <p className="text-xs text-gray-500 mt-0.5">
                    Top followers who share the most Reels & interact frequently
                  </p>
                </div>
                <span className="text-xs font-semibold text-indigo-600 bg-indigo-50 px-2 py-0.5 rounded-full">
                  Top VIPs
                </span>
              </div>

              {superfans.length === 0 ? (
                <div className="text-center py-10 text-gray-400 text-sm">
                  No user activity recorded yet.
                </div>
              ) : (
                <div className="divide-y divide-gray-100">
                  {superfans.slice(0, 7).map((fan) => (
                    <div key={fan.senderId} className="py-3 flex items-center justify-between gap-3">
                      <div className="flex items-center gap-3 min-w-0">
                        {/* Rank Badge */}
                        <div className="w-6 flex items-center justify-center font-bold text-sm shrink-0">
                          {fan.rank === 1 && <span className="text-xl">🥇</span>}
                          {fan.rank === 2 && <span className="text-xl">🥈</span>}
                          {fan.rank === 3 && <span className="text-xl">🥉</span>}
                          {fan.rank > 3 && <span className="text-gray-400">#{fan.rank}</span>}
                        </div>

                        {/* Avatar */}
                        {fan.profilePic ? (
                          <img
                            src={fan.profilePic}
                            alt={fan.username || fan.senderId}
                            className="w-10 h-10 rounded-full object-cover border border-gray-200 shrink-0"
                          />
                        ) : (
                          <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-600 text-white font-bold flex items-center justify-center text-sm shrink-0">
                            {(fan.name || fan.username || fan.senderId).slice(0, 2).toUpperCase()}
                          </div>
                        )}

                        {/* User Details */}
                        <div className="min-w-0">
                          <div className="flex items-center gap-1.5 flex-wrap">
                            <span className="font-semibold text-gray-900 text-sm truncate">
                              {fan.name || (fan.username ? `@${fan.username}` : fan.senderId)}
                            </span>
                            {fan.isFollowing && (
                              <span className="text-[10px] bg-emerald-100 text-emerald-800 font-semibold px-1.5 py-0.2 rounded-full">
                                Follower
                              </span>
                            )}
                          </div>
                          <div className="text-xs text-gray-500 flex items-center gap-2">
                            {fan.username && <span>@{fan.username}</span>}
                            <span>•</span>
                            <span className="flex items-center gap-0.5">
                              <Clock className="w-3 h-3 text-gray-400" />
                              {formatRelativeTime(fan.lastActiveAt)}
                            </span>
                          </div>
                        </div>
                      </div>

                      {/* Request count */}
                      <div className="text-right shrink-0">
                        <span className="inline-flex items-center gap-1 text-sm font-bold text-indigo-600 bg-indigo-50 px-2.5 py-1 rounded-lg">
                          <Video className="w-3.5 h-3.5" />
                          {fan.totalRequests} {fan.totalRequests === 1 ? 'Reel' : 'Reels'}
                        </span>
                      </div>
                    </div>
                  ))}
                </div>
              )}
            </div>
          </div>
        </>
      )}
    </div>
  )
}
