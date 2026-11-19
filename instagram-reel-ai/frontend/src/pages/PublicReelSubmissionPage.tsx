import { useState } from 'react'
import { useMutation, useQuery } from '@tanstack/react-query'
import { CheckCircle, Film } from 'lucide-react'
import { useParams } from 'react-router-dom'
import { apiClient } from '../api/client'

export default function PublicReelSubmissionPage() {
  const { token } = useParams<{ token: string }>()
  const [reelUrl, setReelUrl] = useState('')
  const metadata = useQuery({
    queryKey: ['public-submission', token],
    queryFn: () => apiClient.getSubmissionMetadata(token!),
    enabled: !!token,
    retry: false,
  })
  const submit = useMutation({
    mutationFn: () => apiClient.submitReelPublic(token!, reelUrl.trim()),
  })

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-xl">
        <div className="text-center mb-6">
          <div className="mx-auto w-12 h-12 bg-blue-600 text-white rounded-xl flex items-center justify-center mb-3"><Film size={24} /></div>
          <h1 className="text-2xl font-bold text-gray-900">Submit an Instagram Reel</h1>
          <p className="text-gray-500 mt-1">Share a Reel for processing by Reel Intelligence.</p>
        </div>

        <div className="bg-white rounded-xl border border-gray-200 p-6 shadow-sm">
          {metadata.isLoading ? (
            <div className="text-center py-8 text-gray-500">Validating submission link...</div>
          ) : metadata.error ? (
            <div className="text-center py-8">
              <h2 className="font-semibold text-red-700">This submission link is unavailable</h2>
              <p className="text-sm text-gray-500 mt-2">{metadata.error.message}</p>
            </div>
          ) : submit.data ? (
            <div className="text-center py-6">
              <CheckCircle className="mx-auto text-green-600" size={48} />
              <h2 className="text-xl font-semibold text-gray-900 mt-4">Reel submitted</h2>
              <p className="text-gray-500 mt-2">Status: {submit.data.status}</p>
              <dl className="mt-5 text-left bg-gray-50 rounded-lg p-4 text-sm space-y-2">
                <div className="flex justify-between gap-3"><dt className="text-gray-500">Reel</dt><dd className="font-mono text-gray-700">{submit.data.reelId}</dd></div>
                <div className="flex justify-between gap-3"><dt className="text-gray-500">Key</dt><dd className="font-mono text-gray-700">{submit.data.canonicalKey}</dd></div>
              </dl>
            </div>
          ) : (
            <form onSubmit={event => { event.preventDefault(); submit.mutate() }}>
              {metadata.data?.publicLabel && <p className="mb-4 text-sm bg-blue-50 text-blue-700 rounded-lg p-3">For: {metadata.data.publicLabel}</p>}
              <label className="block text-sm font-medium text-gray-700">
                Instagram Reel URL
                <input
                  type="url"
                  required
                  value={reelUrl}
                  onChange={event => setReelUrl(event.target.value)}
                  placeholder="https://www.instagram.com/reel/..."
                  className="mt-2 w-full px-3 py-2.5 border border-gray-300 rounded-lg focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
                />
              </label>
              {submit.error && <p className="mt-3 text-sm text-red-600">{submit.error.message}</p>}
              <button type="submit" disabled={submit.isPending || metadata.data?.status !== 'ACTIVE'} className="mt-5 w-full px-4 py-2.5 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
                {submit.isPending ? 'Submitting...' : 'Submit Reel'}
              </button>
              <p className="text-xs text-gray-400 text-center mt-3">
                Link status: {metadata.data?.status || 'Unknown'} · Expires {metadata.data?.expiresAt ? new Date(metadata.data.expiresAt).toLocaleString() : '—'}
              </p>
            </form>
          )}
        </div>
      </div>
    </div>
  )
}