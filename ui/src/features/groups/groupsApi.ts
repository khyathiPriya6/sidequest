import { apiRequest, ApiError, isRecord } from '../../lib/api'
import { getCsrfToken } from '../auth/authApi'

export type Page<T> = { content: T[]; page: number; size: number; totalElements: number;
  totalPages: number; hasNext: boolean; hasPrevious: boolean }
export type Team = { id: string; name: string; state: 'ACTIVE' | 'ARCHIVED'; role: 'OWNER' | 'MEMBER';
  version: number; members: number }
export type Invitation = { id: string; teamId: string; teamName: string; sender: string;
  recipient: string; status: string; version: number }
export type Member = { id: string; username: string; role: 'OWNER' | 'MEMBER' }
export type Challenge = { id: string; questId: string; title: string; targetCompletions: number;
  completed: number; earnedXp: number; createdAt: string; endsAt: string; status: string }
export type Score = { username: string; earnedXp: number; completed: number; rank: number }
const fail = () => new ApiError(502, 'invalid_response', 'The team service returned invalid data. Reload.')
const uuid = (v: unknown): v is string => typeof v === 'string'
  && /^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(v)
const nonneg = (v: unknown): v is number => typeof v === 'number' && Number.isSafeInteger(v) && v >= 0
const team = (v: unknown): v is Team => isRecord(v) && uuid(v.id) && typeof v.name === 'string'
  && ['ACTIVE', 'ARCHIVED'].includes(String(v.state)) && ['OWNER', 'MEMBER'].includes(String(v.role))
  && nonneg(v.version) && nonneg(v.members)
const invitation = (v: unknown): v is Invitation => isRecord(v) && uuid(v.id) && uuid(v.teamId)
  && typeof v.teamName === 'string' && typeof v.sender === 'string'
  && typeof v.recipient === 'string' && typeof v.status === 'string' && nonneg(v.version)
const member = (v: unknown): v is Member => isRecord(v) && uuid(v.id)
  && typeof v.username === 'string' && ['OWNER', 'MEMBER'].includes(String(v.role))
const challenge = (v: unknown): v is Challenge => isRecord(v) && uuid(v.id) && uuid(v.questId)
  && typeof v.title === 'string' && nonneg(v.targetCompletions) && nonneg(v.completed)
  && nonneg(v.earnedXp) && typeof v.createdAt === 'string' && typeof v.endsAt === 'string'
  && typeof v.status === 'string'
const score = (v: unknown): v is Score => isRecord(v) && typeof v.username === 'string'
  && nonneg(v.earnedXp) && nonneg(v.completed) && nonneg(v.rank)
const pageOf = <T>(value: unknown, guard: (x: unknown) => x is T): value is Page<T> =>
  isRecord(value) && Array.isArray(value.content) && value.content.every(guard)
    && nonneg(value.page) && nonneg(value.size) && nonneg(value.totalElements)
    && nonneg(value.totalPages) && typeof value.hasNext === 'boolean'
    && typeof value.hasPrevious === 'boolean'
async function read<T>(path: string, check: (x: unknown) => x is T, signal?: AbortSignal): Promise<T> {
  const value: unknown = await apiRequest(path, { signal })
  if (!check(value)) throw fail()
  return value
}
async function mutation<T>(path: string, body: object | null,
  guard: (x: unknown) => x is T): Promise<T> {
  const token = (await getCsrfToken()).csrfToken
  const value: unknown = await apiRequest(path, {
    method: 'POST', headers: { 'X-CSRF-TOKEN': token, 'Content-Type': 'application/json' },
    body: body === null ? '{}' : JSON.stringify(body),
  })
  if (!guard(value)) throw fail()
  return value
}
export const listTeams = (page = 0, signal?: AbortSignal) =>
  read('/api/v1/me/teams?page=' + page, (x): x is Page<Team> => pageOf(x, team), signal)
export const createTeam = (name: string) => mutation('/api/v1/teams', { name }, team)
export const getTeam = (id: string, signal?: AbortSignal) =>
  read('/api/v1/teams/' + encodeURIComponent(id), team, signal)
export const roster = (id: string, page=0, signal?: AbortSignal) =>
  read('/api/v1/teams/' + encodeURIComponent(id) + '/members?page=' + page,
    (x): x is Page<Member> => pageOf(x, member), signal)
export const inviteMember = (teamId: string, username: string) =>
  mutation('/api/v1/teams/' + encodeURIComponent(teamId) + '/invitations',
    { username }, invitation)
export const invitations = (page=0, signal?: AbortSignal) =>
  read('/api/v1/me/team-invitations?page=' + page,
    (x): x is Page<Invitation> => pageOf(x, invitation), signal)
export const decideInvite = (id: string, action: 'accept'|'reject', expectedVersion: number) =>
  mutation('/api/v1/me/team-invitations/' + encodeURIComponent(id) + '/' + action,
    { expectedVersion }, invitation)
export const leaveTeam = (id: string) =>
  mutation('/api/v1/teams/' + encodeURIComponent(id) + '/leave', null, team)
export const archiveTeam = (id: string) =>
  mutation('/api/v1/teams/' + encodeURIComponent(id) + '/archive', null, team)
export const removeMember = async (id: string, userId: string) => {
  const token=(await getCsrfToken()).csrfToken
  await apiRequest('/api/v1/teams/' + encodeURIComponent(id) + '/members/' +
    encodeURIComponent(userId) + '/remove',
    { method: 'POST', headers: { 'X-CSRF-TOKEN': token } })
}
export const addChallenge = (teamId: string, questId: string, days: number, targetCompletions: number) =>
  mutation('/api/v1/teams/' + encodeURIComponent(teamId) + '/challenges',
    { questId, days, targetCompletions }, challenge)
export const challenges = (teamId: string, page=0, signal?: AbortSignal) =>
  read('/api/v1/teams/' + encodeURIComponent(teamId) + '/challenges?page=' + page,
    (x): x is Page<Challenge> => pageOf(x, challenge), signal)
export const leaderboard = (teamId: string, page=0, signal?: AbortSignal) =>
  read('/api/v1/teams/' + encodeURIComponent(teamId) + '/leaderboard?page=' + page,
    (x): x is Page<Score> => pageOf(x, score), signal)
