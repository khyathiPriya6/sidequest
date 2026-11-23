import { ApiError, apiRequest, isRecord } from '../../lib/api'
import { getCsrfToken } from '../auth/authApi'

export const reportReasons = [
  'DANGEROUS_ACT', 'SELF_HARM', 'ILLEGAL', 'VIOLENCE_HARASSMENT',
  'HATE', 'EXPLOITATION', 'FRAUD_SPAM', 'PRIVACY', 'OTHER',
] as const
export type ReportReason = typeof reportReasons[number]
export type ModerationAction = 'APPROVE' | 'REJECT' | 'HIDE'
export type ModerationState = 'UNREVIEWED' | 'PENDING_REVIEW' | 'APPROVED' | 'REJECTED' | 'UNDER_REVIEW' | 'HIDDEN'
export type ReviewItem = {
  questId: string; title: string; description: string | null; questStatus: string; state: ModerationState
  reasonCode: string | null; version: number; reports: number
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
