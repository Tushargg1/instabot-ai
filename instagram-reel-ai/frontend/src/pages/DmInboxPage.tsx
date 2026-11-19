import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { Inbox, RefreshCw, ChevronLeft, ChevronRight, Image, Video, Share2, Film, MessageSquare, Search, X, CheckCircle2, UserX, ExternalLink } from 'lucide-react'

interface DmEvent {
  id: string
  externalEventId: string
  senderId: string
  recipientId: string
  senderUsername?: string | null
  senderName?: string | null
  senderProfilePic?: string | null
  isUserFollowBusiness?: boolean | null
  eventType: string
  messageText: string | null
  attachmentType: string | null
  attachmentUrl: string | null
  processed: boolean
  receivedAt: string
}

const TYPE_ICONS: Record<string, React.ReactNode> = {
  text: <MessageSquare size={14} className="text-blue-500" />,
  image: <Image size={14} className="text-purple-500" />,
  video: <Video size={14} className="text-orange-500" />,
  audio: <Video size={14} className="text-pink-500" />,
  share: <Share2 size={14} className="text-green-500" />,
  ig_reel: <Film size={14} className="text-red-500" />,
  reel: <Film size={14} className="text-red-500" />,
}

const TYPE_LABEL: Record<string, string> = {
  text: 'Text',
  image: 'Image',
  video: 'Video',
  audio: 'Audio',
  share: 'Share',
  ig_reel: 'Reel',
  reel: 'Reel',
}

function formatTime(iso: string) {
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
  return d.toLocaleDateString()
}

function SenderAvatar({ dm, size = 'md' }: { dm: DmEvent; size?: 'sm' | 'md' | 'lg' }) {
  const [imgFailed, setImgFailed] = useState(false)
  const sizeClasses = {
    sm: 'w-7 h-7 text-[10px]',
    md: 'w-10 h-10 text-xs',
    lg: 'w-16 h-16 text-sm',
  }[size]

  if (dm.senderProfilePic && !imgFailed) {
    return (
      <img
        src={dm.senderProfilePic}
        alt={dm.senderUsername || dm.senderId}
        onError={() => setImgFailed(true)}
        className={`${sizeClasses} rounded-full object-cover border border-gray-200 shrink-0 shadow-xs`}
      />
    )
  }

  const label = dm.senderUsername || dm.senderName || dm.senderId
  const colors = ['bg-pink-500', 'bg-purple-500', 'bg-blue-500', 'bg-green-500', 'bg-orange-500', 'bg-red-500', 'bg-indigo-500']
  const idx = label.charCodeAt(label.length - 1) % colors.length
  const initials = (dm.senderName || dm.senderUsername || dm.senderId).slice(0, 2).toUpperCase()

  return (
    <div className={`${sizeClasses} rounded-full ${colors[idx]} flex items-center justify-center text-white font-bold shrink-0 shadow-xs`}>
      {initials}
    </div>
  )
}

function FollowBadge({ isFollowing }: { isFollowing?: boolean | null }) {
  if (isFollowing === true) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-semibold bg-emerald-50 text-emerald-700 border border-emerald-200">
        <CheckCircle2 size={11} className="text-emerald-600" /> Following
      </span>
    )
  }
  if (isFollowing === false) {
    return (
      <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full text-[11px] font-medium bg-amber-50 text-amber-700 border border-amber-200">
        <UserX size={11} className="text-amber-600" /> Not Following
      </span>
    )
  }
  return null
}

export default function DmInboxPage() {
  const [page, setPage] = useState(0)
  const [senderFilter, setSenderFilter] = useState('')
  const [appliedFilter, setAppliedFilter] = useState('')
  const [selectedDm, setSelectedDm] = useState<DmEvent | null>(null)

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const { data, isLoading, isFetching, refetch } = useQuery<any>({
    queryKey: ['dm-inbox', page, appliedFilter],
    queryFn: () => apiClient.getDmInbox(page, 50, appliedFilter || undefined),
    refetchInterval: 12000,
  })

  const events: DmEvent[] = data?.content ?? []
  const totalPages: number = data?.totalPages ?? 0
  const totalElements: number = data?.totalElements ?? 0

  function applyFilter() {
    setAppliedFilter(senderFilter.trim())
    setPage(0)
  }

  function clearFilter() {
    setSenderFilter('')
    setAppliedFilter('')
    setPage(0)
  }

  return (
    <div className="max-w-5xl">
      {/* Header */}
      <div className="flex items-center justify-between mb-6">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <Inbox size={24} className="text-blue-600" />
            DM Inbox
          </h2>
          <p className="text-sm text-gray-500 mt-1">
            Real-time feed of direct messages sent to your connected Instagram account.
          </p>
        </div>
        <button
          onClick={() => refetch()}
          disabled={isFetching}
          className="flex items-center gap-2 px-3.5 py-2 text-sm font-medium border border-gray-200 rounded-lg hover:bg-gray-50 text-gray-700 disabled:opacity-50 transition-colors shadow-2xs"
        >
          <RefreshCw size={14} className={isFetching ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      {/* Filter bar */}
      <div className="flex items-center gap-2 mb-4">
        <div className="relative flex-1 max-w-sm">
          <Search size={15} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            type="text"
            placeholder="Filter by sender ID or username…"
            value={senderFilter}
            onChange={e => setSenderFilter(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && applyFilter()}
            className="w-full pl-9 pr-3 py-2 border border-gray-200 rounded-lg text-sm focus:outline-hidden focus:ring-2 focus:ring-blue-500"
          />
        </div>
        <button
          onClick={applyFilter}
          className="px-3.5 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700 font-medium"
        >
          Filter
        </button>
        {appliedFilter && (
          <button onClick={clearFilter} className="flex items-center gap-1 px-3 py-2 border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50">
            <X size={14} /> Clear
          </button>
        )}
      </div>

      <div className="flex gap-5">
        {/* DM List */}
        <div className="flex-1 min-w-0">
          {isLoading ? (
            <div className="text-center py-16 text-gray-400">Loading DMs…</div>
          ) : events.length === 0 ? (
            <div className="text-center py-16 text-gray-400 bg-white rounded-xl border border-gray-200">
              <Inbox size={48} className="mx-auto mb-3 opacity-30" />
              <p className="font-medium text-gray-700">No DMs yet</p>
              <p className="text-sm mt-1">DMs sent to your Instagram account will appear here automatically.</p>
            </div>
          ) : (
            <div className="space-y-2">
              {events.map(dm => {
                const isSelected = selectedDm?.id === dm.id
                return (
                  <button
                    key={dm.id}
                    onClick={() => setSelectedDm(dm)}
                    className={`w-full text-left bg-white rounded-xl border px-4 py-3.5 flex items-start gap-3.5 transition-all hover:shadow-sm ${
                      isSelected
                        ? 'border-blue-400 ring-1 ring-blue-200'
                        : 'border-gray-200 hover:border-gray-300'
                    }`}
                  >
                    <SenderAvatar dm={dm} size="md" />

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-2">
                        <div className="flex items-center gap-2 min-w-0">
                          {dm.senderUsername ? (
                            <span className="font-semibold text-sm text-gray-900 truncate">
                              @{dm.senderUsername}
                            </span>
                          ) : (
                            <span className="font-mono text-xs text-gray-600 font-medium truncate">
                              {dm.senderId}
                            </span>
                          )}

                          {dm.senderName && (
                            <span className="text-xs text-gray-500 truncate hidden sm:inline">
                              ({dm.senderName})
                            </span>
                          )}

                          <FollowBadge isFollowing={dm.isUserFollowBusiness} />
                        </div>

                        <span className="text-xs text-gray-400 shrink-0">
                          {formatTime(dm.receivedAt)}
                        </span>
                      </div>

                      <div className="flex items-center gap-1.5 mt-1">
                        {TYPE_ICONS[dm.eventType] ?? <MessageSquare size={14} className="text-gray-400" />}
                        <span className="text-xs text-gray-500 font-medium">
                          {TYPE_LABEL[dm.eventType] ?? dm.eventType}
                        </span>
                      </div>

                      {dm.messageText && (
                        <p className="text-sm text-gray-800 mt-1 line-clamp-2 break-words">
                          {dm.messageText}
                        </p>
                      )}

                      {dm.attachmentType && (
                        <span className="inline-block mt-1 text-xs bg-gray-100 text-gray-600 px-2 py-0.5 rounded capitalize">
                          📎 {dm.attachmentType}
                        </span>
                      )}
                    </div>
                  </button>
                )
              })}
            </div>
          )}

          {/* Pagination */}
          {totalPages > 1 && (
            <div className="flex items-center justify-between mt-5">
              <button
                disabled={page === 0}
                onClick={() => setPage(p => p - 1)}
                className="flex items-center gap-1 px-3 py-1.5 border border-gray-200 rounded-lg text-sm disabled:opacity-40"
              >
                <ChevronLeft size={14} /> Newer
              </button>
              <span className="text-xs text-gray-500">
                Page {page + 1} of {totalPages} ({totalElements} total)
              </span>
              <button
                disabled={page >= totalPages - 1}
                onClick={() => setPage(p => p + 1)}
                className="flex items-center gap-1 px-3 py-1.5 border border-gray-200 rounded-lg text-sm disabled:opacity-40"
              >
                Older <ChevronRight size={14} />
              </button>
            </div>
          )}
        </div>

        {/* Detail panel */}
        {selectedDm && (
          <div className="w-84 shrink-0 bg-white rounded-xl border border-gray-200 p-5 self-start sticky top-4 shadow-sm">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-gray-900 text-sm">Message Detail</h3>
              <button onClick={() => setSelectedDm(null)} className="text-gray-400 hover:text-gray-600">
                <X size={16} />
              </button>
            </div>

            {/* User Profile Header */}
            <div className="flex flex-col items-center text-center pb-4 mb-4 border-b border-gray-100">
              <SenderAvatar dm={selectedDm} size="lg" />
              <h4 className="font-semibold text-gray-900 text-base mt-2">
                {selectedDm.senderName || (selectedDm.senderUsername ? `@${selectedDm.senderUsername}` : selectedDm.senderId)}
              </h4>
              {selectedDm.senderUsername && (
                <a
                  href={`https://instagram.com/${selectedDm.senderUsername}`}
                  target="_blank"
                  rel="noopener noreferrer"
                  className="text-xs text-blue-600 hover:underline flex items-center gap-1 mt-0.5"
                >
                  @{selectedDm.senderUsername}
                  <ExternalLink size={11} />
                </a>
              )}
              <div className="mt-2.5">
                <FollowBadge isFollowing={selectedDm.isUserFollowBusiness} />
              </div>
            </div>

            <div className="space-y-3.5 text-sm">
              <div>
                <p className="text-xs text-gray-500 mb-0.5">Sender ID (IGSID)</p>
                <p className="font-mono text-xs bg-gray-50 rounded px-2 py-1 break-all text-gray-700">{selectedDm.senderId}</p>
              </div>

              <div>
                <p className="text-xs text-gray-500 mb-0.5">Type</p>
                <span className="inline-flex items-center gap-1 px-2 py-0.5 rounded-full bg-blue-100 text-blue-700 text-xs font-medium">
                  {TYPE_ICONS[selectedDm.eventType]}
                  {TYPE_LABEL[selectedDm.eventType] ?? selectedDm.eventType}
                </span>
              </div>

              {selectedDm.messageText && (
                <div>
                  <p className="text-xs text-gray-500 mb-0.5">Message Text</p>
                  <div className="bg-blue-50 border border-blue-100 rounded-lg px-3 py-2 text-sm text-gray-800 whitespace-pre-wrap">
                    {selectedDm.messageText}
                  </div>
                </div>
              )}

              {selectedDm.attachmentType && (
                <div>
                  <p className="text-xs text-gray-500 mb-0.5">Attachment</p>
                  <p className="text-xs text-gray-700 capitalize">{selectedDm.attachmentType}</p>
                  {selectedDm.attachmentUrl && (
                    <a
                      href={selectedDm.attachmentUrl}
                      target="_blank"
                      rel="noopener noreferrer"
                      className="text-xs text-blue-600 hover:underline break-all block mt-1"
                    >
                      View media ↗
                    </a>
                  )}
                </div>
              )}

              <div>
                <p className="text-xs text-gray-500 mb-0.5">Received at</p>
                <p className="text-xs text-gray-700">{new Date(selectedDm.receivedAt).toLocaleString()}</p>
              </div>

              <div>
                <p className="text-xs text-gray-500 mb-0.5">Message ID</p>
                <p className="font-mono text-[10px] text-gray-500 break-all">{selectedDm.externalEventId}</p>
              </div>
            </div>
          </div>
        )}
      </div>
    </div>
  )
}
