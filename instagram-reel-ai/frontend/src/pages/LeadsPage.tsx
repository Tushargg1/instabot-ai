import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import {
  Mail,
  Search,
  Download,
  RefreshCw,
  UserCheck,
  Calendar,
  Instagram,
  FileSpreadsheet
} from 'lucide-react'

interface CapturedLead {
  id: string
  email: string
  instagramSenderId: string
  instagramUsername: string | null
  fullName: string | null
  avatarUrl: string | null
  sourceReelTitle: string | null
  capturedAt: string
  status: string
}

interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
}

function formatDate(iso: string) {
  const d = new Date(iso)
  return d.toLocaleDateString(undefined, {
    year: 'numeric',
    month: 'short',
    day: 'numeric',
    hour: '2-digit',
    minute: '2-digit',
  })
}

export default function LeadsPage() {
  const [search, setSearch] = useState('')
  const [page, setPage] = useState(0)

  const { data, isLoading, refetch, isFetching } = useQuery<PageResponse<CapturedLead>>({
    queryKey: ['captured-leads', search, page],
    queryFn: () => apiClient.getLeads(search, page, 25),
    staleTime: 30000,
  })

  const leads = data?.content || []
  const total = data?.totalElements || 0

  const handleExportCsv = () => {
    if (!leads.length) return
    const headers = ['Email', 'Instagram Username', 'Full Name', 'Source Reel', 'Captured At', 'Status']
    const rows = leads.map((l) => [
      `"${l.email}"`,
      `"${l.instagramUsername || l.instagramSenderId}"`,
      `"${l.fullName || ''}"`,
      `"${(l.sourceReelTitle || '').replace(/"/g, '""')}"`,
      `"${l.capturedAt}"`,
      `"${l.status}"`,
    ])
    const csvContent = 'data:text/csv;charset=utf-8,' + [headers.join(','), ...rows.map((r) => r.join(','))].join('\n')
    const encodedUri = encodeURI(csvContent)
    const link = document.createElement('a')
    link.setAttribute('href', encodedUri)
    link.setAttribute('download', `leads_contacts_${new Date().toISOString().slice(0, 10)}.csv`)
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
            <Mail className="w-7 h-7 text-indigo-600" />
            Leads & VIP Contacts
          </h1>
          <p className="text-sm text-gray-500 mt-1">
            Audience emails automatically captured from Instagram DMs for templates, resources & VIP lists
          </p>
        </div>

        <div className="flex items-center gap-2 flex-wrap">
          <button
            onClick={handleExportCsv}
            disabled={leads.length === 0}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-sm font-medium text-emerald-700 bg-emerald-50 border border-emerald-200 rounded-lg hover:bg-emerald-100 transition-colors shadow-xs disabled:opacity-50"
            title="Download CSV for Google Sheets / Excel / Mailchimp"
          >
            <Download className="w-4 h-4" />
            Export CSV
          </button>
          <button
            onClick={() => refetch()}
            disabled={isFetching}
            className="inline-flex items-center gap-2 px-3.5 py-2 text-sm font-medium text-gray-700 bg-white border border-gray-300 rounded-lg hover:bg-gray-50 shadow-xs transition-colors disabled:opacity-50"
          >
            <RefreshCw className={`w-4 h-4 ${isFetching ? 'animate-spin' : ''}`} />
            Refresh
          </button>
        </div>
      </div>

      {/* Stats Bar */}
      <div className="grid grid-cols-1 sm:grid-cols-3 gap-4">
        <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-gray-500 uppercase">Total VIP Leads</p>
            <p className="text-2xl font-extrabold text-gray-900 mt-1">{total}</p>
          </div>
          <div className="p-3 bg-indigo-50 text-indigo-600 rounded-xl">
            <Mail className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-gray-500 uppercase">Verified Channels</p>
            <p className="text-2xl font-extrabold text-gray-900 mt-1">Instagram DM</p>
          </div>
          <div className="p-3 bg-pink-50 text-pink-600 rounded-xl">
            <Instagram className="w-6 h-6" />
          </div>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-4 shadow-xs flex items-center justify-between">
          <div>
            <p className="text-xs font-semibold text-gray-500 uppercase">Sync & Export Ready</p>
            <p className="text-2xl font-extrabold text-gray-900 mt-1">CSV / Excel</p>
          </div>
          <div className="p-3 bg-emerald-50 text-emerald-600 rounded-xl">
            <FileSpreadsheet className="w-6 h-6" />
          </div>
        </div>
      </div>

      {/* Filter / Search */}
      <div className="bg-white p-4 rounded-xl border border-gray-200 shadow-xs">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 w-4 h-4 text-gray-400" />
          <input
            type="text"
            value={search}
            onChange={(e) => {
              setSearch(e.target.value)
              setPage(0)
            }}
            placeholder="Search leads by email, name, or Instagram username..."
            className="w-full pl-9 pr-4 py-2 border border-gray-300 rounded-lg text-sm focus:outline-hidden focus:ring-2 focus:ring-indigo-500 focus:border-indigo-500"
          />
        </div>
      </div>

      {/* Table */}
      <div className="bg-white rounded-xl border border-gray-200 shadow-xs overflow-hidden">
        {isLoading ? (
          <div className="flex justify-center items-center py-20 text-gray-400">
            <RefreshCw className="w-8 h-8 animate-spin" />
          </div>
        ) : leads.length === 0 ? (
          <div className="text-center py-16 px-4">
            <Mail className="w-12 h-12 text-gray-300 mx-auto mb-3" />
            <h3 className="text-base font-semibold text-gray-800">No leads captured yet</h3>
            <p className="text-xs text-gray-500 max-w-sm mx-auto mt-1">
              When users drop their email in DMs requesting Notion templates or resource lists, they will appear here automatically!
            </p>
          </div>
        ) : (
          <div className="overflow-x-auto">
            <table className="min-w-full divide-y divide-gray-200 text-left text-sm">
              <thead className="bg-gray-50 text-gray-600 font-semibold text-xs uppercase tracking-wider">
                <tr>
                  <th className="px-5 py-3.5">Subscriber / Contact</th>
                  <th className="px-5 py-3.5">Email Address</th>
                  <th className="px-5 py-3.5">Source Reel</th>
                  <th className="px-5 py-3.5">Captured At</th>
                  <th className="px-5 py-3.5 text-right">Status</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100 bg-white">
                {leads.map((lead) => (
                  <tr key={lead.id} className="hover:bg-gray-50 transition-colors">
                    {/* Contact Profile */}
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      <div className="flex items-center gap-3">
                        {lead.avatarUrl ? (
                          <img
                            src={lead.avatarUrl}
                            alt={lead.fullName || lead.email}
                            className="w-9 h-9 rounded-full object-cover border border-gray-200 shrink-0"
                          />
                        ) : (
                          <div className="w-9 h-9 rounded-full bg-gradient-to-tr from-indigo-500 to-purple-600 text-white font-bold flex items-center justify-center text-xs shrink-0">
                            {(lead.fullName || lead.instagramUsername || lead.email).slice(0, 2).toUpperCase()}
                          </div>
                        )}
                        <div>
                          <div className="font-semibold text-gray-900 text-sm">
                            {lead.fullName || (lead.instagramUsername ? `@${lead.instagramUsername}` : 'Instagram User')}
                          </div>
                          {lead.instagramUsername && (
                            <a
                              href={`https://instagram.com/${lead.instagramUsername}`}
                              target="_blank"
                              rel="noreferrer"
                              className="text-xs text-indigo-600 hover:underline flex items-center gap-1"
                            >
                              @{lead.instagramUsername}
                            </a>
                          )}
                        </div>
                      </div>
                    </td>

                    {/* Email */}
                    <td className="px-5 py-3.5 whitespace-nowrap">
                      <span className="font-medium text-gray-900 bg-gray-50 px-2.5 py-1 rounded-md border border-gray-100">
                        {lead.email}
                      </span>
                    </td>

                    {/* Source Reel */}
                    <td className="px-5 py-3.5 text-xs text-gray-600 max-w-xs truncate">
                      {lead.sourceReelTitle || 'Shared Reel'}
                    </td>

                    {/* Captured At */}
                    <td className="px-5 py-3.5 whitespace-nowrap text-xs text-gray-500">
                      <div className="flex items-center gap-1">
                        <Calendar className="w-3.5 h-3.5 text-gray-400" />
                        {formatDate(lead.capturedAt)}
                      </div>
                    </td>

                    {/* Status */}
                    <td className="px-5 py-3.5 whitespace-nowrap text-right">
                      <span className="inline-flex items-center gap-1 text-xs font-semibold px-2 py-0.5 rounded-full bg-emerald-100 text-emerald-800">
                        <UserCheck className="w-3 h-3" />
                        {lead.status}
                      </span>
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </div>
    </div>
  )
}
