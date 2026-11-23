import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { AuthProvider } from '../features/auth/AuthContext'
import { createAppRoutes } from '../app/router'
const quest = {
  id: 'quest-123', creatorId: '00000000-0000-4000-8000-000000000099',
  title: 'Morning challenge', description: 'Walk outside', category: 'FITNESS',
  difficulty: 'EASY', rewardXp: 50, status: 'PUBLISHED', createdAt: '2026-11-20T00:00:00Z',
  updatedAt: '2026-11-20T00:00:00Z', publishedAt: '2026-11-20T00:00:00Z', archivedAt: null,
}
const participation = {
  id: 'part-1', questId: quest.id, status: 'IN_PROGRESS', rewardXp: 50, awardedXp: 0,
  joinedAt: '2026-11-20T00:00:00Z', completedAt: null,
  quest: { title: quest.title, description: quest.description, category: 'FITNESS', difficulty: 'EASY', status: 'PUBLISHED' },
}
const envelope = (items: unknown[]) => ({ content: items, page: 0, size: 20, totalElements: items.length,
  totalPages: items.length ? 1 : 0, hasNext: false, hasPrevious: false })
function renderAt(path: string) {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  render(<AuthProvider><RouterProvider router={router} /></AuthProvider>)
  return router
}
let joined = false
let completed = false
beforeEach(() => {
  joined = false
  completed = false
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/api/v1/auth/csrf') return Response.json({ csrfToken: 'token' })
    if (url.pathname === '/api/v1/auth/me') return Response.json({
      id: '00000000-0000-4000-8000-000000000001', email: 'player@example.test', username: 'player_test',
    })
    if (url.pathname === '/api/v1/quests/quest-123') return Response.json(quest)
    if (url.pathname === '/api/v1/me/participations') return Response.json(envelope(joined
      ? [{ ...participation, status: completed ? 'COMPLETED' : 'IN_PROGRESS',
        completedAt: completed ? '2026-11-20T12:00:00Z' : null, awardedXp: completed ? 50 : 0 }] : []))
    if (url.pathname === '/api/v1/quests/quest-123/join') { joined = true; return Response.json(participation) }
    if (url.pathname === '/api/v1/participations/part-1/complete') {
      completed = true
      return Response.json({ ...participation, status: 'COMPLETED', awardedXp: 50, completedAt: '2026-11-20T12:00:00Z' })
    }
    if (url.pathname === '/api/v1/health') return Response.json({ status: 'UP', service: 'sidequest-api' })
    return Response.json({}, { status: 404 })
  }))
})

it('joins a real quest and completes through a confirmation dialog', async () => {
  const user = userEvent.setup()
  renderAt('/quests/quest-123')
  await user.click(await screen.findByRole('button', { name: 'Join quest' }))
  await user.click(await screen.findByRole('button', { name: 'Complete quest' }))
  const dialog = await screen.findByRole('dialog', { name: 'Complete this quest?' })
  expect(within(dialog).getByText(/permanent/)).toBeVisible()
  await user.click(within(dialog).getByRole('button', { name: 'Confirm completion' }))
  await waitFor(() => expect(screen.getByText(/Completed · 50 XP earned/)).toBeVisible())
  const calls = vi.mocked(fetch).mock.calls
  expect(calls.filter(([input]) => String(input).includes('/join'))).toHaveLength(1)
  expect(calls.filter(([input]) => String(input).includes('/complete'))).toHaveLength(1)
})

it('disallows creator enrollment without a mutation', async () => {
  const user = userEvent.setup()
  vi.mocked(fetch).mockImplementation(async input => {
    const path = String(input)
    if (path.endsWith('/auth/csrf')) return Response.json({ csrfToken: 'token' })
    if (path.endsWith('/auth/me')) return Response.json({ id: quest.creatorId, email: 'owner@example.test', username: 'owner_test' })
    if (path.endsWith('/quests/quest-123')) return Response.json(quest)
    if (path.includes('/me/participations?')) return Response.json(envelope([]))
    if (path.endsWith('/health')) return Response.json({ status: 'UP', service: 'sidequest-api' })
    return Response.json({}, { status: 404 })
  })
  renderAt('/quests/quest-123')
  expect(await screen.findByText(/creators cannot join/i)).toBeVisible()
  expect(screen.queryByRole('button', { name: 'Join quest' })).not.toBeInTheDocument()
  expect(user).toBeDefined()
})

it('renders server-supplied profile, achievements and XP history', async () => {
  vi.mocked(fetch).mockImplementation(async input => {
    const path = String(input)
    if (path.endsWith('/auth/csrf')) return Response.json({ csrfToken: 'token' })
    if (path.endsWith('/auth/me')) return Response.json({ id: '00000000-0000-4000-8000-000000000001', email: 'player@example.test', username: 'player_test' })
    if (path.endsWith('/me/profile')) return Response.json({
      username: 'player_test', createdAt: '2026-11-20T00:00:00Z', totalXp: 50, level: 1,
      xpIntoLevel: 50, xpRemaining: 450, progressPercent: 10,
      joinedQuests: 1, activeQuests: 0, completedQuests: 1, createdQuests: 0, unlockedAchievementCount: 1,
    })
    if (path.endsWith('/me/achievements')) return Response.json({ achievements: [{
      id: 'first', name: 'First Quest', description: 'Complete one quest.', progress: 1, target: 1,
      unlocked: true, unlockedAt: '2026-11-20T12:00:00Z',
    }] })
    if (path.includes('/me/xp-transactions?')) return Response.json(envelope([{
      id: 'xp-1', participationId: 'part-1', questId: 'quest-123', amount: 50,
      awardedAt: '2026-11-20T12:00:00Z', questTitle: 'Morning challenge',
      category: 'FITNESS', difficulty: 'EASY', questStatus: 'PUBLISHED',
    }]))
    if (path.endsWith('/health')) return Response.json({ status: 'UP', service: 'sidequest-api' })
    return Response.json({}, { status: 404 })
  })
  renderAt('/profile')
  expect(await screen.findByText('Level 1 · 50 XP total')).toBeVisible()
  expect(screen.getByText('First Quest')).toBeVisible()
  expect(screen.getByText(/Morning challenge/)).toBeVisible()
})
