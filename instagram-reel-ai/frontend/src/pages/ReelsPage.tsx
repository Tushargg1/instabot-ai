import { useState } from 'react'
import { useQuery } from '@tanstack/react-query'
import { Link } from 'react-router-dom'
import { Search } from 'lucide-react'
import { apiClient } from '../api/client'

export default function ReelsPage() {
  const [page, setPage] = useState(0)
  const [searchQuery, setSearchQuery] = useState('')
  const [activeSearch, setActiveSearch] = useState('')

  const { data, isLoading } = useQuery({
    queryKey: ['reels', page, activeSearch],
    queryFn: () => activeSearch
      ? apiClient.searchReels(activeSearch, page, 20)
      : apiClient.getReels(page, 20),
  })

  const handleSearch = (e: React.FormEvent) => {
    e.preventDefault()
    setActiveSearch(searchQuery)
    setPage(0)
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Saved Reels</h2>
      </div>

      <form onSubmit={handleSearch} className="mb-6">
        <div className="relative">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 text-gray-400" size={18} />
          <input
            type="text"
            value={searchQuery}
            onChange={e => setSearchQuery(e.target.value)}
            placeholder="Search reels by title, summary, transcript, topic, or creator..."
            className="w-full pl-10 pr-4 py-2 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
          />
        </div>
      </form>

      {isLoading ? (
        <div className="text-center py-12 text-gray-500">Loading...</div>
      ) : data?.content?.length ? (
        <>
          <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
            <table className="w-full">
              <thead className="bg-gray-50 border-b border-gray-200">
                <tr>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Title</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Creator</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Topics</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Status</th>
                  <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Date</th>
                </tr>
              </thead>
              <tbody className="divide-y divide-gray-100">
                {data.content.map((reel: any) => (
                  <tr key={reel.id} className="hover:bg-gray-50">
                    <td className="px-4 py-3">
                      <Link to={`/reels/${reel.id}`} className="text-blue-600 hover:text-blue-800 font-medium">
                        {reel.title || 'Untitled'}
                      </Link>
                      {reel.summaryExcerpt && (
                        <p className="text-xs text-gray-500 mt-1 line-clamp-1">{reel.summaryExcerpt}</p>
                      )}
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-600">{reel.creatorUsername || '—'}</td>
                    <td className="px-4 py-3">
                      <div className="flex flex-wrap gap-1">
                        {reel.topics?.slice(0, 3).map((t: string) => (
                          <span key={t} className="px-2 py-0.5 bg-blue-50 text-blue-600 text-xs rounded-full">{t}</span>
                        ))}
                      </div>
                    </td>
                    <td className="px-4 py-3">
                      <span className={`px-2 py-1 text-xs rounded-full font-medium ${
                        reel.processingStatus === 'COMPLETED' ? 'bg-green-100 text-green-700' :
                        reel.processingStatus === 'FAILED' ? 'bg-red-100 text-red-700' :
                        'bg-yellow-100 text-yellow-700'
                      }`}>
                        {reel.processingStatus}
                      </span>
                    </td>
                    <td className="px-4 py-3 text-sm text-gray-500">
                      {new Date(reel.createdAt).toLocaleDateString()}
                    </td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          <div className="flex justify-between items-center mt-4">
            <button
              onClick={() => setPage(p => Math.max(0, p - 1))}
              disabled={page === 0}
              className="px-4 py-2 text-sm border border-gray-300 rounded-lg disabled:opacity-50"
            >
              Previous
            </button>
            <span className="text-sm text-gray-500">
              Page {page + 1} of {data.totalPages || 1}
            </span>
            <button
              onClick={() => setPage(p => p + 1)}
              disabled={page >= (data.totalPages - 1)}
              className="px-4 py-2 text-sm border border-gray-300 rounded-lg disabled:opacity-50"
            >
              Next
            </button>
          </div>
        </>
      ) : (
        <div className="text-center py-12 text-gray-500">
          No reels found. {activeSearch && 'Try a different search term.'}
        </div>
      )}
    </div>
  )
}
