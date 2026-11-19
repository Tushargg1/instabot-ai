import { useState, useMemo, useRef, useEffect } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import {
  Inbox,
  RefreshCw,
  Search,
  CheckCircle2,
  UserX,
  ExternalLink,
  Send,
  MessageSquare,
  Bot,
  Paperclip,
  CheckCheck,
  Pause,
  Play
} from 'lucide-react'

interface DmEvent {
  id: string
  externalEventId: string
  senderId: string
  recipientId: string
  contactId?: string | null
  outgoing?: boolean | null
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

interface ConversationThread {
  contactId: string
  username: string | null
  name: string | null
  profilePic: string | null
  isFollowing: boolean | null
  lastMessage: DmEvent
  messages: DmEvent[]
}

function formatRelativeTime(iso: string) {
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

function formatMessageTime(iso: string) {
  const d = new Date(iso)
  return d.toLocaleTimeString([], { hour: '2-digit', minute: '2-digit' })
}

function Avatar({
  src,
  label,
  size = 'md'
}: {
  src?: string | null
  label: string
  size?: 'sm' | 'md' | 'lg'
}) {
  const [imgFailed, setImgFailed] = useState(false)
  const sizeClasses = {
    sm: 'w-8 h-8 text-xs',
    md: 'w-11 h-11 text-sm',
    lg: 'w-14 h-14 text-base',
  }[size]

  if (src && !imgFailed) {
    return (
      <img
        src={src}
        alt={label}
        onError={() => setImgFailed(true)}
        className={`${sizeClasses} rounded-full object-cover border border-gray-200 shrink-0 shadow-2xs`}
      />
    )
  }

  const colors = [
    'bg-gradient-to-tr from-pink-500 to-rose-400',
    'bg-gradient-to-tr from-purple-600 to-indigo-400',
    'bg-gradient-to-tr from-blue-600 to-cyan-400',
    'bg-gradient-to-tr from-emerald-600 to-teal-400',
    'bg-gradient-to-tr from-amber-500 to-orange-400',
  ]
  const idx = Math.abs(label.split('').reduce((acc, c) => acc + c.charCodeAt(0), 0)) % colors.length
  const initials = label.replace('@', '').slice(0, 2).toUpperCase()

  return (
    <div className={`${sizeClasses} rounded-full ${colors[idx]} flex items-center justify-center text-white font-bold shrink-0 shadow-2xs`}>
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
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [selectedContactId, setSelectedContactId] = useState<string | null>(null)
  const [replyText, setReplyText] = useState('')
  const messagesEndRef = useRef<HTMLDivElement>(null)

  // Fetch all DMs (up to 100 recent events)
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const { data, isLoading, isFetching, refetch } = useQuery<any>({
    queryKey: ['dm-inbox'],
    queryFn: () => apiClient.getDmInbox(0, 100),
    refetchInterval: 8000, // poll every 8 seconds for new messages
  })

  const rawEvents: DmEvent[] = data?.content ?? []

  // Group events by contact into conversations
  const conversations: ConversationThread[] = useMemo(() => {
    const map = new Map<string, ConversationThread>()
    const MY_ACCOUNT_ID = '17841431674180722'

    for (const dm of rawEvents) {
      const isOutgoing = dm.outgoing !== null && dm.outgoing !== undefined
        ? dm.outgoing
        : dm.senderId === MY_ACCOUNT_ID

      // Determine contact ID
      const contact = dm.contactId || (isOutgoing ? dm.recipientId : dm.senderId)
      if (!contact || contact === MY_ACCOUNT_ID) continue

      let thread = map.get(contact)
      if (!thread) {
        thread = {
          contactId: contact,
          username: dm.senderUsername || null,
          name: dm.senderName || null,
          profilePic: dm.senderProfilePic || null,
          isFollowing: dm.isUserFollowBusiness ?? null,
          lastMessage: { ...dm, outgoing: isOutgoing, contactId: contact },
          messages: [],
        }
        map.set(contact, thread)
      }

      // Update contact metadata if available
      if (dm.senderUsername && !thread.username) thread.username = dm.senderUsername
      if (dm.senderName && !thread.name) thread.name = dm.senderName
      if (dm.senderProfilePic && !thread.profilePic) thread.profilePic = dm.senderProfilePic
      if (dm.isUserFollowBusiness !== null && dm.isUserFollowBusiness !== undefined) {
        thread.isFollowing = dm.isUserFollowBusiness
      }

      thread.messages.push({ ...dm, outgoing: isOutgoing, contactId: contact })
    }

    // Sort each thread's messages chronologically (oldest to newest)
    for (const thread of map.values()) {
      thread.messages.sort(
        (a, b) => new Date(a.receivedAt).getTime() - new Date(b.receivedAt).getTime()
      )
      // latest message is the last one in chronological array
      thread.lastMessage = thread.messages[thread.messages.length - 1]
    }

    // Sort conversations by latest message timestamp (newest conversation on top)
    return Array.from(map.values()).sort(
      (a, b) => new Date(b.lastMessage.receivedAt).getTime() - new Date(a.lastMessage.receivedAt).getTime()
    )
  }, [rawEvents])

  // Filter conversations by search input
  const filteredConversations = useMemo(() => {
    if (!search.trim()) return conversations
    const q = search.toLowerCase()
    return conversations.filter(c =>
      (c.username && c.username.toLowerCase().includes(q)) ||
      (c.name && c.name.toLowerCase().includes(q)) ||
      c.contactId.toLowerCase().includes(q) ||
      c.messages.some(m => m.messageText && m.messageText.toLowerCase().includes(q))
    )
  }, [conversations, search])

  // Default selection to first conversation if none selected
  useEffect(() => {
    if (!selectedContactId && conversations.length > 0) {
      setSelectedContactId(conversations[0].contactId)
    }
  }, [conversations, selectedContactId])

  // Current active conversation
  const activeConversation = useMemo(() => {
    return conversations.find(c => c.contactId === selectedContactId) || null
  }, [conversations, selectedContactId])

  // Auto-scroll chat to bottom when conversation or message count changes
  useEffect(() => {
    messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' })
  }, [activeConversation?.messages.length, selectedContactId])

  // Paused Users Query
  const { data: pausedUserList = [] } = useQuery<string[]>({
    queryKey: ['paused-users'],
    queryFn: () => apiClient.getPausedUsers(),
  })

  const pausedSet = useMemo(
    () => new Set((pausedUserList || []).map((u) => u.toLowerCase())),
    [pausedUserList]
  )

  const isUserPaused = (contactId: string, username?: string | null) => {
    if (pausedSet.has(contactId.toLowerCase())) return true
    if (username && pausedSet.has(username.toLowerCase().replace('@', ''))) return true
    return false
  }

  // Pause & Resume Mutations
  const pauseMutation = useMutation({
    mutationFn: (data: { userIdentifier: string; username?: string; senderId?: string; reason?: string }) =>
      apiClient.pauseUserAutoReply(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['paused-users'] })
      queryClient.invalidateQueries({ queryKey: ['marketing-leads'] })
    },
  })

  const resumeMutation = useMutation({
    mutationFn: (userIdentifier: string) => apiClient.resumeUserAutoReply(userIdentifier),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['paused-users'] })
      queryClient.invalidateQueries({ queryKey: ['marketing-leads'] })
    },
  })

  // Send manual reply mutation
  const sendMutation = useMutation({
    mutationFn: ({ recipientId, text }: { recipientId: string; text: string }) =>
      apiClient.sendDmMessage(recipientId, text),
    onSuccess: () => {
      setReplyText('')
      queryClient.invalidateQueries({ queryKey: ['dm-inbox'] })
    },
  })

  function handleSend() {
    if (!replyText.trim() || !activeConversation || sendMutation.isPending) return
    sendMutation.mutate({
      recipientId: activeConversation.contactId,
      text: replyText.trim(),
    })
  }

  return (
    <div className="h-[calc(100vh-6rem)] flex flex-col">
      {/* Top Header */}
      <div className="flex items-center justify-between pb-4 border-b border-gray-200 shrink-0">
        <div>
          <h2 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <Inbox size={24} className="text-blue-600" />
            DM Inbox
          </h2>
          <p className="text-xs text-gray-500 mt-0.5">
            {conversations.length} active conversation{conversations.length === 1 ? '' : 's'} · Real-time Instagram DMs
          </p>
        </div>

        <button
          onClick={() => refetch()}
          disabled={isFetching}
          className="flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium border border-gray-200 rounded-lg hover:bg-gray-50 text-gray-700 disabled:opacity-50 transition-colors shadow-2xs"
        >
          <RefreshCw size={13} className={isFetching ? 'animate-spin' : ''} />
          Refresh
        </button>
      </div>

      {/* Main 2-Panel Messenger Interface */}
      <div className="flex-1 min-h-0 flex mt-4 bg-white rounded-2xl border border-gray-200 shadow-xs overflow-hidden">
        {/* LEFT COLUMN: Conversation Rows (1 per user) */}
        <div className="w-80 md:w-96 flex flex-col border-r border-gray-200 bg-gray-50/50 shrink-0">
          {/* Search box */}
          <div className="p-3 border-b border-gray-200 bg-white">
            <div className="relative">
              <Search size={14} className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" />
              <input
                type="text"
                placeholder="Search conversations…"
                value={search}
                onChange={e => setSearch(e.target.value)}
                className="w-full pl-8 pr-3 py-1.5 bg-gray-100 rounded-lg text-xs text-gray-800 placeholder-gray-400 focus:outline-hidden focus:bg-white focus:ring-2 focus:ring-blue-500"
              />
            </div>
          </div>

          {/* Conversations List */}
          <div className="flex-1 overflow-y-auto divide-y divide-gray-100">
            {isLoading ? (
              <div className="p-8 text-center text-xs text-gray-400">Loading conversations…</div>
            ) : filteredConversations.length === 0 ? (
              <div className="p-8 text-center text-gray-400">
                <MessageSquare size={32} className="mx-auto mb-2 opacity-30" />
                <p className="text-xs font-medium text-gray-600">No conversations found</p>
                <p className="text-[11px] text-gray-400 mt-1">DMs sent to your Instagram will show here as 1 row per user.</p>
              </div>
            ) : (
              filteredConversations.map(conv => {
                const isSelected = conv.contactId === selectedContactId
                const displayName = conv.name || (conv.username ? `@${conv.username}` : conv.contactId)
                const isOutgoingLast = conv.lastMessage.outgoing
                const isConvPaused = isUserPaused(conv.contactId, conv.username)

                return (
                  <button
                    key={conv.contactId}
                    onClick={() => setSelectedContactId(conv.contactId)}
                    className={`w-full text-left p-3.5 flex items-start gap-3 transition-colors ${
                      isSelected
                        ? 'bg-blue-50/80 border-r-3 border-blue-600'
                        : 'hover:bg-gray-100/70 bg-white'
                    }`}
                  >
                    <Avatar
                      src={conv.profilePic}
                      label={conv.username || conv.contactId}
                      size="md"
                    />

                    <div className="flex-1 min-w-0">
                      <div className="flex items-center justify-between gap-1 mb-0.5">
                        <div className="flex items-center gap-1.5 min-w-0">
                          <span className={`text-xs font-semibold truncate ${isSelected ? 'text-blue-900' : 'text-gray-900'}`}>
                            {conv.username ? `@${conv.username}` : displayName}
                          </span>
                        </div>
                        <div className="flex items-center gap-1 shrink-0">
                          {isConvPaused && (
                            <span className="text-[10px] font-bold text-amber-800 bg-amber-100 border border-amber-200 px-1.5 py-0.2 rounded">
                              ⏸️ Paused
                            </span>
                          )}
                          <span className="text-[10px] text-gray-400">
                            {formatRelativeTime(conv.lastMessage.receivedAt)}
                          </span>
                        </div>
                      </div>

                      <div className="flex items-center gap-1.5 mb-1">
                        {conv.name && (
                          <span className="text-[11px] text-gray-500 truncate">
                            {conv.name}
                          </span>
                        )}
                        <FollowBadge isFollowing={conv.isFollowing} />
                      </div>

                      <p className="text-xs text-gray-500 truncate">
                        {isOutgoingLast ? (
                          <span className="text-blue-600 font-medium">You: </span>
                        ) : null}
                        {conv.lastMessage.messageText || `[${conv.lastMessage.eventType}]`}
                      </p>
                    </div>
                  </button>
                )
              })
            )}
          </div>
        </div>

        {/* RIGHT COLUMN: Full Chat Conversation */}
        <div className="flex-1 flex flex-col min-w-0 bg-white">
          {activeConversation ? (
            <>
              {/* Chat Header */}
              <div className="px-5 py-3.5 border-b border-gray-200 flex items-center justify-between bg-white shrink-0">
                <div className="flex items-center gap-3 min-w-0">
                  <Avatar
                    src={activeConversation.profilePic}
                    label={activeConversation.username || activeConversation.contactId}
                    size="md"
                  />
                  <div className="min-w-0">
                    <div className="flex items-center gap-2">
                      <h3 className="font-bold text-sm text-gray-900 truncate">
                        {activeConversation.name || (activeConversation.username ? `@${activeConversation.username}` : activeConversation.contactId)}
                      </h3>
                      <FollowBadge isFollowing={activeConversation.isFollowing} />
                    </div>

                    <div className="flex items-center gap-3 text-xs text-gray-500 mt-0.5">
                      {activeConversation.username && (
                        <a
                          href={`https://instagram.com/${activeConversation.username}`}
                          target="_blank"
                          rel="noopener noreferrer"
                          className="text-blue-600 hover:underline flex items-center gap-1 text-[11px]"
                        >
                          @{activeConversation.username}
                          <ExternalLink size={10} />
                        </a>
                      )}
                      <span className="font-mono text-[10px] text-gray-400">
                        IGSID: {activeConversation.contactId}
                      </span>
                    </div>
                  </div>
                </div>

                <div className="flex items-center gap-3 shrink-0">
                  {isUserPaused(activeConversation.contactId, activeConversation.username) ? (
                    <button
                      onClick={() =>
                        resumeMutation.mutate(
                          activeConversation.username || activeConversation.contactId
                        )
                      }
                      disabled={resumeMutation.isPending}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-bold text-white bg-amber-500 rounded-lg hover:bg-amber-600 transition-colors shadow-2xs"
                      title="Automatic replies are currently paused for this user. Click to resume."
                    >
                      <Play className="w-3.5 h-3.5" />
                      {resumeMutation.isPending ? 'Resuming...' : 'Resume Auto-Reply'}
                    </button>
                  ) : (
                    <button
                      onClick={() =>
                        pauseMutation.mutate({
                          userIdentifier:
                            activeConversation.username || activeConversation.contactId,
                          username: activeConversation.username || undefined,
                          senderId: activeConversation.contactId,
                          reason: 'Manual pause from DM Inbox header',
                        })
                      }
                      disabled={pauseMutation.isPending}
                      className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-semibold text-gray-700 bg-gray-100 hover:bg-amber-50 hover:text-amber-900 border border-gray-200 rounded-lg transition-colors shadow-2xs"
                      title="Click to pause all automated replies for this user"
                    >
                      <Pause className="w-3.5 h-3.5 text-amber-600" />
                      {pauseMutation.isPending ? 'Pausing...' : 'Pause Auto-Reply'}
                    </button>
                  )}

                  <div className="text-right text-xs text-gray-400">
                    {activeConversation.messages.length} message{activeConversation.messages.length === 1 ? '' : 's'}
                  </div>
                </div>
              </div>

              {/* Paused Banner Notification */}
              {isUserPaused(activeConversation.contactId, activeConversation.username) && (
                <div className="bg-amber-50 border-b border-amber-200 px-5 py-2 flex items-center justify-between text-xs text-amber-900 font-medium shrink-0">
                  <div className="flex items-center gap-2">
                    <Pause className="w-4 h-4 text-amber-600 shrink-0" />
                    <span>
                      Chat auto-reply is currently <strong>PAUSED</strong> for this user. No automatic replies or bot actions will be triggered.
                    </span>
                  </div>
                  <button
                    onClick={() =>
                      resumeMutation.mutate(
                        activeConversation.username || activeConversation.contactId
                      )
                    }
                    className="font-bold text-amber-900 underline hover:no-underline ml-3 shrink-0"
                  >
                    Resume
                  </button>
                </div>
              )}

              {/* Chat Messages Body */}
              <div className="flex-1 overflow-y-auto p-5 space-y-3 bg-gradient-to-b from-gray-50/40 to-white">
                {activeConversation.messages.map(msg => {
                  const isOutgoing = msg.outgoing

                  return (
                    <div
                      key={msg.id}
                      className={`flex gap-2.5 max-w-[80%] ${
                        isOutgoing ? 'ml-auto flex-row-reverse' : 'mr-auto'
                      }`}
                    >
                      {/* Avatar next to bubble */}
                      {isOutgoing ? (
                        <div className="w-7 h-7 rounded-full bg-blue-600 flex items-center justify-center text-white shrink-0 self-end shadow-2xs">
                          <Bot size={14} />
                        </div>
                      ) : (
                        <Avatar
                          src={activeConversation.profilePic}
                          label={activeConversation.username || activeConversation.contactId}
                          size="sm"
                        />
                      )}

                      <div>
                        {/* Bubble */}
                        <div
                          className={`rounded-2xl px-4 py-2.5 text-xs sm:text-sm shadow-2xs whitespace-pre-wrap break-words leading-relaxed ${
                            isOutgoing
                              ? 'bg-blue-600 text-white rounded-br-xs'
                              : 'bg-white border border-gray-200 text-gray-900 rounded-bl-xs'
                          }`}
                        >
                          {msg.messageText || <span className="italic opacity-60">Attachment: {msg.eventType}</span>}

                          {msg.attachmentUrl && (
                            <div className="mt-2 pt-2 border-t border-white/20">
                              <a
                                href={msg.attachmentUrl}
                                target="_blank"
                                rel="noopener noreferrer"
                                className={`inline-flex items-center gap-1 text-xs underline font-medium ${
                                  isOutgoing ? 'text-white' : 'text-blue-600'
                                }`}
                              >
                                <Paperclip size={12} /> View Media
                              </a>
                            </div>
                          )}
                        </div>

                        {/* Timestamp & status */}
                        <div
                          className={`flex items-center gap-1 mt-1 text-[10px] text-gray-400 ${
                            isOutgoing ? 'justify-end' : 'justify-start'
                          }`}
                        >
                          <span>{formatMessageTime(msg.receivedAt)}</span>
                          {isOutgoing && <CheckCheck size={12} className="text-blue-500" />}
                        </div>
                      </div>
                    </div>
                  )
                })}
                <div ref={messagesEndRef} />
              </div>

              {/* Chat Input Bar */}
              <div className="p-3 border-t border-gray-200 bg-white shrink-0">
                <form
                  onSubmit={e => {
                    e.preventDefault()
                    handleSend()
                  }}
                  className="flex items-center gap-2"
                >
                  <input
                    type="text"
                    placeholder={`Reply to ${activeConversation.username ? '@' + activeConversation.username : 'user'}…`}
                    value={replyText}
                    onChange={e => setReplyText(e.target.value)}
                    disabled={sendMutation.isPending}
                    className="flex-1 px-4 py-2 bg-gray-50 border border-gray-200 rounded-xl text-xs sm:text-sm text-gray-900 placeholder-gray-400 focus:outline-hidden focus:bg-white focus:ring-2 focus:ring-blue-500 focus:border-transparent"
                  />
                  <button
                    type="submit"
                    disabled={!replyText.trim() || sendMutation.isPending}
                    className="px-4 py-2 bg-blue-600 text-white rounded-xl text-xs sm:text-sm font-medium hover:bg-blue-700 disabled:opacity-40 disabled:hover:bg-blue-600 transition-colors flex items-center gap-1.5 shrink-0 shadow-2xs"
                  >
                    <Send size={14} className={sendMutation.isPending ? 'animate-pulse' : ''} />
                    Send
                  </button>
                </form>
              </div>
            </>
          ) : (
            <div className="flex-1 flex flex-col items-center justify-center p-8 text-center text-gray-400">
              <MessageSquare size={48} className="opacity-20 mb-3" />
              <p className="text-sm font-medium text-gray-600">Select a conversation</p>
              <p className="text-xs text-gray-400 mt-1 max-w-xs">
                Choose a user from the list on the left to see their full chronological chat history and reply directly.
              </p>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
