import { ApiError, apiRequest, isRecord } from '../../lib/api'
import { getCsrfToken } from '../auth/authApi'

export const reportReasons = [
  'DANGEROUS_ACT', 'SELF_HARM', 'ILLEGAL', 'VIOLENCE_HARASSMENT',
  'HATE', 'EXPLOITATION', 'FRAUD_SPAM', 'PRIVACY', 'OTHER',
] as const
export type ReportReason = typeof reportReasons[number]
export type ModerationAction = 'APPROVE' | 'REJECT' | 'HIDE' | 'DISMISS_REPORTS'
export type ModerationState = 'UNREVIEWED' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'UNDER_REVIEW' | 'HIDDEN'
export type ReviewItem = {
  questId: string; title: string; description: string | null; questStatus: string; state: ModerationState
  reasonCode: string | null; version: number; reports: number; appealReason: string | null
}
export type ReviewPage = {
  content: ReviewItem[]; page: number; size: number; totalElements: number
  totalPages: number; hasNext: boolean; hasPrevious: boolean
}
const fail = () => new ApiError(502, 'invalid_response', 'Review data could not be verified. Reload before taking action.')
function item(v: unknown): v is ReviewItem {
  return isRecord(v) && typeof v.questId === 'string' && typeof v.title === 'string'
    && (v.description === null || typeof v.description === 'string') && typeof v.questStatus === 'string' && typeof v.state === 'string'
    && (v.reasonCode === null || typeof v.reasonCode === 'string')
    && typeof v.version === 'number' && Number.isSafeInteger(v.version) && v.version >= 0
    && typeof v.reports === 'number' && Number.isSafeInteger(v.reports) && v.reports >= 0
    && (v.appealReason === null || typeof v.appealReason === 'string')
}
export async function listReviews(page: number, signal?: AbortSignal): Promise<ReviewPage> {
  const data: unknown = await apiRequest('/api/v1/moderation/queue?page=' + page + '&size=20', { signal })
  if (!isRecord(data) || !Array.isArray(data.content) || !data.content.every(item)
    || typeof data.totalPages !== 'number' || typeof data.hasNext !== 'boolean'
    || typeof data.hasPrevious !== 'boolean' || typeof data.page !== 'number'
    || typeof data.size !== 'number' || typeof data.totalElements !== 'number') throw fail()
  return data as ReviewPage
}
export async function sendReport(questId: string, reason: ReportReason): Promise<void> {
  const { csrfToken } = await getCsrfToken()
  const result: unknown = await apiRequest('/api/v1/quests/' + encodeURIComponent(questId) + '/reports', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
    body: JSON.stringify({ reason }),
  })
  if (!isRecord(result) || typeof result.id !== 'string' || result.status !== 'RECEIVED') throw fail()
}
export async function decideReview(item: ReviewItem, action: ModerationAction, reasonCode: string): Promise<ReviewItem> {
  const { csrfToken } = await getCsrfToken()
  const result: unknown = await apiRequest('/api/v1/moderation/quests/' + encodeURIComponent(item.questId) + '/decisions', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
    body: JSON.stringify({ action, reasonCode, expectedVersion: item.version }),
  })
  if (!itemCheck(result)) throw fail()
  return result
}
function itemCheck(v: unknown): v is ReviewItem { return item(v) }

export type OwnerReviewStatus = { state: ModerationState; reasonCode: string | null; version: number }
const states: readonly string[] = ['UNREVIEWED', 'PENDING_REVIEW', 'APPROVED', 'REJECTED', 'UNDER_REVIEW', 'HIDDEN']
function parseOwnerReview(value: unknown): OwnerReviewStatus {
  if (!isRecord(value) || typeof value.state !== 'string' || !states.includes(value.state)
      || !(value.reasonCode === null || typeof value.reasonCode === 'string')
      || typeof value.version !== 'number' || !Number.isSafeInteger(value.version) || value.version < 0) throw fail()
  return { state: value.state as ModerationState, reasonCode: value.reasonCode, version: value.version }
}
export function getOwnerReview(id: string, signal?: AbortSignal): Promise<OwnerReviewStatus> {
  return apiRequest<unknown>('/api/v1/me/quests/' + encodeURIComponent(id) + '/moderation', { signal })
    .then(parseOwnerReview)
}
export async function submitQuestReview(id: string, etag: string): Promise<OwnerReviewStatus> {
  const { csrfToken } = await getCsrfToken()
  const result = await apiRequest<unknown>('/api/v1/quests/' + encodeURIComponent(id) + '/submit-review', {
    method: 'POST',
    headers: { 'X-CSRF-TOKEN': csrfToken, 'If-Match': etag },
  })
  return parseOwnerReview(result)
}
export function reviewLabel(status: OwnerReviewStatus): string {
  switch (status.state) {
    case 'UNREVIEWED': return 'Not submitted for safety review'
    case 'PENDING_REVIEW': return 'Awaiting safety review'
    case 'UNDER_REVIEW': return 'Under safety review'
    case 'APPROVED': return 'Approved for publishing'
    case 'REJECTED': return 'Rejected by a moderator'
    case 'HIDDEN': return 'Hidden for safety review'
  }
}

export async function appealQuest(id: string, status: OwnerReviewStatus, reason: string): Promise<void> {
  if (status.state !== 'REJECTED' || reason.trim().length < 10 || reason.trim().length > 1000) throw fail()
  const { csrfToken } = await getCsrfToken()
  const response = await apiRequest<unknown>('/api/v1/me/quests/' + encodeURIComponent(id) + '/appeals', {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
    body: JSON.stringify({ reason: reason.trim(), expectedVersion: status.version }),
  })
  if (!isRecord(response) || typeof response.id !== 'string' || response.status !== 'PENDING') throw fail()
}

export type LegacyQuest = {
  questId: string; title: string; description: string | null; publishedAt: string | null
}
export type LegacyPage = {
  content: LegacyQuest[]; page: number; size: number; totalElements: number
  totalPages: number; hasNext: boolean; hasPrevious: boolean
}
export async function listLegacyQuests(page: number, signal?: AbortSignal): Promise<LegacyPage> {
  const response: unknown = await apiRequest('/api/v1/moderation/legacy?page=' + page + '&size=20', { signal })
  if (!isRecord(response) || !Array.isArray(response.content)
      || !response.content.every((quest: unknown) => isRecord(quest)
        && typeof quest.questId === 'string' && typeof quest.title === 'string'
        && (quest.description === null || typeof quest.description === 'string')
        && (quest.publishedAt === null || typeof quest.publishedAt === 'string'))
      || !Number.isSafeInteger(response.page) || !Number.isSafeInteger(response.size)
      || !Number.isSafeInteger(response.totalElements) || !Number.isSafeInteger(response.totalPages)
      || typeof response.hasNext !== 'boolean' || typeof response.hasPrevious !== 'boolean') throw fail()
  return response as LegacyPage
}
export async function intakeLegacyQuest(questId: string): Promise<ReviewItem> {
  const { csrfToken } = await getCsrfToken()
  const result: unknown = await apiRequest('/api/v1/moderation/legacy/' +
    encodeURIComponent(questId) + '/intake', {
    method: 'POST', headers: { 'X-CSRF-TOKEN': csrfToken },
  })
  if (!item(result)) throw fail()
  return result
}
