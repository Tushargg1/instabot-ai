import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { apiClient } from '../api/client'

export default function JobsPage() {
  const [page] = useState(0)
  const queryClient = useQueryClient()

  const { data, isLoading } = useQuery({
    queryKey: ['jobs', page],
    queryFn: () => apiClient.getJobs(page, 20),
  })

  const retryMutation = useMutation({
    mutationFn: (id: string) => apiClient.retryJob(id),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['jobs'] }),
  })

  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Processing Jobs</h2>

      {isLoading ? (
        <div className="text-center py-12 text-gray-500">Loading...</div>
      ) : data?.content?.length ? (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Job ID</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Status</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Stage</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Attempts</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Error</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Created</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Actions</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {data.content.map((job: any) => (
                <tr key={job.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm font-mono text-gray-600">
                    {job.id.substring(0, 8)}...
                  </td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-1 text-xs rounded-full font-medium ${
                      job.status === 'COMPLETED' ? 'bg-green-100 text-green-700' :
                      job.status === 'FAILED' || job.status === 'MEDIA_UNAVAILABLE' ? 'bg-red-100 text-red-700' :
                      job.status === 'QUEUED' ? 'bg-blue-100 text-blue-700' :
                      'bg-yellow-100 text-yellow-700'
                    }`}>
                      {job.status}
                    </span>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-600">{job.stage || '—'}</td>
                  <td className="px-4 py-3 text-sm text-gray-600">{job.attempts}/{job.maxAttempts}</td>
                  <td className="px-4 py-3 text-sm text-red-600 max-w-[200px] truncate">
                    {job.lastErrorMessage || '—'}
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-500">
                    {new Date(job.createdAt).toLocaleString()}
                  </td>
                  <td className="px-4 py-3">
                    {(job.status === 'FAILED' || job.status === 'MEDIA_UNAVAILABLE' ||
                      job.status === 'REPLY_FAILED') && (
                      <button
                        onClick={() => retryMutation.mutate(job.id)}
                        className="px-3 py-1 text-xs bg-blue-600 text-white rounded hover:bg-blue-700"
                      >
                        Retry
                      </button>
                    )}
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <div className="text-center py-12 text-gray-500">No processing jobs yet.</div>
      )}
    </div>
  )
}
