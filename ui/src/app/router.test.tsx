import { cleanup, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { createAppRoutes } from './router'
import { AuthProvider } from '../features/auth/AuthContext'

function renderAt(path: string) {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  render(<AuthProvider><RouterProvider router={router} /></AuthProvider>)
  return router
}

beforeEach(() => vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
  const path = String(input)
  if (path.endsWith('/csrf')) return Response.json({ csrfToken: 'test-token' })
  if (path.endsWith('/me')) return Response.json({ status: 401, code: 'unauthenticated' }, { status: 401 })
  return Response.json({ status: 'UP', service: 'sidequest-api' })
})))

it.each([
  ['/', 'Make today a quest.'], ['/quests/new', 'Create a quest'], ['/quests/123/edit', 'Edit a quest'],
  ['/my-quests', 'My quests'], ['/profile', 'Your profile'],
  ['/missing', 'That page isn’t here.'],
])('resolves public route %s to the expected view', async (path, title) => {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  render(<AuthProvider><RouterProvider router={router} /></AuthProvider>)
  if (path === '/quests/new' || path === '/quests/123/edit' || path === '/my-quests' || path === '/profile') {
    expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
  } else {
    expect(await screen.findByRole('heading', { name: title, level: 1 })).toBeVisible()
  }
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
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: ['/quests'] })
  render(<AuthProvider><RouterProvider router={router} /></AuthProvider>)
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

it('preserves a protected destination through the login redirect', async () => {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: ['/my-quests?tab=drafts'] })
  render(<AuthProvider><RouterProvider router={router} /></AuthProvider>)
  expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
  expect(router.state.location.pathname).toBe('/login')
  expect(router.state.location.state.from.pathname).toBe('/my-quests')
  expect(router.state.location.state.from.search).toBe('?tab=drafts')
})
