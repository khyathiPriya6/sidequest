export type Quest = {
  id: string
  creatorId: string
  title: string
  description: string | null
  category: string | null
  difficulty: string | null
  rewardXp: number | null
  status: string
}
export type QuestPage = {
  content: Quest[]
  page: number
  size: number
  totalPages: number
  totalElements: number
  hasNext: boolean
  hasPrevious: boolean
}

const base = (process.env.EXPO_PUBLIC_API_URL ?? 'http://127.0.0.1:8080').replace(/\/$/, '')

async function read<T>(path: string, signal?: AbortSignal): Promise<T> {
  let response: Response
  try {
    response = await fetch(base + path, { headers: { Accept: 'application/json' }, signal })
  } catch (error) {
    if (signal?.aborted) throw error
    throw new Error('Cannot connect to SideQuest. Check that the backend is running and the API URL is reachable from this device.')
  }
  if (!response.ok) throw new Error('SideQuest API returned ' + response.status + '. Please try again.')
  try {
    return await response.json() as T
  } catch {
    throw new Error('SideQuest returned an unexpected response.')
  }
}

export const questFeed = (query: string, page: number, signal?: AbortSignal) =>
  read<QuestPage>('/api/v1/quests?' + new URLSearchParams({ q: query, page: String(page), size: '12' }), signal)

export const questDetails = (id: string, signal?: AbortSignal) =>
  read<Quest>('/api/v1/quests/' + encodeURIComponent(id), signal)
