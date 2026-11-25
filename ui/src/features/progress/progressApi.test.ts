import { beforeEach, expect, it, vi } from 'vitest'
import { completeParticipation, getParticipations, getPlayerProfile, joinQuest } from './progressApi'

const participation = {
  id: 'participation-id', questId: 'quest-id', status: 'IN_PROGRESS', rewardXp: 50,
  joinedAt: '2026-11-20T10:00:00Z', completedAt: null, awardedXp: 0,
  quest: { title: 'Morning walk', description: 'Go outside.', category: 'FITNESS', difficulty: 'EASY', status: 'PUBLISHED' },
}
const empty = { content: [], page: 0, size: 20, totalElements: 0, totalPages: 0, hasNext: false, hasPrevious: false }

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = String(input)
    if (url.endsWith('/auth/csrf')) return Response.json({ csrfToken: 'csrf-test' })
    if (url.includes('/me/participations?')) return Response.json({ ...empty, content: [participation], totalElements: 1, totalPages: 1 })
    if (url.endsWith('/join') || url.endsWith('/complete')) return Response.json(participation)
    return Response.json({}, { status: 404 })
  }))
})

it('does not send a mutation body and includes the CSRF header', async () => {
  await joinQuest('quest-id')
  const calls = vi.mocked(fetch).mock.calls
  const [, options] = calls.find(([input]) => String(input).endsWith('/join'))!
  expect(options?.method).toBe('POST')
  expect(new Headers(options?.headers).get('X-CSRF-TOKEN')).toBe('csrf-test')
  expect(options?.body).toBeUndefined()
  await completeParticipation('participation-id')
})

it('filters participation history by status and quest', async () => {
  const page = await getParticipations({ status: 'IN_PROGRESS', questId: 'quest-id', page: 0 })
  expect(page.content[0].quest.title).toBe('Morning walk')
  expect(vi.mocked(fetch).mock.calls.some(([input]) =>
    String(input).includes('status=IN_PROGRESS') && String(input).includes('questId=quest-id'))).toBe(true)
})

it('rejects malformed XP/profile responses rather than showing fabricated data', async () => {
  vi.mocked(fetch).mockResolvedValue(Response.json({ totalXp: 999999 }))
  await expect(getPlayerProfile()).rejects.toMatchObject({ code: 'invalid_response' })
})

it('does not retry a failed mutation automatically', async () => {
  let joinCount = 0
  vi.mocked(fetch).mockImplementation(async input => {
    if (String(input).endsWith('/auth/csrf')) return Response.json({ csrfToken: 'token' })
    if (String(input).endsWith('/join')) { joinCount++; throw new Error('connection reset') }
    return Response.json({})
  })
  await expect(joinQuest('quest-id')).rejects.toMatchObject({ code: 'network_error' })
  expect(joinCount).toBe(1)
})
