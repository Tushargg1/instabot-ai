import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'

export default function ManualActionsPage() {
  const [page, setPage] = useState(0)
  const [filter, setFilter] = useState('')
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['manual-actions', page, filter],
    queryFn: () => apiClient.getManualActions(page, 20, filter || undefined),
  })

  const claimMutation = useMutation({
    mutationFn: (id: string) => apiClient.claimAction(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['manual-actions'] }),
  })

  const completeMutation = useMutation({
    mutationFn: (id: string) => apiClient.completeAction(id, 'Completed manually'),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['manual-actions'] }),
  })

  const statusColor = (status: string) => {
    switch (status) {
      case 'PENDING': return 'bg-yellow-100 text-yellow-700'
      case 'CLAIMED': return 'bg-blue-100 text-blue-700'
      case 'COMPLETED': return 'bg-green-100 text-green-700'
      case 'FAILED': return 'bg-red-100 text-red-700'
      case 'CANCELLED': return 'bg-gray-100 text-gray-600'
      default: return 'bg-gray-100 text-gray-600'
    }
  }

  return (
    <div>
      <div className="flex items-center justify-between mb-6">
        <h2 className="text-2xl font-bold text-gray-900">Manual Actions</h2>
        <select value={filter} onChange={e => { setFilter(e.target.value); setPage(0) }}
          className="border border-gray-300 rounded-lg px-3 py-2 text-sm">
          <option value="">All</option>
          <option value="PENDING">Pending</option>
          <option value="CLAIMED">Claimed</option>
          <option value="COMPLETED">Completed</option>
          <option value="FAILED">Failed</option>
        </select>
      </div>

      {isLoading ? (
        <div className="text-center py-12 text-gray-500">Loading...</div>
      ) : data?.content?.length ? (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Type</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Payload</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Status</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Claimed By</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Created</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.content.map((action) => (
                <tr key={action.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm font-medium text-gray-900">{action.actionType}</td>
                  <td className="px-4 py-3 text-sm text-gray-600 max-w-[150px] truncate">{action.actionPayload || '—'}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-1 text-xs rounded-full font-medium ${statusColor(action.status)}`}>
                      {action.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">{action.claimedByUsername || '—'}</td>
                  <td className="px-4 py-3 text-sm text-gray-500">{new Date(action.createdAt).toLocaleString()}</td>
                  <td className="px-4 py-3 space-x-2">
                    {action.status === 'PENDING' && (
                      <button onClick={() => claimMutation.mutate(action.id)}
                        className="px-3 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700">Claim</button>
                    )}
                    {action.status === 'CLAIMED' && (
                      <button onClick={() => completeMutation.mutate(action.id)}
                        className="px-3 py-1 text-xs bg-green-600 text-white rounded hover:bg-green-700">Complete</button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>

          <div className="flex justify-between items-center p-4 border-t border-gray-200">
            <button onClick={() => setPage(p => Math.max(0, p - 1))} disabled={page === 0}
              className="px-4 py-2 text-sm border border-gray-300 rounded-lg disabled:opacity-50">Previous</button>
            <span className="text-sm text-gray-500">Page {page + 1}</span>
            <button onClick={() => setPage(p => p + 1)} disabled={!data?.content?.length || data.content.length < 20}
              className="px-4 py-2 text-sm border border-gray-300 rounded-lg disabled:opacity-50">Next</button>
          </div>
        </div>
      ) : (
        <div className="text-center py-12 text-gray-500">No manual actions.</div>
      )}
    </div>
  )
}
