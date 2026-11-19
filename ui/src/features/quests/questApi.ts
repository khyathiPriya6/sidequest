import { ApiError, apiResponse, isRecord } from '../../lib/api'
export type QuestCategory = 'ADVENTURE' | 'CREATIVITY' | 'FITNESS' | 'LEARNING' | 'FOOD' | 'SOCIAL'
export type QuestDifficulty = 'EASY' | 'MEDIUM' | 'HARD'
export type QuestStatus = 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
const questCategories: readonly string[] = ['ADVENTURE', 'CREATIVITY', 'FITNESS', 'LEARNING', 'FOOD', 'SOCIAL']
const questDifficulties: readonly string[] = ['EASY', 'MEDIUM', 'HARD']

export interface Quest {
  id: string
  creatorId: string
  title: string
  description: string | null
  category: QuestCategory | null
  difficulty: QuestDifficulty | null
  rewardXp: number | null
  status: 'DRAFT' | 'PUBLISHED' | 'ARCHIVED'
  createdAt: string
  updatedAt: string
  publishedAt: string | null
  archivedAt: string | null
}

export interface QuestPage {
  content: Quest[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}

export class QuestApiError extends ApiError {
  constructor(status: number, message: string, code = 'request_failed', fieldErrors: import('../../lib/api').ProblemFieldError[] = []) {
    super(status, code, message, fieldErrors)
  }
}
async function questResponse(path: string, init?: RequestInit) {
  try { return await apiResponse(path, init) } catch (error) {
    if (error instanceof ApiError) throw new QuestApiError(error.status, error.message, error.code, error.fieldErrors)
    throw error
  }
}
export function requireEtag(value: string | null): string {
  if (!value || value.length > 21 || !/^"(?:0|[1-9][0-9]*)"$/.test(value) || BigInt(value.slice(1, -1)) > 9223372036854775807n) {
    throw new QuestApiError(502, 'The service did not return a valid version. Reload the quest before making another change.', 'invalid_etag')
  }
  return value
}

function isQuest(value: unknown): value is Quest {
  if (!isRecord(value)) return false
  return typeof value.id === 'string'
    && typeof value.creatorId === 'string'
    && typeof value.title === 'string'
    && (typeof value.description === 'string' || value.description === null)
    && (value.category === null || (typeof value.category === 'string' && questCategories.includes(value.category)))
    && (value.difficulty === null || (typeof value.difficulty === 'string' && questDifficulties.includes(value.difficulty)))
    && (typeof value.rewardXp === 'number' && Number.isInteger(value.rewardXp) || value.rewardXp === null)
    && (value.status === 'DRAFT' || value.status === 'PUBLISHED' || value.status === 'ARCHIVED')
    && typeof value.createdAt === 'string'
    && typeof value.updatedAt === 'string'
    && (typeof value.publishedAt === 'string' || value.publishedAt === null)
    && (typeof value.archivedAt === 'string' || value.archivedAt === null)
}

function isQuestPage(value: unknown): value is QuestPage {
  return isRecord(value)
    && Array.isArray(value.content)
    && value.content.every(isQuest)
    && Number.isInteger(value.page)
    && Number.isInteger(value.size)
    && Number.isInteger(value.totalElements)
    && Number.isInteger(value.totalPages)
    && typeof value.hasNext === 'boolean'
    && typeof value.hasPrevious === 'boolean'
}

async function request<T>(
  path: string,
  signal: AbortSignal,
  isExpectedShape: (value: unknown) => value is T,
): Promise<T> {
  const { body } = await questResponse(path, { signal })
  if (!isExpectedShape(body)) {
    throw new QuestApiError(502, 'The quest service returned an unexpected response.', 'invalid_response')
  }
  return body
}

export function getPublishedQuests(search: string, signal: AbortSignal): Promise<QuestPage> {
  const query = new URLSearchParams(search)
  const parameters = new URLSearchParams()
  for (const name of ['q', 'category', 'difficulty', 'page', 'size']) {
    const value = query.get(name)
    if (value !== null) parameters.set(name, value)
  }
  return request(`/api/v1/quests?${parameters}`, signal, isQuestPage)
}

export function getQuest(id: string, signal: AbortSignal): Promise<Quest> {
  return request(`/api/v1/quests/${encodeURIComponent(id)}`, signal, isQuest)
}

export function getMyQuests(status: QuestStatus | '', page: number, signal: AbortSignal): Promise<QuestPage> {
  const parameters = new URLSearchParams({ page: String(page), size: '20' })
  if (status) parameters.set('status', status)
  return request(`/api/v1/me/quests?${parameters}`, signal, isQuestPage)
}

export interface QuestMutationResult {
  quest: Quest
  etag: string
}

export interface QuestDraftInput {
  title?: string
  description?: string | null
  category?: QuestCategory | null
  difficulty?: QuestDifficulty | null
  rewardXp?: number | null
}

async function versioned(path: string, init?: RequestInit): Promise<QuestMutationResult> {
  const { body, response } = await questResponse(path, init)
  if (!isQuest(body)) throw new QuestApiError(502, 'The quest service returned an unexpected response. Reload before changing it.', 'invalid_response')
  return { quest: body, etag: requireEtag(response.headers.get('ETag')) }
}
export function getOwnedQuest(id: string, signal: AbortSignal) {
  return versioned(`/api/v1/me/quests/${encodeURIComponent(id)}`, { signal })
}
export function createQuestDraft(input: QuestDraftInput, csrfToken: string) {
  return versioned('/api/v1/quests', { method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken }, body: JSON.stringify(input) })
}
export function updateQuestDraft(id: string, input: QuestDraftInput, etag: string, csrfToken: string) {
  return versioned(`/api/v1/quests/${encodeURIComponent(id)}`, { method: 'PATCH',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken, 'If-Match': requireEtag(etag) }, body: JSON.stringify(input) })
}
function transition(id: string, action: string, etag: string, csrfToken: string) {
  return versioned(`/api/v1/quests/${encodeURIComponent(id)}/${action}`, { method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken, 'If-Match': requireEtag(etag) }, body: '{}' })
}
export function publishQuest(id: string, etag: string, csrfToken: string) { return transition(id, 'publish', etag, csrfToken) }
export function archiveQuest(id: string, etag: string, csrfToken: string) { return transition(id, 'archive', etag, csrfToken) }

export function displayEnum(value: string | null): string {
  if (value === null) return 'Not specified'
  return value.charAt(0) + value.slice(1).toLowerCase()
}
