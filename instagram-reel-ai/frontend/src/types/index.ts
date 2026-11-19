export interface ReelListItem {
  id: string
  title: string | null
  summaryExcerpt: string | null
  creatorUsername: string | null
  processingStatus: string
  topics: string[]
  savedAt: string
  createdAt: string
}

export interface ReelDetail {
  id: string
  sourceUrl: string | null
  externalMediaId: string | null
  creatorUsername: string | null
  caption: string | null
  processingStatus: string
  savedAt: string
  createdAt: string
  title: string | null
  summaryText: string | null
  keyPoints: string[]
  actionItems: string[]
  topics: string[]
  toolsOrProducts: string[]
  peopleOrCompanies: string[]
  claimsToVerify: string[]
  language: string | null
  confidence: number | null
  transcript: string | null
  durationSeconds: number | null
}

export interface ProcessingJob {
  id: string
  reelId: string
  jobType: string
  priority: number
  assignedWorker: string | null
  leaseOwner: string | null
  leaseExpiresAt: string | null
  heartbeatAt: string | null
  status: string
  stage: string | null
  attempts: number
  maxAttempts: number
  lastErrorCode: string | null
  lastErrorMessage: string | null
  retryable: boolean
  retryReason: string | null
  startedAt: string | null
  completedAt: string | null
  nextRetryAt: string | null
  createdAt: string
  updatedAt: string
}

export interface DashboardMetrics {
  totalReels: number
  totalJobs: number
  pendingJobs: number
  failedJobs: number
  totalResources: number
  availableResources: number
  totalRequests: number
  pendingActions: number
}

export interface ReelResource {
  id: string
  reelId: string
  resourceUrl: string | null
  resourceType: string | null
  status: string
  requiredAction: string | null
  actionPayload: string | null
  requestCount: number
  firstFoundAt: string | null
  lastVerifiedAt: string | null
  expiresAt: string | null
  createdAt: string
}

export interface ManualAction {
  id: string
  resourceId: string
  reelId: string
  actionType: string
  actionPayload: string | null
  status: string
  claimedByUserId: string | null
  claimedByUsername: string | null
  claimedAt: string | null
  leaseExpiresAt: string | null
  completedAt: string | null
  failedAt: string | null
  result: string | null
  failureReason: string | null
  createdAt: string
  updatedAt: string
}

export interface WorkerInstance {
  id: string
  instanceId: string
  hostName: string
  processId: number
  applicationName: string
  state: string
  startedAt: string
  registeredAt: string
  lastHeartbeatAt: string
  stoppedAt: string | null
}

export interface SubmissionToken {
  token: string
  expiresAt: string
}

export interface SubmissionMetadata {
  status: string
  publicLabel: string | null
  expiresAt: string
}

export interface AuthResponse {
  token: string
  username: string
  role: string
}

export interface PageResponse<T> {
  content: T[]
  totalElements: number
  totalPages: number
  number: number
  size: number
}

export interface InstagramStatus {
  available: boolean
  connected: boolean
  configured: boolean
  accountId: string
  username: string
  displayName: string
  profilePictureUrl: string
  tokenExpiresAt: string | null
  scopes: string[]
  webhookConfigured: boolean
  testMode: boolean
}
