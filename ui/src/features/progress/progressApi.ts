import { ApiError, apiRequest, isRecord } from '../../lib/api'
import { getCsrfToken } from '../auth/authApi'

export type ParticipationStatus = 'IN_PROGRESS' | 'COMPLETED'
export interface Participation {
  id: string
  questId: string
  status: ParticipationStatus
  rewardXp: number
  joinedAt: string
  completedAt: string | null
  awardedXp: number
  quest: { title: string; description: string; category: string; difficulty: string; status: string }
}
export interface Page<T> {
  content: T[]
  page: number
  size: number
  totalElements: number
  totalPages: number
  hasNext: boolean
  hasPrevious: boolean
}
export interface PlayerProfile {
  username: string; createdAt: string; totalXp: number; level: number
  xpIntoLevel: number; xpRemaining: number; progressPercent: number
  joinedQuests: number; activeQuests: number; completedQuests: number
  createdQuests: number; unlockedAchievementCount: number
}
export interface Achievement {
  id: string; name: string; description: string; progress: number; target: number
  unlocked: boolean; unlockedAt: string | null
}
export interface XpTransaction {
  id: string; participationId: string; questId: string; amount: number
  awardedAt: string; questTitle: string; category: string
  difficulty: string; questStatus: string
}
const invalid = () => new ApiError(502, 'invalid_response', 'The service returned unexpected progress information. Reload before taking another action.')
const number = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0
function participation(v: unknown): v is Participation {
  return isRecord(v) && typeof v.id === 'string' && typeof v.questId === 'string'
    && (v.status === 'IN_PROGRESS' || v.status === 'COMPLETED')
    && number(v.rewardXp) && v.rewardXp >= 1 && v.rewardXp <= 1000
    && number(v.awardedXp) && typeof v.joinedAt === 'string'
    && (v.completedAt === null || typeof v.completedAt === 'string')
    && isRecord(v.quest) && typeof v.quest.title === 'string'
    && typeof v.quest.description === 'string' && typeof v.quest.category === 'string'
    && typeof v.quest.difficulty === 'string' && typeof v.quest.status === 'string'
}
function page<T>(v: unknown, check: (v: unknown) => v is T): v is Page<T> {
  return isRecord(v) && Array.isArray(v.content) && v.content.every(check)
    && number(v.page) && number(v.size) && number(v.totalElements)
    && number(v.totalPages) && typeof v.hasNext === 'boolean'
    && typeof v.hasPrevious === 'boolean'
}
function profile(v: unknown): v is PlayerProfile {
  return isRecord(v) && typeof v.username === 'string' && typeof v.createdAt === 'string'
    && ['totalXp','level','xpIntoLevel','xpRemaining','joinedQuests','activeQuests','completedQuests','createdQuests','unlockedAchievementCount'].every(k => number(v[k]))
    && typeof v.progressPercent === 'number' && Number.isFinite(v.progressPercent)
    && v.progressPercent >= 0 && v.progressPercent <= 100
}
function achievement(v: unknown): v is Achievement {
  return isRecord(v) && typeof v.id === 'string' && typeof v.name === 'string'
    && typeof v.description === 'string' && number(v.progress) && number(v.target)
    && typeof v.unlocked === 'boolean' && (v.unlockedAt === null || typeof v.unlockedAt === 'string')
}
function xp(v: unknown): v is XpTransaction {
  return isRecord(v) && typeof v.id === 'string' && typeof v.participationId === 'string'
    && typeof v.questId === 'string' && number(v.amount) && typeof v.awardedAt === 'string'
    && typeof v.questTitle === 'string' && typeof v.category === 'string'
    && typeof v.difficulty === 'string' && typeof v.questStatus === 'string'
}
async function validated<T>(url: string, check: (v: unknown) => v is T, init?: RequestInit): Promise<T> {
  const response: unknown = await apiRequest(url, init)
  if (!check(response)) throw invalid()
  return response
}
async function mutation(path: string): Promise<Participation> {
  const csrf = (await getCsrfToken()).csrfToken
  return validated(path, participation, { method: 'POST', headers: { 'X-CSRF-TOKEN': csrf } })
}
export const joinQuest = (id: string) => mutation(`/api/v1/quests/${encodeURIComponent(id)}/join`)
export const completeParticipation = (id: string) => mutation(`/api/v1/participations/${encodeURIComponent(id)}/complete`)
export const getParticipation = (id: string, signal?: AbortSignal) =>
  validated(`/api/v1/me/participations/${encodeURIComponent(id)}`, participation, { signal })
export const getParticipations = (options: { status?: ParticipationStatus; questId?: string; page?: number; size?: number } = {}, signal?: AbortSignal) => {
  const params = new URLSearchParams({ page: String(options.page ?? 0), size: String(options.size ?? 20) })
  if (options.status) params.set('status', options.status)
  if (options.questId) params.set('questId', options.questId)
  return validated(`/api/v1/me/participations?${params}`, (v): v is Page<Participation> => page(v, participation), { signal })
}
export const getPlayerProfile = (signal?: AbortSignal) => validated('/api/v1/me/profile', profile, { signal })
export const getAchievements = (signal?: AbortSignal) =>
  validated('/api/v1/me/achievements', (v): v is { achievements: Achievement[] } =>
    isRecord(v) && Array.isArray(v.achievements) && v.achievements.every(achievement), { signal })
export const getXpHistory = (pageNumber = 0, signal?: AbortSignal) =>
  validated(`/api/v1/me/xp-transactions?page=${pageNumber}&size=20`,
    (v): v is Page<XpTransaction> => page(v, xp), { signal })
