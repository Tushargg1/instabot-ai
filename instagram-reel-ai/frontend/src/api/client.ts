import type {
  AuthResponse,
  DashboardMetrics,
  InstagramStatus,
  ManualAction,
  PageResponse,
  ProcessingJob,
  ReelDetail,
  ReelListItem,
  ReelResource,
  SubmissionMetadata,
  SubmissionToken,
  WorkerInstance,
} from '../types'

const API_BASE = '/api'

class ApiClient {
  private token: string | null = localStorage.getItem('token')

  setToken(token: string | null) {
    this.token = token
    if (token) localStorage.setItem('token', token)
    else localStorage.removeItem('token')
  }

  getToken(): string | null {
    return this.token
  }

  private async execute<T>(path: string, options: RequestInit, authenticated: boolean): Promise<T> {
    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      ...((options.headers as Record<string, string>) || {}),
    }
    if (authenticated && this.token) headers.Authorization = `Bearer ${this.token}`

    const response = await fetch(`${API_BASE}${path}`, { ...options, headers })
    if (authenticated && response.status === 401) {
      this.setToken(null)
      window.location.href = '/'
      throw new Error('Unauthorized')
    }
    if (!response.ok) {
      let message = `Request failed (${response.status})`
      try {
        const body = await response.json() as { message?: string; error?: string }
        message = body.message || body.error || message
      } catch { /* Use the status fallback for non-JSON errors. */ }
      throw new Error(message)
    }
    if (response.status === 204) return undefined as T
    return response.json() as Promise<T>
  }

  private request<T>(path: string, options: RequestInit = {}): Promise<T> {
    return this.execute<T>(path, options, true)
  }

  private publicRequest<T>(path: string, options: RequestInit = {}): Promise<T> {
    return this.execute<T>(path, options, false)
  }

  // Auth
  login(username: string, password: string) {
    return this.request<AuthResponse>('/auth/login', {
      method: 'POST', body: JSON.stringify({ username, password }),
    })
  }

  demoLogin() {
    return this.request<AuthResponse>('/auth/demo-login', { method: 'POST' })
  }

  register(username: string, password: string, email?: string, displayName?: string) {
    return this.request<AuthResponse>('/auth/register', {
      method: 'POST', body: JSON.stringify({ username, password, email, displayName }),
    })
  }

  // Settings / Profile
  getProfile() {
    return this.request<Record<string, unknown>>('/settings/profile')
  }

  updateProfile(data: { email?: string; displayName?: string }) {
    return this.request<Record<string, unknown>>('/settings/profile', {
      method: 'PUT', body: JSON.stringify(data),
    })
  }

  getSettings() {
    return this.request<Record<string, string>>('/settings')
  }

  saveSettings(settings: Record<string, string>) {
    return this.request<Record<string, string>>('/settings', {
      method: 'PUT', body: JSON.stringify(settings),
    })
  }

  deleteSetting(key: string) {
    return this.request<void>(`/settings/${encodeURIComponent(key)}`, { method: 'DELETE' })
  }

  // Health
  health() {
    return this.request<{ status: string; timestamp: string }>('/health')
  }

  // Dashboard metrics
  getMetrics() {
    return this.request<DashboardMetrics>('/dashboard/metrics')
  }

  // Reels
  getReels(page = 0, size = 20, status?: string) {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (status) params.set('status', status)
    return this.request<PageResponse<ReelListItem>>(`/reels?${params}`)
  }

  getReel(id: string) {
    return this.request<ReelDetail>(`/reels/${id}`)
  }

  deleteReel(id: string) {
    return this.request<void>(`/reels/${id}`, { method: 'DELETE' })
  }

  searchReels(query: string, page = 0, size = 20) {
    const params = new URLSearchParams({ q: query, page: String(page), size: String(size) })
    return this.request<PageResponse<ReelListItem>>(`/reels/search?${params}`)
  }

  // Jobs
  getJobs(page = 0, size = 20, status?: string) {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (status) params.set('status', status)
    return this.request<PageResponse<ProcessingJob>>(`/jobs?${params}`)
  }

  getJob(id: string) {
    return this.request<ProcessingJob>(`/jobs/${id}`)
  }

  retryJob(id: string) {
    return this.request<ProcessingJob>(`/jobs/${id}/retry`, { method: 'POST' })
  }

  // Resources
  getResources(page = 0, size = 20) {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    return this.request<PageResponse<ReelResource>>(`/resources?${params}`)
  }

  refreshResource(id: string) {
    return this.request<ReelResource>(`/resources/${id}/refresh`, { method: 'POST' })
  }

  // Manual Actions
  getManualActions(page = 0, size = 20, status?: string) {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (status) params.set('status', status)
    return this.request<PageResponse<ManualAction>>(`/manual-actions?${params}`)
  }

  claimAction(id: string, leaseSeconds = 300) {
    return this.request<ManualAction>(`/manual-actions/${id}/claim`, {
      method: 'POST',
      body: JSON.stringify({ leaseSeconds }),
    })
  }

  completeAction(id: string, result?: string) {
    return this.request<ManualAction>(`/manual-actions/${id}/complete`, {
      method: 'POST',
      body: JSON.stringify({ result }),
    })
  }

  failAction(id: string, reason: string) {
    return this.request<ManualAction>(`/manual-actions/${id}/fail`, {
      method: 'POST',
      body: JSON.stringify({ reason }),
    })
  }

  // Workers
  getActiveWorkers() {
    return this.request<WorkerInstance[]>('/workers/active')
  }

  getStaleWorkers() {
    return this.request<WorkerInstance[]>('/workers/stale')
  }

  // Submission Tokens
  createSubmissionToken(expiresInSeconds?: number, publicLabel?: string) {
    return this.request<SubmissionToken>('/public-submission-tokens', {
      method: 'POST',
      body: JSON.stringify({ expiresInSeconds, publicLabel }),
    })
  }

  // Public (no auth) methods
  getSubmissionMetadata(token: string) {
    return this.publicRequest<SubmissionMetadata>(`/public/submissions/${token}`)
  }

  submitReelPublic(token: string, reelUrl: string) {
    return this.publicRequest<{ status: string; reelId: string; canonicalKey: string }>(
      `/public/submissions/${token}/submit`,
      { method: 'POST', body: JSON.stringify({ reelUrl }) }
    )
  }

  // Instagram
  getInstagramStatus() {
    return this.request<InstagramStatus>('/instagram/status')
  }

  startInstagramOAuth() {
    return this.request<{ authorizationUrl: string }>('/instagram/oauth/start', { method: 'POST' })
  }

  disconnectInstagram() {
    return this.request<void>('/instagram/connection', { method: 'DELETE' })
  }

  testWebhook() {
    return this.request<Record<string, unknown>>('/instagram/test', { method: 'POST' })
  }

  // DM Auto-Reply Rules
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getDmRules(): Promise<any[]> {
    return this.request<any[]>('/dm-rules')
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  createDmRule(data: any): Promise<any> {
    return this.request<Record<string, unknown>>('/dm-rules', {
      method: 'POST', body: JSON.stringify(data),
    })
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  updateDmRule(id: string, data: any): Promise<any> {
    return this.request<Record<string, unknown>>(`/dm-rules/${id}`, {
      method: 'PUT', body: JSON.stringify(data),
    })
  }

  deleteDmRule(id: string) {
    return this.request<void>(`/dm-rules/${id}`, { method: 'DELETE' })
  }

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  toggleDmRule(id: string, enabled: boolean): Promise<any> {
    return this.request<Record<string, unknown>>(`/dm-rules/${id}/toggle`, {
      method: 'PATCH', body: JSON.stringify({ enabled }),
    })
  }

  // DM Inbox
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  getDmInbox(page = 0, size = 50, senderId?: string): Promise<any> {
    const params = new URLSearchParams({ page: String(page), size: String(size) })
    if (senderId) params.set('senderId', senderId)
    return this.request<Record<string, unknown>>(`/dm-inbox?${params}`)
  }
}

export const apiClient = new ApiClient()
