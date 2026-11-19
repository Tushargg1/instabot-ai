import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'

export default function OverviewPage() {
  const { data: metrics } = useQuery({
    queryKey: ['dashboard-metrics'],
    queryFn: () => apiClient.getMetrics(),
    refetchInterval: 30000,
  })

  const { data: reelsData } = useQuery({
    queryKey: ['reels', 0],
    queryFn: () => apiClient.getReels(0, 5),
  })

  const { data: health } = useQuery({
    queryKey: ['health'],
    queryFn: () => apiClient.health(),
  })

  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Overview</h2>

      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Canonical Reels</p>
          <p className="text-3xl font-bold text-gray-900 mt-1">{metrics?.totalReels ?? '—'}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Resources Found</p>
          <p className="text-3xl font-bold text-gray-900 mt-1">{metrics?.totalResources ?? '—'}</p>
          <p className="text-xs text-green-600 mt-1">{metrics?.availableResources ?? 0} available</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Total Requests</p>
          <p className="text-3xl font-bold text-gray-900 mt-1">{metrics?.totalRequests ?? '—'}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-sm text-gray-500">System</p>
          <p className="text-xl font-bold text-green-600 mt-1">
            {health?.status === 'UP' ? '✓ Healthy' : '⚠ Unknown'}
          </p>
        </div>
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-4 mb-8">
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Pending Jobs</p>
          <p className="text-2xl font-bold text-yellow-600 mt-1">{metrics?.pendingJobs ?? 0}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Failed Jobs</p>
          <p className="text-2xl font-bold text-red-600 mt-1">{metrics?.failedJobs ?? 0}</p>
        </div>
        <div className="bg-white rounded-xl border border-gray-200 p-5">
          <p className="text-sm text-gray-500">Pending Actions</p>
          <p className="text-2xl font-bold text-orange-600 mt-1">{metrics?.pendingActions ?? 0}</p>
        </div>
      </div>

      <div className="bg-white rounded-xl border border-gray-200 p-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Recent Reels</h3>
        {reelsData?.content?.length ? (
          <div className="space-y-3">
            {reelsData.content.map((reel) => (
              <div key={reel.id} className="flex items-center justify-between py-2 border-b border-gray-100 last:border-0">
                <div>
                  <p className="font-medium text-gray-900">{reel.title || 'Untitled Reel'}</p>
                  <p className="text-sm text-gray-500">{reel.creatorUsername || 'Unknown'}</p>
                </div>
                <span className={`px-2 py-1 text-xs rounded-full font-medium ${
                  reel.processingStatus === 'COMPLETED' ? 'bg-green-100 text-green-700' :
                  reel.processingStatus === 'FAILED' ? 'bg-red-100 text-red-700' :
                  'bg-yellow-100 text-yellow-700'
                }`}>
                  {reel.processingStatus}
                </span>
              </div>
            ))}
          </div>
        ) : (
          <p className="text-gray-500">No reels yet. Send a Reel to your connected Instagram account or use a submission link.</p>
        )}
      </div>
    </div>
  )
}
