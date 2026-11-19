import { expect, it, vi } from 'vitest'
import { archiveQuest, createQuestDraft, getOwnedQuest, publishQuest, updateQuestDraft, requireEtag } from './questApi'
import { getCurrentUser, login, register } from '../auth/authApi'
import { authExpiredEvent } from '../../lib/api'

const quest = { id: 'draft-1', creatorId: 'user-1', title: 'Saved title', description: null,
  category: null, difficulty: null, rewardXp: null, status: 'DRAFT',
  createdAt: '2026-01-01T00:00:00Z', updatedAt: '2026-01-01T00:00:00Z', publishedAt: null, archivedAt: null }

it.each([null, '', '0', 'W/"2"', '"01"', '"-1"', '*', '"1", "2"', '"9223372036854775808"'])(
  'rejects invalid strong version %s', value => expect(() => requireEtag(value)).toThrow('valid version'))
it.each(['"0"', '"42"', '"9223372036854775807"'])('accepts contract version %s', value => expect(requireEtag(value)).toBe(value))
const calls = [
  () => getOwnedQuest('draft-1', new AbortController().signal),
  () => createQuestDraft({ title: 'Saved title' }, 'csrf'),
  () => updateQuestDraft('draft-1', { title: 'Saved title' }, '"4"', 'csrf'),
  () => publishQuest('draft-1', '"4"', 'csrf'),
  () => archiveQuest('draft-1', '"4"', 'csrf'),
]
it.each(calls)('rejects missing mutation/read ETag without retrying %#', async call => {
  const fetchMock = vi.fn(async () => Response.json(quest))
  vi.stubGlobal('fetch', fetchMock)
  await expect(call()).rejects.toMatchObject({ code: 'invalid_etag' })
  expect(fetchMock).toHaveBeenCalledTimes(1)
})
it('invalidates a session on a protected 401 even with malformed JSON', async () => {
  const listener = vi.fn()
  window.addEventListener(authExpiredEvent, listener)
  vi.stubGlobal('fetch', vi.fn(async () => new Response('not json', { status: 401 })))
  try {
    await expect(getOwnedQuest('draft-1', new AbortController().signal)).rejects.toMatchObject({ status: 401 })
    expect(listener).toHaveBeenCalledOnce()
  } finally { window.removeEventListener(authExpiredEvent, listener) }
})
it.each([
  () => getCurrentUser(),
  () => login({ email: 'player@example.test', password: 'example password' }, 'csrf'),
  () => register({ email: 'player@example.test', username: 'player', password: 'example password' }, 'csrf'),
])('rejects malformed user responses before auth state receives them %#', async call => {
  vi.stubGlobal('fetch', vi.fn(async () => Response.json({ id: 'not-uuid', email: null, username: 'player' })))
  await expect(call()).rejects.toMatchObject({ code: 'invalid_response' })
})
it('normalizes network errors without replaying mutations', async () => {
  const fetchMock = vi.fn(async () => { throw new TypeError('Failed to fetch') })
  vi.stubGlobal('fetch', fetchMock)
  await expect(createQuestDraft({ title: 'A draft' }, 'csrf')).rejects.toMatchObject({ status: 0, code: 'network_error' })
  expect(fetchMock).toHaveBeenCalledOnce()
})
