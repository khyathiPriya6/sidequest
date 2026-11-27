import type { Quest, QuestPage } from './api'

export type Page<T> = { content: T[]; page: number; totalPages: number; hasNext: boolean; hasPrevious: boolean }
export type User = { id: string; email: string; username: string }
export type Participation = { id: string; questId: string; status: 'IN_PROGRESS' | 'COMPLETED'; awardedXp: number; rewardXp: number; quest: { title: string; description: string; status: string }; }
export type Profile = { username: string; totalXp: number; level: number; progressPercent: number; activeQuests: number; completedQuests: number; joinedQuests: number; unlockedAchievementCount: number }
export type Achievement = { id: string; name: string; description: string; unlocked: boolean; progress: number; target: number }
export type Connection = { id: string; profileId: string; username: string; state: string; incoming: boolean; version: number }
export type Invitation = { id: string; teamId: string; teamName: string; sender: string; recipient: string; status: string; version: number }
export type Team = { id: string; name: string; state: string; role: 'OWNER' | 'MEMBER'; members: number; version: number }
export type Member = { id: string; username: string; role: string }
export type Challenge = { id: string; questId: string; title: string; targetCompletions: number; completed: number; earnedXp: number; status: string }
export type Score = { username: string; earnedXp: number; completed: number; rank: number }
export type Review = { questId: string; title: string; description: string; state: string; questStatus: string; version: number; reports: number; reasonCode: string | null }
export type Draft = { quest: Quest; etag: string }

const base = (process.env.EXPO_PUBLIC_API_URL ?? 'http://127.0.0.1:8080').replace(/\/$/, '')
export class ApiFailure extends Error {
  constructor(readonly status: number, readonly code: string, message: string) { super(message) }
}
export async function request<T>(path: string, options: { method?: string; body?: object; etag?: string; signal?: AbortSignal } = {}): Promise<T> {
  const method = options.method ?? 'GET'
  const headers: Record<string, string> = { Accept: 'application/json, application/problem+json' }
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.etag) headers['If-Match'] = options.etag
  if (method !== 'GET') {
    // Native fetch must preserve the same server session cookie from the CSRF request.
    const csrf = await request<{ csrfToken: string }>('/api/v1/auth/csrf')
    headers['X-CSRF-TOKEN'] = csrf.csrfToken
  }
  let response: Response
  try {
    response = await fetch(base + path, {
      method, headers, credentials: 'include', signal: options.signal,
      body: options.body === undefined ? undefined : JSON.stringify(options.body),
    })
  } catch (error) {
    if (options.signal?.aborted) throw error
    throw new ApiFailure(0, 'network_error', 'Network error. The action may have succeeded; refresh before retrying.')
  }
  let data: unknown = undefined
  if (response.status !== 204) {
    try { data = await response.json() } catch {
      throw new ApiFailure(response.status, 'invalid_response', 'The service returned an unreadable response.')
    }
  }
  if (!response.ok) {
    const problem = data && typeof data === 'object' ? data as Record<string, unknown> : {}
    throw new ApiFailure(response.status, String(problem.code ?? 'request_failed'),
      String(problem.detail ?? problem.title ?? 'Request failed (' + response.status + ').'))
  }
  return data as T
}
export async function draftRequest(path: string, options: { method?: string; body?: object; etag?: string } = {}): Promise<Draft> {
  const method = options.method ?? 'GET'
  const headers: Record<string,string> = { Accept: 'application/json' }
  if (options.etag) headers['If-Match'] = options.etag
  if (options.body) headers['Content-Type'] = 'application/json'
  if (method !== 'GET') {
    const csrf = await request<{ csrfToken: string }>('/api/v1/auth/csrf')
    headers['X-CSRF-TOKEN'] = csrf.csrfToken
  }
  let res: Response
  try {
    res = await fetch(base + path, { method, headers, credentials: 'include',
      body: options.body ? JSON.stringify(options.body) : undefined })
  } catch { throw new ApiFailure(0, 'network_error', 'Request status uncertain. Reload before retrying.') }
  let data: unknown
  try { data = await res.json() } catch { throw new ApiFailure(res.status, 'invalid_response','Unreadable Quest response.') }
  if (!res.ok) {
    const p = data && typeof data === 'object' ? data as Record<string,unknown> : {}
    throw new ApiFailure(res.status,String(p.code ?? 'request_failed'),String(p.detail ?? 'Quest update failed.'))
  }
  const etag = res.headers.get('ETag')
  if (!etag || !/^"(?:0|[1-9][0-9]*)"$/.test(etag)) throw new ApiFailure(502,'invalid_etag','Missing Quest version. Reload before editing.')
  return { quest: data as Quest, etag }
}
export const api = {
  me: () => request<User>('/api/v1/auth/me'),
  login: (email: string, password: string) => request<User>('/api/v1/auth/login',{method:'POST',body:{email,password}}),
  register: (email: string, username: string, password: string) => request<User>('/api/v1/auth/register',{method:'POST',body:{email,username,password}}),
  logout: () => request<void>('/api/v1/auth/logout',{method:'POST',body:{}}),
  quests: (q='',page=0) => request<QuestPage>('/api/v1/quests?'+new URLSearchParams({q,page:String(page),size:'12'})),
  quest: (id: string) => request<Quest>('/api/v1/quests/'+encodeURIComponent(id)),
  myQuests: () => request<QuestPage>('/api/v1/me/quests?page=0&size=100'),
  draft: (id: string) => draftRequest('/api/v1/me/quests/'+encodeURIComponent(id)),
  createDraft: (body: object) => draftRequest('/api/v1/quests',{method:'POST',body}),
  updateDraft: (id: string,body: object,etag: string) => draftRequest('/api/v1/quests/'+encodeURIComponent(id),{method:'PATCH',body,etag}),
  publish: (id: string,etag: string) => draftRequest('/api/v1/quests/'+encodeURIComponent(id)+'/publish',{method:'POST',etag,body:{}}),
  archive: (id: string,etag: string) => draftRequest('/api/v1/quests/'+encodeURIComponent(id)+'/archive',{method:'POST',etag,body:{}}),
  reviewState: (id: string) => request<{state:string;version:number}>('/api/v1/me/quests/'+encodeURIComponent(id)+'/moderation'),
  submitReview: (id:string,etag:string) => request<unknown>('/api/v1/quests/'+encodeURIComponent(id)+'/submit-review',{method:'POST',etag}),
  participations: () => request<Page<Participation>>('/api/v1/me/participations?page=0&size=100'),
  byQuest: (id:string) => request<Page<Participation>>('/api/v1/me/participations?questId='+encodeURIComponent(id)),
  join: (id:string) => request<Participation>('/api/v1/quests/'+encodeURIComponent(id)+'/join',{method:'POST'}),
  complete: (id:string) => request<Participation>('/api/v1/participations/'+encodeURIComponent(id)+'/complete',{method:'POST'}),
  profile: () => request<Profile>('/api/v1/me/profile'),
  achievements: () => request<{achievements:Achievement[]}>('/api/v1/me/achievements'),
  xp: () => request<Page<{id:string;questTitle:string;amount:number}>>('/api/v1/me/xp-transactions?page=0&size=30'),
  friends: () => request<Page<Connection>>('/api/v1/me/friends?page=0'),
  requests: (direction:'incoming'|'outgoing') => request<Page<Connection>>('/api/v1/me/friend-requests?direction='+direction+'&page=0'),
  addFriend: (username:string) => request<Connection>('/api/v1/me/friend-requests',{method:'POST',body:{username}}),
  friendDecision: (id:string,action:string,expectedVersion:number) => request<Connection>('/api/v1/me/friend-requests/'+encodeURIComponent(id)+'/'+action,{method:'POST',body:{expectedVersion}}),
  friendsBoard: (period='all') => request<Page<Score>>('/api/v1/me/friends/leaderboard?period='+period+'&page=0'),
  teams: () => request<Page<Team>>('/api/v1/me/teams?page=0'),
  team: (id:string) => request<Team>('/api/v1/teams/'+encodeURIComponent(id)),
  createTeam: (name:string) => request<Team>('/api/v1/teams',{method:'POST',body:{name}}),
  teamMembers: (id:string) => request<Page<Member>>('/api/v1/teams/'+encodeURIComponent(id)+'/members?page=0'),
  teamInvitations: () => request<Page<Invitation>>('/api/v1/me/team-invitations?page=0'),
  teamSentInvites: (id:string) => request<Page<Invitation>>('/api/v1/teams/'+encodeURIComponent(id)+'/invitations?page=0'),
  inviteTeam: (id:string,username:string) => request<Invitation>('/api/v1/teams/'+encodeURIComponent(id)+'/invitations',{method:'POST',body:{username}}),
  inviteDecision: (id:string,action:string,expectedVersion:number) => request<Invitation>('/api/v1/me/team-invitations/'+encodeURIComponent(id)+'/'+action,{method:'POST',body:{expectedVersion}}),
  leaveTeam: (id:string) => request<Team>('/api/v1/teams/'+encodeURIComponent(id)+'/leave',{method:'POST',body:{}}),
  archiveTeam: (id:string) => request<Team>('/api/v1/teams/'+encodeURIComponent(id)+'/archive',{method:'POST',body:{}}),
  removeMember: (id:string,userId:string) => request<void>('/api/v1/teams/'+encodeURIComponent(id)+'/members/'+encodeURIComponent(userId)+'/remove',{method:'POST'}),
  challenges: (id:string) => request<Page<Challenge>>('/api/v1/teams/'+encodeURIComponent(id)+'/challenges?page=0'),
  addChallenge: (id:string,questId:string,days:number,targetCompletions:number) =>
    request<Challenge>('/api/v1/teams/'+encodeURIComponent(id)+'/challenges',{method:'POST',body:{questId,days,targetCompletions}}),
  teamBoard: (id:string) => request<Page<Score>>('/api/v1/teams/'+encodeURIComponent(id)+'/leaderboard?page=0'),
  moderation: () => request<Page<Review>>('/api/v1/moderation/queue?page=0&size=20'),
  moderate: (item:Review,action:string,reasonCode:string) => request<Review>('/api/v1/moderation/quests/'+encodeURIComponent(item.questId)+'/decisions',
    {method:'POST',body:{action,reasonCode,expectedVersion:item.version}}),
}
