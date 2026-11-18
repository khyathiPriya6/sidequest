import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { createAppRoutes } from './router'
import { AuthProvider } from '../features/auth/AuthContext'

const quest = {
  id: '123', creatorId: '456', title: 'Walk a new route', description: 'A new route worth exploring.',
  category: 'ADVENTURE', difficulty: 'EASY', rewardXp: 50, status: 'PUBLISHED',
  createdAt: '2025-01-01T10:00:00Z', updatedAt: '2025-01-01T10:00:00Z',
  publishedAt: '2025-01-01T10:00:00Z', archivedAt: null,
}

beforeEach(() => vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
  const url = new URL(String(input), 'http://localhost')
  if (url.pathname === '/api/v1/auth/csrf') return Response.json({ csrfToken: 'test-token' })
  if (url.pathname === '/api/v1/auth/me') {
    return Response.json({ title: 'Unauthorized', detail: 'Authentication is required.', code: 'unauthenticated' }, { status: 401 })
  }
  if (url.pathname === '/api/v1/health') return Response.json({ status: 'UP', service: 'sidequest-api' })
  if (url.pathname === '/api/v1/quests/123') return Response.json(quest)
  if (url.pathname === '/api/v1/quests') return Response.json({
    content: [quest], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
  })
  return Response.json({}, { status: 404 })
})))

function renderAt(path: string) {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  render(<AuthProvider><RouterProvider router={router} /></AuthProvider>)
  return router
}

it.each([
  ['/', 'Make today a quest.'],
  ['/quests/123', 'Walk a new route'],
  ['/login', 'Welcome back'],
  ['/register', 'Create your account'],
  ['/missing', 'That page isn’t here.'],
])('resolves public route %s to the expected view', async (path, title) => {
  renderAt(path)
  expect(await screen.findByRole('heading', { name: title, level: 1 })).toBeVisible()
})

it.each(['/quests/new', '/quests/123/edit', '/my-quests', '/my/quests', '/profile'])(
  'protects %s behind the shared sign-in flow',
  async path => {
    const router = renderAt(path)
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
    await waitFor(() => expect(router.state.location.pathname).toBe('/login'))
  },
)

it('uses active navigation and moves focus/title after navigation', async () => {
  const router = renderAt('/quests')
  expect(within(screen.getByRole('navigation', { name: 'Main navigation' })).getByRole('link', { name: 'Explore' })).toHaveAttribute('aria-current', 'page')
  expect(screen.queryByRole('navigation', { name: 'Mobile navigation' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('link', { name: 'Sign in' }))
  expect(await screen.findByRole('heading', { name: 'Welcome back', level: 1 })).toBeVisible()
  await waitFor(() => expect(screen.getByRole('main')).toHaveFocus())
  expect(document.title).toBe('Sign in · SideQuest')
  await userEvent.click(screen.getByRole('link', { name: 'Explore' }))
  expect(await screen.findByRole('heading', { name: 'Make today a quest.' })).toBeVisible()
  expect(router.state.location.pathname).toBe('/quests')
})

it('preserves protected destinations and canonicalizes the legacy dashboard path', async () => {
  const router = renderAt('/my-quests?status=DRAFT')
  expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
  expect(router.state.location.pathname).toBe('/login')
  expect(router.state.location.state.from.pathname).toBe('/my-quests')
  expect(router.state.location.state.from.search).toBe('?status=DRAFT')

  cleanup()
  const legacyRouter = renderAt('/my/quests?status=ARCHIVED')
  expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
  expect(legacyRouter.state.location.pathname).toBe('/login')
  expect(legacyRouter.state.location.state.from.pathname).toBe('/my-quests')
  expect(legacyRouter.state.location.state.from.search).toBe('?status=ARCHIVED')
})
