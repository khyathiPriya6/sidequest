import { ApiError, apiRequest, isRecord } from '../../lib/api'
import type { Page } from './progressApi'

export type Period = 'all' | 'weekly' | 'monthly'
export interface RankingEntry { username: string; rank: number; earnedXp: number }
export interface Ranking { period: Period; player: RankingEntry | null }
export interface Leaderboard extends Page<RankingEntry> { period: Period }
export interface Streak { currentStreak: number; longestStreak: number; lastCompletionDate: string | null; timezone: 'UTC' }
const integer = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0
const entry = (v: unknown): v is RankingEntry => isRecord(v) && typeof v.username === 'string'
  && integer(v.rank) && v.rank > 0 && integer(v.earnedXp) && v.earnedXp > 0
const invalid = () => new ApiError(502, 'invalid_response', 'Unable to verify ranking or streak information. Please retry.')
export async function getLeaderboard(period: Period, page = 0, signal?: AbortSignal): Promise<Leaderboard> {
  const v: unknown = await apiRequest(`/api/v1/leaderboard?period=${period}&page=${page}&size=20`, { signal })
  if (!isRecord(v) || v.period !== period || !Array.isArray(v.content) || !v.content.every(entry)
    || !integer(v.page) || v.page !== page || !integer(v.size) || v.size !== 20
    || !integer(v.totalElements) || !integer(v.totalPages)
    || typeof v.hasNext !== 'boolean' || typeof v.hasPrevious !== 'boolean') throw invalid()
  return v as unknown as Leaderboard
}
export async function getRanking(period: Period = 'all', signal?: AbortSignal): Promise<Ranking> {
  const v: unknown = await apiRequest(`/api/v1/me/leaderboard-rank?period=${period}`, { signal })
  if (!isRecord(v) || v.period !== period || (v.player !== null && !entry(v.player))) throw invalid()
  return v as unknown as Ranking
}
export async function getStreak(signal?: AbortSignal): Promise<Streak> {
  const v: unknown = await apiRequest('/api/v1/me/streak', { signal })
  if (!isRecord(v) || !integer(v.currentStreak) || !integer(v.longestStreak) || v.currentStreak > v.longestStreak
    || v.timezone !== 'UTC' || !(v.lastCompletionDate === null || (typeof v.lastCompletionDate === 'string'
      && /^\d{4}-\d{2}-\d{2}$/.test(v.lastCompletionDate)))) throw invalid()
  return v as unknown as Streak
}
