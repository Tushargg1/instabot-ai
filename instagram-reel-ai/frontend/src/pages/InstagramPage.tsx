import { useMutation, useQuery, useQueryClient } from '@tanstack/react-query'
import { useSearchParams } from 'react-router-dom'
import { apiClient } from '../api/client'

export default function InstagramPage() {
  const queryClient = useQueryClient()
  const [searchParams, setSearchParams] = useSearchParams()
  const oauthResult = searchParams.get('oauth')
  const oauthMessage = searchParams.get('message')

  const { data: status, isLoading, error } = useQuery({
    queryKey: ['instagram-status'],
    queryFn: () => apiClient.getInstagramStatus(),
  })

  const connectMutation = useMutation({
    mutationFn: () => apiClient.startInstagramOAuth(),
    onSuccess: ({ authorizationUrl }) => window.location.assign(authorizationUrl),
  })

  const disconnectMutation = useMutation({
    mutationFn: () => apiClient.disconnectInstagram(),
    onSuccess: () => queryClient.invalidateQueries({ queryKey: ['instagram-status'] }),
  })

  const testMutation = useMutation({
    mutationFn: () => apiClient.testWebhook(),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['reels'] })
      queryClient.invalidateQueries({ queryKey: ['jobs'] })
    },
  })

  const clearOAuthMessage = () => {
    searchParams.delete('oauth')
    searchParams.delete('message')
    setSearchParams(searchParams, { replace: true })
  }

  return (
    <div className="max-w-3xl">
      <h2 className="text-2xl font-bold text-gray-900 mb-2">Main Instagram Gateway</h2>
      <p className="text-gray-500 mb-6">Admin-only connection for the single account users send Reels to.</p>

      {oauthResult && (
        <div className={`mb-6 rounded-lg border p-4 ${oauthResult === 'success' ? 'border-green-200 bg-green-50 text-green-700' : 'border-red-200 bg-red-50 text-red-700'}`}>
          <div className="flex items-start justify-between gap-4">
            <p className="text-sm font-medium">
              {oauthResult === 'success' ? '✓ Instagram connected successfully.' : `✗ Connection failed${oauthMessage ? `: ${oauthMessage}` : '.'}`}
            </p>
            <button onClick={clearOAuthMessage} className="text-xs underline">Dismiss</button>
          </div>
        </div>
      )}

      <div className="bg-white rounded-xl border border-gray-200 p-6 mb-6">
        <h3 className="text-lg font-semibold text-gray-900 mb-4">Connection Status</h3>
        {isLoading ? (
          <p className="text-gray-500">Loading...</p>
        ) : error ? (
          <p className="text-red-600 text-sm">Could not load Instagram status.</p>
        ) : status?.connected ? (
          <div>
            <div className="flex items-center gap-4 mb-5">
              {status.profilePictureUrl ? (
                <img src={status.profilePictureUrl} alt="Instagram profile" className="h-14 w-14 rounded-full object-cover" />
              ) : (
                <div className="h-14 w-14 rounded-full bg-pink-100 flex items-center justify-center text-2xl">📷</div>
              )}
              <div>
                <p className="font-semibold text-gray-900">{status.displayName || status.username || 'Instagram account'}</p>
                {status.username && <p className="text-sm text-gray-500">@{status.username}</p>}
                <p className="text-xs text-green-600 mt-1">● Connected</p>
              </div>
            </div>
            <div className="space-y-2 text-sm border-t border-gray-100 pt-4">
              <div className="flex justify-between gap-4"><span className="text-gray-500">Account ID</span><span className="font-mono text-gray-800">{status.accountId}</span></div>
              <div className="flex justify-between gap-4"><span className="text-gray-500">Token expires</span><span className="text-gray-800">{status.tokenExpiresAt ? new Date(status.tokenExpiresAt).toLocaleDateString() : 'Unknown'}</span></div>
              <div className="flex justify-between gap-4"><span className="text-gray-500">Permissions</span><span className="text-right text-gray-800">{status.scopes.join(', ')}</span></div>
            </div>
            <div className="flex gap-3 mt-5">
              <button onClick={() => connectMutation.mutate()} disabled={connectMutation.isPending}
                className="px-4 py-2 border border-gray-300 rounded-lg text-sm hover:bg-gray-50 disabled:opacity-50">
                {connectMutation.isPending ? 'Opening Meta...' : 'Reconnect'}
              </button>
              <button onClick={() => disconnectMutation.mutate()} disabled={disconnectMutation.isPending}
                className="px-4 py-2 border border-red-200 text-red-600 rounded-lg text-sm hover:bg-red-50 disabled:opacity-50">
                {disconnectMutation.isPending ? 'Disconnecting...' : 'Disconnect'}
              </button>
            </div>
          </div>
        ) : (
          <div>
            <div className="rounded-lg bg-gradient-to-r from-pink-50 to-purple-50 border border-pink-100 p-5 mb-4">
                <p className="font-semibold text-gray-900 mb-2">Authorize the Main Instagram account</p>
              <p className="text-sm text-gray-600 mb-4">Sign in with the Professional account that users will DM. End users never connect an account to this dashboard, and this app never receives the account password.</p>
              <button onClick={() => connectMutation.mutate()} disabled={!status?.available || connectMutation.isPending}
                className="px-5 py-2.5 bg-gradient-to-r from-purple-600 to-pink-600 text-white rounded-lg font-medium disabled:opacity-50 disabled:cursor-not-allowed">
                {connectMutation.isPending ? 'Opening Meta...' : 'Connect Main Instagram Account'}
              </button>
            </div>
            {!status?.available && (
              <p className="text-sm text-amber-700 bg-amber-50 border border-amber-200 rounded-lg p-3">Set the deployment’s Meta App ID, App Secret, and OAuth redirect URI to enable the Main account connection.</p>
            )}
          </div>
        )}
        {connectMutation.isError && <p className="text-red-600 text-sm mt-3">{(connectMutation.error as Error).message}</p>}
        {disconnectMutation.isError && <p className="text-red-600 text-sm mt-3">{(disconnectMutation.error as Error).message}</p>}
      </div>

      <div className="bg-blue-50 rounded-xl border border-blue-200 p-5 mb-6">
        <h3 className="font-semibold text-blue-900 mb-2">How normal users participate</h3>
        <p className="text-sm text-blue-800 mb-3">
          Users can send or share a Reel to the Main account from a normal personal Instagram account. They do not log into this dashboard, provide a token, or switch their own account to Professional.
        </p>
        <ul className="text-sm text-blue-800 space-y-1">
          <li>• Only the operator-owned Main account must be Business or Creator.</li>
          <li>• Each sender becomes a request linked to the canonical Reel.</li>
          <li>• The Reel is processed once and the result is delivered to every requester.</li>
          <li>• Worker accounts, when added, must use only supported Meta APIs and legitimate actions.</li>
        </ul>
      </div>

      {status?.testMode && (
        <div className="bg-white rounded-xl border border-gray-200 p-6">
          <h3 className="text-lg font-semibold text-gray-900 mb-2">Development Test</h3>
          <p className="text-gray-500 text-sm mb-4">Send the same canonical demo Reel repeatedly to verify one AI job with multiple requester records.</p>
          <button onClick={() => testMutation.mutate()} disabled={testMutation.isPending}
            className="px-4 py-2 bg-blue-600 text-white rounded-lg hover:bg-blue-700 disabled:opacity-50">
            {testMutation.isPending ? 'Sending...' : 'Send Test Event'}
          </button>
          {testMutation.isSuccess && <p className="text-green-600 text-sm mt-2">✓ Test event sent successfully.</p>}
          {testMutation.isError && <p className="text-red-600 text-sm mt-2">✗ {(testMutation.error as Error).message}</p>}
        </div>
      )}
    </div>
  )
}
