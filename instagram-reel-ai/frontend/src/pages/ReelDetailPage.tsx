import { useParams, Link } from 'react-router-dom'
import { useQuery } from '@tanstack/react-query'
import { ArrowLeft } from 'lucide-react'
import { apiClient } from '../api/client'

export default function ReelDetailPage() {
  const { id } = useParams<{ id: string }>()

  const { data: reel, isLoading } = useQuery({
    queryKey: ['reel', id],
    queryFn: () => apiClient.getReel(id!),
    enabled: !!id,
  })

  if (isLoading) return <div className="text-center py-12 text-gray-500">Loading...</div>
  if (!reel) return <div className="text-center py-12 text-gray-500">Reel not found</div>

  return (
    <div>
      <Link to="/reels" className="inline-flex items-center gap-2 text-blue-600 hover:text-blue-800 mb-6">
        <ArrowLeft size={16} /> Back to Reels
      </Link>

      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
        <div className="flex items-start justify-between mb-4">
          <div>
            <h2 className="text-2xl font-bold text-gray-900">{reel.title || 'Untitled Reel'}</h2>
            {reel.creatorUsername && (
              <p className="text-gray-500 mt-1">by @{reel.creatorUsername}</p>
            )}
          </div>
          <span className={`px-3 py-1 text-sm rounded-full font-medium ${
            reel.processingStatus === 'COMPLETED' ? 'bg-green-100 text-green-700' :
            reel.processingStatus === 'FAILED' ? 'bg-red-100 text-red-700' :
            'bg-yellow-100 text-yellow-700'
          }`}>
            {reel.processingStatus}
          </span>
        </div>

        {reel.sourceUrl && (
          <a href={reel.sourceUrl} target="_blank" rel="noopener noreferrer"
            className="text-blue-600 hover:underline text-sm">
            View Source →
          </a>
        )}
      </div>

      {reel.summaryText && (
        <div className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-3">Summary</h3>
          <p className="text-gray-700 leading-relaxed">{reel.summaryText}</p>
          {reel.confidence && (
            <p className="text-sm text-gray-400 mt-2">Confidence: {(reel.confidence * 100).toFixed(0)}%</p>
          )}
        </div>
      )}

      <div className="grid grid-cols-1 md:grid-cols-2 gap-6 mb-6">
        {reel.keyPoints?.length > 0 && (
          <div className="bg-white rounded-xl border border-gray-200 p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-3">🔑 Key Points</h3>
            <ul className="space-y-2">
              {reel.keyPoints.map((point: string, i: number) => (
                <li key={i} className="text-gray-700 text-sm flex gap-2">
                  <span className="text-blue-500">•</span> {point}
                </li>
              ))}
            </ul>
          </div>
        )}

        {reel.actionItems?.length > 0 && (
          <div className="bg-white rounded-xl border border-gray-200 p-6">
            <h3 className="text-lg font-semibold text-gray-900 mb-3">✅ Action Items</h3>
            <ul className="space-y-2">
              {reel.actionItems.map((item: string, i: number) => (
                <li key={i} className="text-gray-700 text-sm flex gap-2">
                  <span className="text-green-500">•</span> {item}
                </li>
              ))}
            </ul>
          </div>
        )}
      </div>

      <div className="grid grid-cols-1 md:grid-cols-3 gap-6 mb-6">
        {reel.topics?.length > 0 && (
          <div className="bg-white rounded-xl border border-gray-200 p-6">
            <h3 className="font-semibold text-gray-900 mb-2">Topics</h3>
            <div className="flex flex-wrap gap-2">
              {reel.topics.map((t: string) => (
                <span key={t} className="px-2 py-1 bg-blue-50 text-blue-600 text-xs rounded-full">{t}</span>
              ))}
            </div>
          </div>
        )}

        {reel.toolsOrProducts?.length > 0 && (
          <div className="bg-white rounded-xl border border-gray-200 p-6">
            <h3 className="font-semibold text-gray-900 mb-2">Tools & Products</h3>
            <div className="flex flex-wrap gap-2">
              {reel.toolsOrProducts.map((t: string) => (
                <span key={t} className="px-2 py-1 bg-purple-50 text-purple-600 text-xs rounded-full">{t}</span>
              ))}
            </div>
          </div>
        )}

        {reel.peopleOrCompanies?.length > 0 && (
          <div className="bg-white rounded-xl border border-gray-200 p-6">
            <h3 className="font-semibold text-gray-900 mb-2">People & Companies</h3>
            <div className="flex flex-wrap gap-2">
              {reel.peopleOrCompanies.map((t: string) => (
                <span key={t} className="px-2 py-1 bg-orange-50 text-orange-600 text-xs rounded-full">{t}</span>
              ))}
            </div>
          </div>
        )}
      </div>

      {reel.claimsToVerify?.length > 0 && (
        <div className="bg-yellow-50 rounded-xl border border-yellow-200 p-6 mb-6">
          <h3 className="font-semibold text-yellow-800 mb-2">⚠️ Claims to Verify</h3>
          <ul className="space-y-1">
            {reel.claimsToVerify.map((claim: string, i: number) => (
              <li key={i} className="text-yellow-700 text-sm">• {claim}</li>
            ))}
          </ul>
        </div>
      )}

      {reel.transcript && (
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-3">📝 Transcript</h3>
          {reel.durationSeconds && (
            <p className="text-sm text-gray-400 mb-2">Duration: {Math.round(reel.durationSeconds)}s</p>
          )}
          <p className="text-gray-700 text-sm leading-relaxed whitespace-pre-wrap">{reel.transcript}</p>
        </div>
      )}
    </div>
  )
}
