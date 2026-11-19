import { useState } from 'react'
import { useMutation } from '@tanstack/react-query'
import { apiClient } from '../api/client'
import { Copy, Check } from 'lucide-react'

export default function SubmissionLinksPage() {
  const [ttl, setTtl] = useState(900)
  const [label, setLabel] = useState('')
  const [tokens, setTokens] = useState<{ token: string; expiresAt: string; label: string }[]>([])
  const [copied, setCopied] = useState<string | null>(null)

  const createMutation = useMutation({
    mutationFn: () => apiClient.createSubmissionToken(ttl, label || undefined),
    onSuccess: (data) => {
      setTokens(prev => [{ token: data.token, expiresAt: data.expiresAt, label: label || 'Untitled' }, ...prev])
      setLabel('')
    },
  })

  const getLink = (token: string) => `${window.location.origin}/submit/${token}`

  const copyLink = (token: string) => {
    navigator.clipboard.writeText(getLink(token))
    setCopied(token)
    setTimeout(() => setCopied(null), 2000)
  }

  return (
    <div>
      <h2 className="text-2xl font-bold text-gray-900 mb-6">Submission Links</h2>
      <p className="text-gray-600 mb-6">
        Generate one-time links that allow anyone to submit a Reel URL without logging in.
      </p>

      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-8">
        <h3 className="font-semibold text-gray-900 mb-4">Create New Link</h3>
        <div className="grid grid-cols-1 md:grid-cols-3 gap-4">
          <div>
            <label className="block text-sm text-gray-600 mb-1">Label</label>
            <input type="text" value={label} onChange={e => setLabel(e.target.value)}
              placeholder="e.g. For John" className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm" />
          </div>
          <div>
            <label className="block text-sm text-gray-600 mb-1">Expires in (seconds)</label>
            <select value={ttl} onChange={e => setTtl(Number(e.target.value))}
              className="w-full border border-gray-300 rounded-lg px-3 py-2 text-sm">
              <option value={300}>5 minutes</option>
              <option value={900}>15 minutes</option>
              <option value={3600}>1 hour</option>
              <option value={86400}>24 hours</option>
            </select>
          </div>
          <div className="flex items-end">
            <button onClick={() => createMutation.mutate()}
              disabled={createMutation.isPending}
              className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50 text-sm">
              {createMutation.isPending ? 'Creating...' : 'Generate Link'}
            </button>
          </div>
        </div>
      </div>

      {tokens.length > 0 && (
        <div className="bg-white rounded-xl border border-gray-200 overflow-hidden">
          <table className="w-full">
            <thead className="bg-gray-50 border-b border-gray-200">
              <tr>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Label</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Link</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Expires</th>
                <th className="px-4 py-3 text-left text-sm font-medium text-gray-500">Copy</th>
              </tr>
            </thead>
            <tbody className="divide-y divide-gray-100">
              {tokens.map(t => (
                <tr key={t.token}>
                  <td className="px-4 py-3 text-sm text-gray-900">{t.label}</td>
                  <td className="px-4 py-3 text-sm text-gray-600 font-mono max-w-[300px] truncate">{getLink(t.token)}</td>
                  <td className="px-4 py-3 text-sm text-gray-500">{new Date(t.expiresAt).toLocaleString()}</td>
                  <td className="px-4 py-3">
                    <button onClick={() => copyLink(t.token)} className="p-1 text-gray-500 hover:text-gray-800">
                      {copied === t.token ? <Check size={16} className="text-green-600" /> : <Copy size={16} />}
                    </button>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>
      )}
    </div>
  )
}
