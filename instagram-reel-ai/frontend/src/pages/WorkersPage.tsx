import { useQuery } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import type { WorkerInstance } from '../types'

export default function WorkersPage() {
  const { data: active, isLoading: loadingActive } = useQuery({
    queryKey: ['workers-active'],
    queryFn: () => apiClient.getActiveWorkers(),
    refetchInterval: 10000,
  })

  const { data: stale, isLoading: loadingStale } = useQuery({
    queryKey: ['workers-stale'],
    queryFn: () => apiClient.getStaleWorkers(),
  })

  const stateColor = (state: string) => {
    switch (state) {
      case 'ACTIVE': return 'bg-green-100 text-green-700'
      case 'STALE': return 'bg-yellow-100 text-yellow-700'
      case 'STOPPED': return 'bg-red-100 text-red-700'
      default: return 'bg-gray-100 text-gray-600'
    }
  }

  const WorkerTable = ({ workers, title }: { workers: WorkerInstance[]; title: string }) => (
    <div className="mb-8">
      <h3 className="text-lg font-semibold text-gray-900 mb-3">{title} ({workers.length})</h3>
      {workers.length ? (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Instance</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Host</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">PID</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">State</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Last Heartbeat</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {workers.map((w) => (
                <tr key={w.id} className="hover:bg-gray-50">
                  <td className="px-4 py-3 text-sm font-mono text-gray-600">{w.instanceId}</td>
                  <td className="px-4 py-3 text-sm text-gray-600">{w.hostName}</td>
                  <td className="px-4 py-3 text-sm text-gray-600">{w.processId}</td>
                  <td className="px-4 py-3">
                    <span className={`px-2 py-1 text-xs rounded-full font-medium ${stateColor(w.state)}`}>{w.state}</span>
                  </td>
                  <td className="px-4 py-3 text-sm text-gray-500">{new Date(w.lastHeartbeatAt).toLocaleString()}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      ) : (
        <p className="text-gray-500 text-sm">None</p>
      )}
    </div>
  )

  if (loadingActive || loadingStale) return <div className="text-center py-12 text-gray-500">Loading...</div>

  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Worker Instances</h2>
      <WorkerTable workers={active || []} title="Active Workers" />
      <WorkerTable workers={stale || []} title="Stale / Stopped Workers" />
    </div>
  )
}
