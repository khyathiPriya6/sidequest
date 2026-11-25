import { apiRequest, ApiError, isRecord } from '../../lib/api'
import { getCsrfToken } from '../auth/authApi'

export type Visibility = 'PRIVATE' | 'FRIENDS' | 'PUBLIC'
export type Period = 'all' | 'weekly' | 'monthly'
export type Settings = { profileId: string; visibility: Visibility; version: number }
export type Connection = { id: string; profileId: string; username: string; state: string; incoming: boolean; version: number }
export type Ranking = { username: string; rank: number; earnedXp: number }
export type FriendBoard = { period: Period; content: Ranking[] }

const invalid = () => new ApiError(502, 'invalid_response', 'The social service returned unexpected data. Reload and try again.')
const uuid = (s: unknown): s is string => typeof s === 'string' && /^[0-9a-f-]{36}$/i.test(s)
const validSettings = (v: unknown): v is Settings =>
  isRecord(v) && uuid(v.profileId) && ['PRIVATE', 'FRIENDS', 'PUBLIC'].includes(String(v.visibility)) &&
  Number.isSafeInteger(v.version) && Number(v.version) >= 0
const validConnection = (v: unknown): v is Connection =>
  isRecord(v) && uuid(v.id) && uuid(v.profileId) && typeof v.username === 'string' &&
  typeof v.state === 'string' && typeof v.incoming === 'boolean' && Number.isSafeInteger(v.version)
const validConnections = (v: unknown): v is { content: Connection[] } =>
  isRecord(v) && Array.isArray(v.content) && v.content.every(validConnection)
const validBoard = (v: unknown): v is FriendBoard =>
  isRecord(v) && ['all', 'weekly', 'monthly'].includes(String(v.period)) &&
  Array.isArray(v.content) && v.content.every((row: unknown) =>
    isRecord(row) && typeof row.username === 'string' && Number.isSafeInteger(row.rank) &&
    Number.isSafeInteger(row.earnedXp))

async function read<T>(path: string, guard: (v: unknown) => v is T, signal?: AbortSignal): Promise<T> {
  const v: unknown = await apiRequest(path, { signal })
  if (!guard(v)) throw invalid()
  return v
}

async function write<T>(path: string, method: 'POST' | 'PATCH', data: object,
  guard: (v: unknown) => v is T): Promise<T> {
  const token = (await getCsrfToken()).csrfToken
  const v: unknown = await apiRequest(path, { method,
    headers: { 'X-CSRF-TOKEN': token, 'Content-Type': 'application/json' },
    body: JSON.stringify(data) })
  if (!guard(v)) throw invalid()
  return v
}
export const getSettings = (signal?: AbortSignal) => read('/api/v1/me/social/settings', validSettings, signal)
export const setVisibility = (visibility: Visibility, expectedVersion: number) =>
  write('/api/v1/me/social/settings', 'PATCH', { visibility, expectedVersion }, validSettings)
export const invite = (username: string) =>
  write('/api/v1/me/friend-requests', 'POST', { username }, validConnection)
export const listRequests = (direction: 'incoming' | 'outgoing', signal?: AbortSignal) =>
  read('/api/v1/me/friend-requests?direction=' + direction, validConnections, signal)
export const listFriends = (signal?: AbortSignal) => read('/api/v1/me/friends', validConnections, signal)
export const decide = (id: string, action: 'accept' | 'reject' | 'cancel' | 'remove', expectedVersion: number) =>
  write('/api/v1/me/friend-requests/' + encodeURIComponent(id) + '/' + action, 'POST',
    { expectedVersion }, validConnection)
export const block = async (username: string) => {
  const csrf = (await getCsrfToken()).csrfToken
  await apiRequest('/api/v1/me/social/blocks', {
    method: 'POST', headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrf },
    body: JSON.stringify({ username }),
  })
}
export const getFriendsBoard = (period: Period, signal?: AbortSignal) =>
  read('/api/v1/me/friends/leaderboard?period=' + period, validBoard, signal)
export type PublicProfile = { profileId: string; displayName: string }
const validProfile = (v: unknown): v is PublicProfile => isRecord(v) && uuid(v.profileId) && typeof v.displayName === 'string'
export const getPublicProfile = (id: string, signal?: AbortSignal) =>
  read('/api/v1/social/profiles/' + encodeURIComponent(id), validProfile, signal)
