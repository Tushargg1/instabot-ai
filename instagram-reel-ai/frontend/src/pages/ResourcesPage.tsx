import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { Link } from 'react-router-dom'

export default function ResourcesPage() {
  const [page, setPage] = useState(0)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['resources', page],
    queryFn: () => apiClient.getResources(page, 20),
  })

  const refreshMutation = useMutation({
    mutationFn: (id: string) => apiClient.refreshResource(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['resources'] }),
  })

  const statusColor = (status: string) => {
    switch (status) {
      case 'AVAILABLE': return 'bg-green-100 text-green-700'
      case 'ACTION_REQUIRED': return 'bg-orange-100 text-orange-700'
      case 'NOT_FOUND': return 'bg-gray-100 text-gray-600'
      case 'ACTION_IN_PROGRESS': return 'bg-blue-100 text-blue-700'
      case 'ACTION_COMPLETED': return 'bg-green-100 text-green-700'
      default: return 'bg-yellow-100 text-yellow-700'
    }
  }

  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Resources</h2>

      {isLoading ? (
        <div className="text-center py-12 text-gray-500">Loading...</div>
      ) : data?.content?.length ? (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Reel</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Type</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">URL / Action</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Status</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Verified</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.content.map((r) => (
                <tr key={r.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm">
                    <Link to={`/reels/${r.reelId}`} className="text-blue-600 hover:underline">
                      {r.reelId.substring(0, 8)}...
                    </Link>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">{r.resourceType || '—'}</td>
                  <td className="px-4 py-3 text-sm text-gray-600 max-w-[200px] truncate">
                    {r.resourceUrl ? (
                      <a href={r.resourceUrl} target="_blank" rel="noopener noreferrer" className="text-blue-600 hover:underline">
                        {r.resourceUrl}
                      </a>
                    ) : r.requiredAction || '—'}
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-1 text-xs rounded-full font-medium ${statusColor(r.status)}`}>
                      {r.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-500">
                    {r.lastVerifiedAt ? new Date(r.lastVerifiedAt).toLocaleString() : '—'}
                  </td>
                  <td className="px-4 py-3">
                    <button
                      onClick={() => refreshMutation.mutate(r.id)}
                      className="px-3 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700"
                    >
                      Refresh
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="flex justify-between items-center p-4 border-t border-gray-200">
            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
              className="px-4 py-2 text-sm border border-gray-300 rounded-lg disabled:opacity-50">Previous</button>
            <span className="text-sm text-gray-500">Page {page + 1} of {data.totalPages || 1}</span>
            <button onClick={() => setPage(p => p + 1)} disabled={page >= (data.totalPages - 1)}
              className="px-4 py-2 text-sm border border-gray-300 rounded-lg disabled:opacity-50">Next</button>
          </div>
        </div>
      ) : (
        <div className="text-center py-12 text-gray-500">No resources detected yet.</div>
      )}
    </div>
  )
}
