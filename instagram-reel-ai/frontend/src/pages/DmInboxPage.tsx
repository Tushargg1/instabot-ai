import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { Inbox, RefreshCw, ChevronLeft, ChevronRight, Image, Video, Share2, Film, MessageSquare, Search, X } from 'lucide-react'

interface DmEvent {
  id: string
  externalEventId: string
  senderId: string
  recipientId: string
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

function SenderAvatar({ senderId }: { senderId: string }) {
  // Generate a consistent color from the sender ID
  const colors = ['bg-pink-500', 'bg-purple-500', 'bg-blue-500', 'bg-green-500', 'bg-orange-500', 'bg-red-500', 'bg-indigo-500']
  const idx = senderId.charCodeAt(senderId.length - 1) % colors.length
  const initials = senderId.slice(-2).toUpperCase()
  return (
    <div className={`w-9 h-9 rounded-full ${colors[idx]} flex items-center justify-center text-white text-xs font-bold shrink-0`}>
      {initials}
    </div>
  )
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
    refetchInterval: 15000, // auto-refresh every 15 seconds
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
            All incoming Instagram DMs received via webhook ·{' '}
            <span className="font-medium text-gray-700">{totalElements} total</span>
            {isFetching && <span className="ml-2 text-blue-500 animate-pulse">· refreshing…</span>}
          </p>
        </div>
        <button
          onClick={() => refetch()}
          className="flex items-center gap-2 px-3 py-2 border border-gray-200 rounded-lg text-sm text-gray-600 hover:bg-gray-50"
        >
          <RefreshCw size={14} className={isFetching ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      {/* Filter bar */}
      <div className="flex gap-2 mb-5">
        <div className="relative flex-1 max-w-xs">
          <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
          <input
            value={senderFilter}
            onChange={e => setSenderFilter(e.target.value)}
            onKeyDown={e => e.key === 'Enter' && applyFilter()}
            placeholder="Filter by sender ID…"
            className="w-full pl-8 pr-3 py-2 border border-gray-300 rounded-lg text-sm"
          />
        </div>
        <button
          onClick={applyFilter}
          className="px-3 py-2 bg-blue-600 text-white rounded-lg text-sm hover:bg-blue-700"
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
            <div className="text-center py-16 text-gray-400">
              <Inbox size={48} className="mx-auto mb-3 opacity-30" />
              <p className="font-medium">No DMs yet</p>
              <p className="text-sm mt-1">DMs sent to your Instagram will appear here automatically.</p>
            </div>
          ) : (
            <div className="space-y-2">
              {events.map(dm => (
                <button
                  key={dm.id}
                  onClick={() => setSelectedDm(dm)}
                  className={`w-full text-left bg-white rounded-xl border px-4 py-3 flex items-start gap-3 transition-all hover:shadow-sm ${
                    selectedDm?.id === dm.id
                      ? 'border-blue-400 ring-1 ring-blue-200'
                      : 'border-gray-200 hover:border-gray-300'
                  }`}
                >
                  <SenderAvatar senderId={dm.senderId} />

                  <div className="flex-1 min-w-0">
                    <div className="flex items-center justify-between gap-2">
                      <span className="font-mono text-xs text-gray-600 font-medium truncate">
                        {dm.senderId}
                      </span>
                      <span className="text-xs text-gray-400 shrink-0">
                        {formatTime(dm.receivedAt)}
                      </span>
                    </div>
                    <div className="flex items-center gap-1.5 mt-0.5">
                      {TYPE_ICONS[dm.eventType] ?? <MessageSquare size={14} className="text-gray-400" />}
                      <span className="text-xs text-gray-500">
                        {TYPE_LABEL[dm.eventType] ?? dm.eventType}
                      </span>
                      {dm.messageText && (
                        <span className="text-xs text-gray-700 truncate ml-1">
                          — {dm.messageText}
                        </span>
                      )}
                    </div>
                  </div>
                </button>
              ))}
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
                Page {page + 1} of {totalPages}
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
          <div className="w-80 shrink-0 bg-white rounded-xl border border-gray-200 p-5 self-start sticky top-0">
            <div className="flex items-center justify-between mb-4">
              <h3 className="font-semibold text-gray-900 text-sm">Message Detail</h3>
              <button onClick={() => setSelectedDm(null)} className="text-gray-400 hover:text-gray-600">
                <X size={16} />
              </button>
            </div>

            <div className="flex justify-center mb-4">
              <SenderAvatar senderId={selectedDm.senderId} />
            </div>

            <div className="space-y-3 text-sm">
              <div>
                <p className="text-xs text-gray-500 mb-0.5">Sender ID</p>
                <p className="font-mono text-xs bg-gray-50 rounded px-2 py-1 break-all">{selectedDm.senderId}</p>
              </div>
              <div>
                <p className="text-xs text-gray-500 mb-0.5">Recipient ID (your account)</p>
                <p className="font-mono text-xs bg-gray-50 rounded px-2 py-1 break-all">{selectedDm.recipientId}</p>
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
                      className="text-xs text-blue-600 hover:underline break-all"
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
