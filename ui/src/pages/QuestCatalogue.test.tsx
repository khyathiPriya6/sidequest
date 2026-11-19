import { cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { createAppRoutes } from '../app/router'
import type { Quest, QuestPage } from '../features/quests/questApi'
import { AuthProvider } from '../features/auth/AuthContext'

const publishedQuest: Quest = {
  id: 'quest-123',
  creatorId: 'creator-456',
  title: 'Walk a new route',
  description: 'Find a nearby street you have never explored before.',
  category: 'ADVENTURE',
  difficulty: 'EASY',
  rewardXp: 50,
  status: 'PUBLISHED',
  createdAt: '2025-01-01T10:00:00Z',
  updatedAt: '2025-01-01T10:00:00Z',
  publishedAt: '2025-01-01T10:00:00Z',
  archivedAt: null,
}

function page(content: Quest[] = [publishedQuest], extras: Partial<QuestPage> = {}): QuestPage {
  return {
    content,
    page: 0,
    size: 20,
    totalElements: content.length,
    totalPages: content.length ? 1 : 0,
    hasNext: false,
    hasPrevious: false,
    ...extras,
  }
}

function jsonResponse(body: unknown, status = 200) {
  return Response.json(body, { status })
}

function renderAt(path: string) {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  render(<AuthProvider><RouterProvider router={router} /></AuthProvider>)
  return router
}

function mockApi(onQuestList: (url: URL) => Response | Promise<Response> = () => jsonResponse(page())) {
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/api/v1/health') return Promise.resolve(jsonResponse({ status: 'UP', service: 'sidequest-api' }))
    if (url.pathname === '/api/v1/quests') return Promise.resolve(onQuestList(url))
    if (url.pathname === '/api/v1/quests/quest-123') return Promise.resolve(jsonResponse(publishedQuest))
    return Promise.resolve(jsonResponse({}, 404))
  })
  vi.stubGlobal('fetch', fetchMock)
  return fetchMock
}

beforeEach(() => mockApi())

it('loads published quests from the API and encodes search, filters, and pagination in the URL', async () => {
  const user = userEvent.setup()
  const fetchMock = mockApi(url => jsonResponse(page([], {
    page: Number(url.searchParams.get('page') ?? 0),
    totalElements: 21,
    totalPages: 2,
    hasNext: url.searchParams.get('page') !== '1',
    hasPrevious: url.searchParams.get('page') === '1',
  })))
  const router = renderAt('/quests?q=old&page=3')
  expect(await screen.findByText('No quests match those filters.')).toBeVisible()

  await user.clear(screen.getByRole('searchbox', { name: 'Search quests' }))
  await user.type(screen.getByRole('searchbox', { name: 'Search quests' }), 'new walk')
  await user.click(screen.getByRole('button', { name: /search/i }))
  await waitFor(() => expect(router.state.location.search).toContain('q=new+walk'))
  expect(router.state.location.search).not.toContain('page=')

  await user.selectOptions(screen.getByLabelText('Category'), 'ADVENTURE')
  await waitFor(() => expect(router.state.location.search).toContain('category=ADVENTURE'))
  expect(router.state.location.search).not.toContain('page=')
  expect(fetchMock.mock.calls.some(([request]) => String(request).includes('q=new+walk'))).toBe(true)

  await user.click(await screen.findByRole('button', { name: 'Next' }))
  await waitFor(() => expect(router.state.location.search).toContain('page=1'))
  expect(screen.getByText('Page 2 of 2')).toBeVisible()
  await user.selectOptions(screen.getByLabelText('Difficulty'), 'MEDIUM')
  await waitFor(() => expect(router.state.location.search).toContain('difficulty=MEDIUM'))
  expect(router.state.location.search).not.toContain('page=')
})

it('distinguishes a truly empty public catalogue from filtered no-match results', async () => {
  mockApi(() => jsonResponse(page([], { totalPages: 0 })))
  renderAt('/quests')
  expect(await screen.findByText('Your next great story starts soon.')).toBeVisible()

  cleanup()
  mockApi(() => jsonResponse(page([], { totalPages: 0 })))
  renderAt('/quests?q=missing')
  expect(await screen.findByText('No quests match those filters.')).toBeVisible()
})

it('measures search length in Unicode code points and does not send an invalid query', async () => {
  const fetchMock = mockApi()
  const user = userEvent.setup()
  renderAt('/quests')
  const search = await screen.findByRole('searchbox', { name: 'Search quests' })
  fireEvent.change(search, { target: { value: '😀'.repeat(121) } })
  await user.click(screen.getByRole('button', { name: /search/i }))
  expect(await screen.findByText('Search must be 120 characters or fewer.')).toBeVisible()
  expect(fetchMock.mock.calls.filter(([input]) => String(input).includes('/api/v1/quests?'))).toHaveLength(1)
})

it('shows service errors with retry and keeps newer results when an old request resolves late', async () => {
  let resolveFirst: ((response: Response) => void) | undefined
  const user = userEvent.setup()
  const fetchMock = vi.fn((input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/api/v1/health') return Promise.resolve(jsonResponse({ status: 'UP' }))
    if (url.pathname === '/api/v1/quests' && url.searchParams.get('q') === 'first') {
      return new Promise<Response>(resolve => { resolveFirst = resolve })
    }
    if (url.pathname === '/api/v1/quests') {
      return Promise.resolve(jsonResponse(page([{ ...publishedQuest, id: 'second', title: 'Second result' }])))
    }
    return Promise.resolve(jsonResponse({ detail: 'The service is temporarily unavailable.' }, 503))
  })
  vi.stubGlobal('fetch', fetchMock)
  const router = renderAt('/quests?q=first')
  const search = await screen.findByRole('searchbox', { name: 'Search quests' })
  fireEvent.change(search, { target: { value: 'second' } })
  expect(search).toHaveValue('second')
  await user.click(screen.getByRole('button', { name: /search/i }))
  expect(await screen.findByRole('article', { name: 'Second result' })).toBeVisible()
  await waitFor(() => expect(router.state.location.search).toContain('q=second'))
  expect(fetchMock.mock.calls.some(([input]) => new URL(String(input), 'http://localhost').searchParams.get('q') === 'second')).toBe(true)
  resolveFirst?.(jsonResponse(page([{ ...publishedQuest, id: 'first', title: 'First result' }])))
  expect(screen.queryByRole('article', { name: 'First result' })).not.toBeInTheDocument()

  cleanup()
  let response = jsonResponse({ detail: 'The service is temporarily unavailable.' }, 503)
  mockApi(() => response)
  const errorRouter = renderAt('/quests')
  expect(await screen.findByRole('alert')).toHaveTextContent('temporarily unavailable')
  response = jsonResponse(page())
  fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByRole('article', { name: 'Walk a new route' })).toBeVisible()
  expect(errorRouter.state.location.pathname).toBe('/quests')
})

it('renders public quest details and distinguishes a private or missing quest', async () => {
  mockApi()
  renderAt('/quests/quest-123?q=walk&page=1')
  expect(await screen.findByRole('heading', { name: 'Walk a new route' })).toBeVisible()
  expect(screen.getByText(publishedQuest.description!)).toBeVisible()
  expect(screen.getByText('50 XP reward')).toBeVisible()
  expect(screen.getByRole('link', { name: 'Back to Explore' })).toHaveAttribute('href', '/quests?q=walk&page=1')

  mockApi(url => url.pathname === '/api/v1/quests'
    ? jsonResponse(page())
    : jsonResponse({ detail: 'Quest not found.' }, 404))
  renderAt('/quests/private-quest')
  expect(await screen.findByRole('heading', { name: 'Quest unavailable' })).toBeVisible()
})

it('renders an archived quest returned for its authenticated owner', async () => {
  const archived = { ...publishedQuest, status: 'ARCHIVED', archivedAt: '2026-01-01T00:00:00Z' }
  vi.stubGlobal('fetch', vi.fn(async input => {
    const path = String(input)
    if (path.endsWith('/csrf')) return Response.json({ csrfToken: 'token' })
    if (path.endsWith('/auth/me')) return Response.json({ id: '00000000-0000-4000-8000-000000000001', email: 'owner@example.test', username: 'owner' })
    if (path.endsWith('/quests/quest-123')) return Response.json(archived, { headers: { ETag: '"3"' } })
    return Response.json({ status: 'UP' })
  }))
  renderAt('/quests/quest-123')
  expect(await screen.findByText(archived.description!)).toBeVisible()
  expect(screen.getByText('50 XP reward')).toBeVisible()
  expect(await screen.findByText('@owner')).toBeVisible()
})
