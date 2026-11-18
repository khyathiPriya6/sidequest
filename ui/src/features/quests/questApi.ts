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

interface ProblemDetails {
  detail?: string
  title?: string
  code?: string
}

export class QuestApiError extends Error {
  constructor(readonly status: number, message: string, readonly code = 'request_failed') {
    super(message)
    this.name = 'QuestApiError'
  }
}

function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
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
  let response: Response
  try {
    response = await fetch(path, {
      signal,
      headers: { Accept: 'application/json, application/problem+json' },
      credentials: 'same-origin',
    })
  } catch (error) {
    if (signal.aborted) throw error
    throw new QuestApiError(0, 'Unable to reach the quest service. Check your connection and try again.', 'network_error')
  }

  let body: unknown
  try {
    body = await response.json()
  } catch {
    if (!response.ok) {
      throw new QuestApiError(response.status, 'The quest request could not be completed.', 'invalid_error_response')
    }
    throw new QuestApiError(502, 'The quest service returned an unreadable response.', 'invalid_response')
  }

  if (!response.ok) {
    const problem = isRecord(body) ? body as ProblemDetails : {}
    throw new QuestApiError(
      response.status,
      typeof problem.detail === 'string'
        ? problem.detail
        : typeof problem.title === 'string' ? problem.title : 'The quest request could not be completed.',
      typeof problem.code === 'string' ? problem.code : 'request_failed',
    )
  }
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

export function displayEnum(value: string | null): string {
  if (value === null) return 'Not specified'
  return value.charAt(0) + value.slice(1).toLowerCase()
}
