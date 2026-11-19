import { useState, useEffect } from 'react'
import { useParams } from 'react-router-dom'
import { apiClient } from '../api/client'

export default function PublicSubmitPage() {
  const { token } = useParams<{ token: string }>()
  const [metadata, setMetadata] = useState<{ status: string; publicLabel: string | null; expiresAt: string } | null>(null)
  const [reelUrl, setReelUrl] = useState('')
  const [status, setStatus] = useState<'loading' | 'ready' | 'submitting' | 'success' | 'error' | 'invalid'>('loading')
  const [message, setMessage] = useState('')

  useEffect(() => {
    if (!token) { setStatus('invalid'); return }
    apiClient.getSubmissionMetadata(token)
      .then(meta => {
        setMetadata(meta)
        if (meta.status === 'VALID') setStatus('ready')
        else { setStatus('invalid'); setMessage(`Token is ${meta.status.toLowerCase()}`) }
      })
      .catch(() => { setStatus('invalid'); setMessage('Token not found or expired') })
  }, [token])

  const handleSubmit = async (e: React.FormEvent) => {
    e.preventDefault()
    if (!token || !reelUrl.trim()) return
    setStatus('submitting')
    try {
      await apiClient.submitReelPublic(token, reelUrl.trim())
      setStatus('success')
      setMessage('Reel submitted successfully! It will be processed shortly.')
    } catch (err: any) {
      setStatus('error')
      setMessage(err.message || 'Submission failed')
    }
  }

  return (
    <div className="min-h-screen bg-gray-50 flex items-center justify-center p-4">
      <div className="w-full max-w-md">
        <div className="bg-white rounded-xl border border-gray-200 p-8 shadow-sm">
          <h1 className="text-2xl font-bold text-gray-900 mb-2">📱 Submit a Reel</h1>
          <p className="text-gray-500 text-sm mb-6">Paste an Instagram Reel URL to get an AI summary</p>

          {status === 'loading' && <p className="text-gray-500">Validating link...</p>}

          {status === 'invalid' && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4">
              <p className="text-red-700 text-sm">{message || 'This submission link is not valid.'}</p>
            </div>
          )}

          {status === 'ready' && (
            <form onSubmit={handleSubmit}>
              {metadata?.publicLabel && (
                <p className="text-sm text-gray-600 mb-4">Link: <strong>{metadata.publicLabel}</strong></p>
              )}
              <label className="block text-sm font-medium text-gray-700 mb-2">Instagram Reel URL</label>
              <input
                type="url"
                value={reelUrl}
                onChange={e => setReelUrl(e.target.value)}
                placeholder="https://www.instagram.com/reel/..."
                required
                className="w-full border border-gray-300 rounded-lg px-4 py-3 text-sm mb-4 focus:ring-2 focus:ring-blue-500 focus:border-blue-500"
              />
              <button type="submit"
                className="w-full bg-blue-600 text-white py-3 rounded-lg font-medium hover:bg-blue-700">
                Submit Reel
              </button>
            </form>
          )}

          {status === 'submitting' && <p className="text-gray-500">Submitting...</p>}

          {status === 'success' && (
            <div className="bg-green-50 border border-green-200 rounded-lg p-4">
              <p className="text-green-700 text-sm">{message}</p>
            </div>
          )}

          {status === 'error' && (
            <div className="bg-red-50 border border-red-200 rounded-lg p-4">
              <p className="text-red-700 text-sm">{message}</p>
              <button onClick={() => setStatus('ready')} className="mt-2 text-sm text-blue-600 hover:underline">Try again</button>
            </div>
          )}
        </div>
      </div>
    </div>
  )
}
