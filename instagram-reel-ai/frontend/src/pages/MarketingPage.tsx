import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import {
  Megaphone,
  Plus,
  Search,
  Sparkles,
  Send,
  MessageSquare,
  CheckCircle2,
  TrendingUp,
  Download,
  ExternalLink,
  RefreshCw,
  Copy,
  Check,
  Bot,
  Tag,
  Target,
  X,
  Play
} from 'lucide-react'

interface ChatMessage {
  sender: 'USER' | 'PROSPECT' | 'BOT'
  text: string
  timestamp: string
}

interface MarketingLead {
  id: string
  instagramUsername: string
  instagramSenderId: string | null
  contactName: string | null
  avatarUrl: string | null
  context: string | null
  tag: string
  productOffer: string
  salesGoal: string
  firstMessage: string | null
  outreachSentAt: string | null
  lastInteractionAt: string | null
  status: 'PENDING' | 'OUTREACHED' | 'IN_CONVERSATION' | 'CONVERTED' | 'CLOSED'
  conversationHistory: string
  notes: string | null
  createdAt: string
}

interface MarketingStats {
  totalLeads: number
  pending: number
  outreached: number
  inConversation: number
  converted: number
  closed: number
  conversionRate: number
}

interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
}

function formatRelativeTime(iso: string | null) {
  if (!iso) return 'Not yet'
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

export default function MarketingPage() {
  const queryClient = useQueryClient()
  const [search, setSearch] = useState('')
  const [selectedStatus, setSelectedStatus] = useState<string>('')
  const [selectedTag, setSelectedTag] = useState<string>('')
  const [page, setPage] = useState(0)

  // Modals & Panels
  const [isAddModalOpen, setIsAddModalOpen] = useState(false)
  const [activeLeadForChat, setActiveLeadForChat] = useState<MarketingLead | null>(null)
  const [copiedId, setCopiedId] = useState<string | null>(null)

  // Simulation test input
  const [simulatedText, setSimulatedText] = useState('')

  // Form State for New Lead
  const [formData, setFormData] = useState({
    instagramUsername: '',
    contactName: '',
    context: '',
    tag: 'Video Agency',
    productOffer: '',
    salesGoal: 'Drive to buy / book demo call',
    firstMessage: '',
    notes: '',
  })
  const [isGeneratingFirstMsg, setIsGeneratingFirstMsg] = useState(false)

  // Fetch Stats
  const { data: stats } = useQuery<MarketingStats>({
    queryKey: ['marketing-stats'],
    queryFn: () => apiClient.getMarketingStats(),
  })

  // Fetch Tags
  const { data: tags = [] } = useQuery<string[]>({
    queryKey: ['marketing-tags'],
    queryFn: () => apiClient.getMarketingTags(),
  })

  // Fetch Leads
  const { data: leadsPage, isLoading, refetch, isFetching } = useQuery<PageResponse<MarketingLead>>({
    queryKey: ['marketing-leads', search, selectedStatus, selectedTag, page],
    queryFn: () => apiClient.getMarketingLeads(search, selectedStatus, selectedTag, page, 20),
  })

  const leads = leadsPage?.content || []

  // Create Lead Mutation
  const createMutation = useMutation({
    mutationFn: (data: typeof formData) => apiClient.createMarketingLead(data),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['marketing-leads'] })
      queryClient.invalidateQueries({ queryKey: ['marketing-stats'] })
      queryClient.invalidateQueries({ queryKey: ['marketing-tags'] })
      setIsAddModalOpen(false)
      setFormData({
        instagramUsername: '',
        contactName: '',
        context: '',
        tag: 'Video Agency',
        productOffer: '',
        salesGoal: 'Drive to buy / book demo call',
        firstMessage: '',
        notes: '',
      })
    },
  })

  // Send Outreach Mutation
  const outreachMutation = useMutation({
    mutationFn: (id: string) => apiClient.sendMarketingOutreach(id),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['marketing-leads'] })
      queryClient.invalidateQueries({ queryKey: ['marketing-stats'] })
      if (activeLeadForChat && activeLeadForChat.id === updated.id) {
        setActiveLeadForChat(updated)
      }
    },
  })

  // Update Status Mutation
  const updateStatusMutation = useMutation({
    mutationFn: ({ id, status }: { id: string; status: string }) =>
      apiClient.updateMarketingLead(id, { status }),
    onSuccess: (updated) => {
      queryClient.invalidateQueries({ queryKey: ['marketing-leads'] })
      queryClient.invalidateQueries({ queryKey: ['marketing-stats'] })
      if (activeLeadForChat && activeLeadForChat.id === updated.id) {
        setActiveLeadForChat(updated)
      }
    },
  })

  // Simulate Sales Bot Mutation
  const simulateMutation = useMutation({
    mutationFn: ({ id, message }: { id: string; message: string }) =>
      apiClient.simulateMarketingChat(id, message),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['marketing-leads'] })
      // Refetch the active lead's details
      if (activeLeadForChat) {
        apiClient.getMarketingLead(activeLeadForChat.id).then((fresh) => {
          setActiveLeadForChat(fresh)
        })
      }
      setSimulatedText('')
    },
  })

  // AI Icebreaker Generator
  const handleGenerateIcebreaker = async () => {
    if (!formData.instagramUsername && !formData.context && !formData.productOffer) {
      alert('Please enter at least context and the product offer first!')
      return
    }
    setIsGeneratingFirstMsg(true)
    try {
      const res = await apiClient.generateFirstMessage(formData)
      if (res?.message) {
        setFormData((prev) => ({ ...prev, firstMessage: res.message }))
      }
    } catch (e) {
      console.error(e)
    } finally {
      setIsGeneratingFirstMsg(false)
    }
  }

  const handleCopyIcebreaker = (lead: MarketingLead) => {
    const text = lead.firstMessage || ''
    navigator.clipboard.writeText(text)
    setCopiedId(lead.id)
    setTimeout(() => setCopiedId(null), 2000)
  }

  const parseHistory = (json: string): ChatMessage[] => {
    try {
      return JSON.parse(json || '[]')
    } catch {
      return []
    }
  }

  // Export CSV
  const handleExportCsv = () => {
    if (!leads.length) return
    const headers = ['Username', 'Contact Name', 'Tag', 'Status', 'Product Offer', 'Context', 'Outreached At', 'Last Active']
    const rows = leads.map((l) => [
      `"${l.instagramUsername}"`,
      `"${l.contactName || ''}"`,
      `"${l.tag}"`,
      `"${l.status}"`,
      `"${(l.productOffer || '').replace(/"/g, '""')}"`,
      `"${(l.context || '').replace(/"/g, '""')}"`,
      `"${l.outreachSentAt || ''}"`,
      `"${l.lastInteractionAt || ''}"`,
    ])
    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map((r) => r.join(','))].join('\n')
    const encodedUri = encodeURI(csvContent)
    const link = document.createElement('a')
    link.setAttribute('href', encodedUri)
    link.setAttribute('download', `marketing_leads_${new Date().toISOString().slice(0, 10)}.csv`)
    document.body.appendChild(link)
    link.click()
    document.body.removeChild(link)
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex flex-col sm:flex-row sm:items-center sm:justify-between gap-4">
        <div>
          <h1 className="text-2xl font-bold text-gray-900 flex items-center gap-2">
            <Megaphone className="w-7 h-7 text-indigo-600" />
            Marketing & AI Sales Outreach
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            Provide prospect Instagram data with context & tags. You send the 1st text; our AI bot handles all replies to convert the lead!
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={handleExportCsv}
            disabled={leads.length === 0}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 transition-colors shadow-xs disabled:opacity-50"
          >
            <Download className="w-4 h-4" />
            Export CSV
          </button>
          <button
            onClick={() => setIsAddModalOpen(true)}
            className="inline-flex items-center gap-2 px-4 py-2 text-sm font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 transition-colors shadow-xs"
          >
            <Plus className="w-4 h-4" />
            Add Target Lead
          </button>
        </div>
      </div>

      {/* KPI Cards */}
      <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-5 gap-4">
        <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase">Target Leads</span>
            <Target className="w-4 h-4 text-indigo-500" />
          </div>
          <p className="text-2xl font-extrabold text-gray-900 mt-1">{stats?.totalLeads ?? 0}</p>
          <span className="text-xs text-gray-400">{stats?.pending ?? 0} pending outreach</span>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase">Outreached</span>
            <Send className="w-4 h-4 text-blue-500" />
          </div>
          <p className="text-2xl font-extrabold text-blue-600 mt-1">{stats?.outreached ?? 0}</p>
          <span className="text-xs text-gray-400">1st text delivered</span>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase">AI Selling</span>
            <Bot className="w-4 h-4 text-purple-500" />
          </div>
          <p className="text-2xl font-extrabold text-purple-600 mt-1">{stats?.inConversation ?? 0}</p>
          <span className="text-xs text-gray-400">In live conversation</span>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase">Converted 🎉</span>
            <CheckCircle2 className="w-4 h-4 text-emerald-500" />
          </div>
          <p className="text-2xl font-extrabold text-emerald-600 mt-1">{stats?.converted ?? 0}</p>
          <span className="text-xs text-gray-400">Sales / Calls closed</span>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-xs">
          <div className="flex items-center justify-between">
            <span className="text-xs font-semibold text-gray-500 uppercase">Win Rate</span>
            <TrendingUp className="w-4 h-4 text-emerald-500" />
          </div>
          <p className="text-2xl font-extrabold text-gray-900 mt-1">{stats?.conversionRate ?? 0}%</p>
          <span className="text-xs text-emerald-600 font-medium">Lead to customer</span>
        </div>
      </div>

      {/* Filter and Search Bar */}
      <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-xs flex flex-col md:flex-row md:items-center justify-between gap-3">
        {/* Status Tabs */}
        <div className="flex items-center gap-1.5 overflow-x-auto pb-1 md:pb-0 text-xs font-medium">
          {[
            { label: 'All Leads', value: '' },
            { label: 'Pending', value: 'PENDING' },
            { label: 'Outreached', value: 'OUTREACHED' },
            { label: 'In Conversation (AI)', value: 'IN_CONVERSATION' },
            { label: 'Converted 🎉', value: 'CONVERTED' },
          ].map((tab) => (
            <button
              key={tab.value}
              onClick={() => {
                setSelectedStatus(tab.value)
                setPage(0)
              }}
              className={`px-3 py-1.5 rounded-lg transition-colors whitespace-nowrap ${
                selectedStatus === tab.value
                  ? 'bg-indigo-600 text-white shadow-2xs font-semibold'
                  : 'bg-gray-100 text-gray-600 hover:bg-gray-200'
              }`}
            >
              {tab.label}
            </button>
          ))}
        </div>

        {/* Search & Tag Select */}
        <div className="flex items-center gap-2">
          {tags.length > 0 && (
            <select
              value={selectedTag}
              onChange={(e) => {
                setSelectedTag(e.target.value)
                setPage(0)
              }}
              aria-label="Filter by Tag"
              className="px-3 py-2 text-xs border border-gray-300 rounded-lg bg-white text-gray-700 focus:outline-hidden focus:ring-2 focus:ring-indigo-500"
            >
              <option value="">All Tags</option>
              {tags.map((t) => (
                <option key={t} value={t}>
                  {t}
                </option>
              ))}
            </select>
          )}

          <div className="relative min-w-[200px]">
            <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-3.5 h-3.5 text-gray-400" />
            <input
              type="text"
              value={search}
              onChange={(e) => {
                setSearch(e.target.value)
                setPage(0)
              }}
              placeholder="Search leads, bio, offer..."
              className="w-full pl-8 pr-3 py-1.5 text-xs border border-gray-300 rounded-lg focus:outline-hidden focus:ring-2 focus:ring-indigo-500"
            />
          </div>

          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="p-2 border border-gray-300 rounded-lg hover:bg-gray-50 text-gray-500 disabled:opacity-50"
            title="Refresh"
          >
            <RefreshCw className={`w-3.5 h-3.5 ${isFetching ? 'animate-spin' : ''}`} />
          </button>
        </div>
      </div>

      {/* Leads Table */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-xs overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center items-center py-20 text-gray-400">
            <RefreshCw className="w-8 h-8 animate-spin" />
          </div>
        ) : leads.length === 0 ? (
          <div className="text-center py-16 px-4">
            <Megaphone className="w-12 h-12 text-gray-300 mx-auto mb-3" />
            <h3 className="text-base font-semibold text-gray-800">No marketing leads found</h3>
            <p className="text-xs text-gray-500 max-w-sm mx-auto mt-1">
              Add your target Instagram accounts with context and product offer to launch AI sales outreach!
            </p>
            <button
              onClick={() => setIsAddModalOpen(true)}
              className="mt-4 inline-flex items-center gap-1.5 px-3.5 py-2 text-xs font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700"
            >
              <Plus className="w-3.5 h-3.5" />
              Add First Lead
            </button>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 text-left text-sm">
              <thead className="bg-gray-50 text-gray-600 font-semibold text-xs uppercase tracking-wider">
                <tr>
                  <th className="px-5 py-3.5">Instagram Target</th>
                  <th className="px-5 py-3.5">Tag & Context</th>
                  <th className="px-5 py-3.5">Product / Offer</th>
                  <th className="px-5 py-3.5">Status</th>
                  <th className="px-5 py-3.5 text-right">Actions</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 bg-white">
                {leads.map((lead) => {
                  const history = parseHistory(lead.conversationHistory)
                  return (
                    <tr key={lead.id} className="hover:bg-gray-50 transition-colors">
                      {/* Instagram Profile */}
                      <td className="px-5 py-3.5 whitespace-nowrap">
                        <div className="flex items-center gap-3">
                          {lead.avatarUrl ? (
                            <img
                              src={lead.avatarUrl}
                              alt={lead.instagramUsername}
                              className="w-10 h-10 rounded-full object-cover border border-gray-200 shrink-0"
                            />
                          ) : (
                            <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-pink-500 to-rose-400 text-white font-bold flex items-center justify-center text-xs shrink-0">
                              {lead.instagramUsername.slice(0, 2).toUpperCase()}
                            </div>
                          )}
                          <div>
                            <div className="font-semibold text-gray-900 text-sm flex items-center gap-1.5">
                              {lead.contactName || `@${lead.instagramUsername}`}
                              <a
                                href={`https://instagram.com/${lead.instagramUsername}`}
                                target="_blank"
                                rel="noreferrer"
                                className="text-gray-400 hover:text-indigo-600"
                                title="Open Instagram Profile"
                              >
                                <ExternalLink className="w-3.5 h-3.5" />
                              </a>
                            </div>
                            <div className="text-xs text-gray-500">
                              @{lead.instagramUsername}
                            </div>
                          </div>
                        </div>
                      </td>

                      {/* Tag & Context */}
                      <td className="px-5 py-3.5 max-w-xs">
                        <div className="flex items-center gap-1.5 mb-1">
                          <span className="inline-flex items-center gap-1 text-[11px] font-semibold px-2 py-0.5 rounded-md bg-indigo-50 text-indigo-700 border border-indigo-100">
                            <Tag className="w-3 h-3" />
                            {lead.tag}
                          </span>
                        </div>
                        <p className="text-xs text-gray-600 line-clamp-2" title={lead.context || ''}>
                          {lead.context || 'No specific context provided'}
                        </p>
                      </td>

                      {/* Product Offer */}
                      <td className="px-5 py-3.5 max-w-xs">
                        <p className="text-xs font-medium text-gray-900 line-clamp-2" title={lead.productOffer}>
                          {lead.productOffer}
                        </p>
                        <span className="text-[11px] text-gray-500">
                          Goal: {lead.salesGoal}
                        </span>
                      </td>

                      {/* Status */}
                      <td className="px-5 py-3.5 whitespace-nowrap">
                        <span
                          className={`inline-flex items-center gap-1 text-xs font-semibold px-2.5 py-1 rounded-full ${
                            lead.status === 'CONVERTED'
                              ? 'bg-emerald-100 text-emerald-800'
                              : lead.status === 'IN_CONVERSATION'
                              ? 'bg-purple-100 text-purple-800 animate-pulse'
                              : lead.status === 'OUTREACHED'
                              ? 'bg-blue-100 text-blue-800'
                              : lead.status === 'CLOSED'
                              ? 'bg-gray-100 text-gray-600'
                              : 'bg-amber-100 text-amber-800'
                          }`}
                        >
                          {lead.status === 'IN_CONVERSATION' && <Bot className="w-3 h-3" />}
                          {lead.status === 'CONVERTED' && <CheckCircle2 className="w-3 h-3" />}
                          {lead.status.replace('_', ' ')}
                        </span>
                        <div className="text-[11px] text-gray-400 mt-1">
                          {formatRelativeTime(lead.lastInteractionAt || lead.outreachSentAt)}
                        </div>
                      </td>

                      {/* Actions */}
                      <td className="px-5 py-3.5 whitespace-nowrap text-right space-x-2">
                        {lead.status === 'PENDING' ? (
                          <div className="inline-flex items-center gap-1.5">
                            <button
                              onClick={() => outreachMutation.mutate(lead.id)}
                              disabled={outreachMutation.isPending}
                              className="inline-flex items-center gap-1 px-3 py-1.5 text-xs font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 shadow-2xs"
                              title="Mark as Outreached and ready for AI auto-reply"
                            >
                              <Send className="w-3 h-3" />
                              Send 1st Text
                            </button>
                            <button
                              onClick={() => handleCopyIcebreaker(lead)}
                              className="p-1.5 border border-gray-200 rounded-lg text-gray-600 hover:bg-gray-100"
                              title="Copy 1st icebreaker message to clipboard"
                            >
                              {copiedId === lead.id ? (
                                <Check className="w-3.5 h-3.5 text-emerald-600" />
                              ) : (
                                <Copy className="w-3.5 h-3.5" />
                              )}
                            </button>
                          </div>
                        ) : (
                          <button
                            onClick={() => setActiveLeadForChat(lead)}
                            className="inline-flex items-center gap-1.5 px-3 py-1.5 text-xs font-medium text-indigo-700 bg-indigo-50 border border-indigo-200 rounded-lg hover:bg-indigo-100 shadow-2xs"
                          >
                            <MessageSquare className="w-3.5 h-3.5" />
                            Sales Chat ({history.length})
                          </button>
                        )}
                      </td>
                    </tr>
                  )
                })}
              </tbody>
            </table>
          </div>
        )}
      </div>

      {/* ── Add Target Lead Modal ── */}
      {isAddModalOpen && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/50 p-4">
          <div className="bg-white rounded-2xl max-w-xl w-full p-6 shadow-xl space-y-4 max-h-[90vh] overflow-y-auto">
            <div className="flex items-center justify-between pb-3 border-b border-gray-100">
              <h2 className="text-lg font-bold text-gray-900 flex items-center gap-2">
                <Target className="w-5 h-5 text-indigo-600" />
                Add Target Lead for AI Outreach
              </h2>
              <button
                onClick={() => setIsAddModalOpen(false)}
                className="text-gray-400 hover:text-gray-600 p-1 rounded-lg"
              >
                <X className="w-5 h-5" />
              </button>
            </div>

            <div className="space-y-3.5 text-sm">
              <div className="grid grid-cols-1 sm:grid-cols-2 gap-3">
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    Instagram Username *
                  </label>
                  <input
                    type="text"
                    required
                    value={formData.instagramUsername}
                    onChange={(e) => setFormData({ ...formData, instagramUsername: e.target.value })}
                    placeholder="e.g. sarah_fitness"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-xs focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
                <div>
                  <label className="block text-xs font-semibold text-gray-700 mb-1">
                    Contact Name
                  </label>
                  <input
                    type="text"
                    value={formData.contactName}
                    onChange={(e) => setFormData({ ...formData, contactName: e.target.value })}
                    placeholder="e.g. Sarah Johnson"
                    className="w-full px-3 py-2 border border-gray-300 rounded-lg text-xs focus:ring-2 focus:ring-indigo-500"
                  />
                </div>
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  Tag / Niche *
                </label>
                <input
                  type="text"
                  value={formData.tag}
                  onChange={(e) => setFormData({ ...formData, tag: e.target.value })}
                  placeholder="e.g. Fitness Coaching, Video Agency, SaaS, Ecommerce"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-xs focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  Context & Pain Points *
                </label>
                <textarea
                  rows={2}
                  value={formData.context}
                  onChange={(e) => setFormData({ ...formData, context: e.target.value })}
                  placeholder="e.g. Produces 1-hr weekly podcasts, struggles with manual video editing, wants to grow Reels & TikTok"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-xs focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  Product / Offer to Sell *
                </label>
                <textarea
                  rows={2}
                  value={formData.productOffer}
                  onChange={(e) => setFormData({ ...formData, productOffer: e.target.value })}
                  placeholder="e.g. AI Video Repurposing Suite: 1-click turn podcast into 10 viral clips. $49/mo with 3-day free trial. Link: https://unireelverse.in/pro"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-xs focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              <div>
                <label className="block text-xs font-semibold text-gray-700 mb-1">
                  Sales Goal
                </label>
                <input
                  type="text"
                  value={formData.salesGoal}
                  onChange={(e) => setFormData({ ...formData, salesGoal: e.target.value })}
                  placeholder="e.g. Get them to click the link or book a 15-min discovery call"
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-xs focus:ring-2 focus:ring-indigo-500"
                />
              </div>

              {/* 1st Message with AI Generation */}
              <div>
                <div className="flex items-center justify-between mb-1">
                  <label className="block text-xs font-semibold text-gray-700">
                    1st Message (Icebreaker from You)
                  </label>
                  <button
                    type="button"
                    onClick={handleGenerateIcebreaker}
                    disabled={isGeneratingFirstMsg}
                    className="inline-flex items-center gap-1 text-xs text-indigo-600 hover:text-indigo-800 font-semibold"
                  >
                    <Sparkles className={`w-3.5 h-3.5 ${isGeneratingFirstMsg ? 'animate-spin' : ''}`} />
                    {isGeneratingFirstMsg ? 'Writing Icebreaker...' : '✨ Draft with AI'}
                  </button>
                </div>
                <textarea
                  rows={3}
                  value={formData.firstMessage}
                  onChange={(e) => setFormData({ ...formData, firstMessage: e.target.value })}
                  placeholder="Draft your friendly opening text or click 'Draft with AI' above..."
                  className="w-full px-3 py-2 border border-gray-300 rounded-lg text-xs focus:ring-2 focus:ring-indigo-500"
                />
              </div>
            </div>

            <div className="flex justify-end gap-2 pt-3 border-t border-gray-100">
              <button
                type="button"
                onClick={() => setIsAddModalOpen(false)}
                className="px-4 py-2 text-xs font-medium text-gray-700 bg-gray-100 rounded-lg hover:bg-gray-200"
              >
                Cancel
              </button>
              <button
                type="button"
                onClick={() => createMutation.mutate(formData)}
                disabled={!formData.instagramUsername || !formData.productOffer || createMutation.isPending}
                className="px-4 py-2 text-xs font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 disabled:opacity-50"
              >
                {createMutation.isPending ? 'Saving...' : 'Save Target Lead'}
              </button>
            </div>
          </div>
        </div>
      )}

      {/* ── Active Sales Chat & Context Drawer ── */}
      {activeLeadForChat && (
        <div className="fixed inset-0 z-50 flex justify-end bg-black/40">
          <div className="bg-white w-full max-w-xl h-full shadow-2xl flex flex-col justify-between">
            {/* Drawer Header */}
            <div className="p-4 border-b border-gray-200 flex items-center justify-between bg-gray-50">
              <div className="flex items-center gap-3">
                <div className="w-10 h-10 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-600 text-white font-bold flex items-center justify-center text-xs">
                  {activeLeadForChat.instagramUsername.slice(0, 2).toUpperCase()}
                </div>
                <div>
                  <h3 className="font-bold text-gray-900 text-sm flex items-center gap-1.5">
                    {activeLeadForChat.contactName || `@${activeLeadForChat.instagramUsername}`}
                    <span className="text-[10px] font-semibold px-2 py-0.5 rounded-full bg-indigo-100 text-indigo-800">
                      {activeLeadForChat.status}
                    </span>
                  </h3>
                  <p className="text-xs text-gray-500">
                    Tag: {activeLeadForChat.tag} • Goal: {activeLeadForChat.salesGoal}
                  </p>
                </div>
              </div>

              <div className="flex items-center gap-2">
                <select
                  value={activeLeadForChat.status}
                  onChange={(e) =>
                    updateStatusMutation.mutate({
                      id: activeLeadForChat.id,
                      status: e.target.value,
                    })
                  }
                  aria-label="Lead Status"
                  className="text-xs border border-gray-300 rounded-md py-1 px-2 font-medium"
                >
                  <option value="PENDING">Pending</option>
                  <option value="OUTREACHED">Outreached</option>
                  <option value="IN_CONVERSATION">In Conversation</option>
                  <option value="CONVERTED">Converted 🎉</option>
                  <option value="CLOSED">Closed</option>
                </select>
                <button
                  onClick={() => setActiveLeadForChat(null)}
                  className="p-1 text-gray-400 hover:text-gray-600 rounded-lg"
                >
                  <X className="w-5 h-5" />
                </button>
              </div>
            </div>

            {/* Context & Offer Bar */}
            <div className="px-4 py-2.5 bg-indigo-50/60 border-b border-indigo-100 text-xs text-gray-700 space-y-1">
              <div>
                <span className="font-semibold text-indigo-900">Prospect Context: </span>
                {activeLeadForChat.context || 'None'}
              </div>
              <div>
                <span className="font-semibold text-indigo-900">Offer to Sell: </span>
                {activeLeadForChat.productOffer}
              </div>
            </div>

            {/* Chat Transcript Area */}
            <div className="flex-1 p-4 overflow-y-auto space-y-3 bg-gray-50/50">
              {parseHistory(activeLeadForChat.conversationHistory).length === 0 ? (
                <div className="text-center py-12 text-gray-400 text-xs">
                  <Bot className="w-8 h-8 text-gray-300 mx-auto mb-2" />
                  No messages yet. Send your 1st outreach text, and when they reply, the AI bot takes over!
                </div>
              ) : (
                parseHistory(activeLeadForChat.conversationHistory).map((msg, idx) => (
                  <div
                    key={idx}
                    className={`flex flex-col ${
                      msg.sender === 'PROSPECT' ? 'items-start' : 'items-end'
                    }`}
                  >
                    <span className="text-[10px] text-gray-400 font-medium mb-0.5 px-1">
                      {msg.sender === 'PROSPECT'
                        ? `@${activeLeadForChat.instagramUsername}`
                        : msg.sender === 'BOT'
                        ? '🤖 AI Sales Bot'
                        : '👤 You (1st Text)'}
                    </span>
                    <div
                      className={`max-w-[85%] rounded-2xl px-3.5 py-2 text-xs shadow-2xs leading-relaxed ${
                        msg.sender === 'PROSPECT'
                          ? 'bg-white text-gray-800 border border-gray-200 rounded-tl-xs'
                          : msg.sender === 'BOT'
                          ? 'bg-gradient-to-tr from-indigo-600 to-purple-600 text-white rounded-tr-xs'
                          : 'bg-gray-800 text-white rounded-tr-xs'
                      }`}
                    >
                      {msg.text}
                    </div>
                  </div>
                ))
              )}
            </div>

            {/* Test Simulation Box */}
            <div className="p-3 border-t border-gray-200 bg-white space-y-2">
              <div className="flex items-center justify-between text-xs text-gray-500">
                <span className="flex items-center gap-1 font-semibold text-gray-700">
                  <Play className="w-3.5 h-3.5 text-indigo-600" />
                  Test AI Bot Objection Handling:
                </span>
                <span className="text-[11px] text-gray-400">Sandbox simulator</span>
              </div>
              <div className="flex gap-2">
                <input
                  type="text"
                  value={simulatedText}
                  onChange={(e) => setSimulatedText(e.target.value)}
                  onKeyDown={(e) => {
                    if (e.key === 'Enter' && simulatedText.trim() && !simulateMutation.isPending) {
                      simulateMutation.mutate({
                        id: activeLeadForChat.id,
                        message: simulatedText,
                      })
                    }
                  }}
                  placeholder="Type what prospect might say (e.g. 'How much does it cost?')"
                  className="flex-1 px-3 py-2 text-xs border border-gray-300 rounded-lg focus:ring-2 focus:ring-indigo-500"
                />
                <button
                  type="button"
                  onClick={() => {
                    if (simulatedText.trim()) {
                      simulateMutation.mutate({
                        id: activeLeadForChat.id,
                        message: simulatedText,
                      })
                    }
                  }}
                  disabled={!simulatedText.trim() || simulateMutation.isPending}
                  className="inline-flex items-center gap-1 px-3 py-2 text-xs font-medium text-white bg-indigo-600 rounded-lg hover:bg-indigo-700 disabled:opacity-50"
                >
                  <Send className="w-3.5 h-3.5" />
                  {simulateMutation.isPending ? 'Bot Replying...' : 'Simulate'}
                </button>
              </div>
            </div>
          </div>
        </div>
      )}
    </div>
  )
}
