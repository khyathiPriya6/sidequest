import { cleanup, fireEvent, render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { createAppRoutes } from '../app/router'
import { AuthProvider } from '../features/auth/AuthContext'
import type { Quest, QuestPage } from '../features/quests/questApi'

const draft: Quest = {
  id: 'draft-1', creatorId: '00000000-0000-4000-8000-000000000001', title: 'Draft walk', description: 'A draft description',
  category: 'ADVENTURE', difficulty: 'EASY', rewardXp: 25, status: 'DRAFT',
  createdAt: '2025-01-02T10:00:00Z', updatedAt: '2025-01-02T10:00:00Z',
  publishedAt: null, archivedAt: null,
}
const published: Quest = { ...draft, id: 'published-1', title: 'Published walk', status: 'PUBLISHED', publishedAt: '2025-01-02T10:00:00Z' }
const archived: Quest = { ...draft, id: 'archived-1', title: 'Archived walk', status: 'ARCHIVED', archivedAt: '2025-01-03T10:00:00Z' }
let draftPublished = false
let publishedArchived = false

function page(content: Quest[], extras: Partial<QuestPage> = {}): QuestPage {
  return { content, page: 0, size: 20, totalElements: content.length, totalPages: content.length ? 1 : 0,
    hasNext: false, hasPrevious: false, ...extras }
}

function renderAt(path: string) {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  render(<AuthProvider><RouterProvider router={router} /></AuthProvider>)
  return router
}

beforeEach(() => {
  draftPublished = false
  publishedArchived = false
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
  const url = new URL(String(input), 'http://localhost')
  if (url.pathname === '/api/v1/auth/csrf') return Promise.resolve(Response.json({ csrfToken: 'test-token' }))
  if (url.pathname === '/api/v1/auth/me') return Promise.resolve(Response.json({
    id: '00000000-0000-4000-8000-000000000001', email: 'player@example.test', username: 'quest_player',
  }))
  if (url.pathname === '/api/v1/me/quests') {
    const status = url.searchParams.get('status')
    const pageNumber = Number(url.searchParams.get('page') ?? 0)
    if (pageNumber > 0) return Promise.resolve(Response.json(page([published], { page: pageNumber, totalElements: 21, totalPages: 2, hasNext: false, hasPrevious: true })))
    if (status === 'DRAFT') return Promise.resolve(Response.json(page(draftPublished ? [] : [draft])))
    if (status === 'PUBLISHED') return Promise.resolve(Response.json(page(publishedArchived ? [] : [published])))
    if (status === 'ARCHIVED') return Promise.resolve(Response.json(page([archived])))
    return Promise.resolve(Response.json(page([
      ...(draftPublished ? [] : [draft]),
      ...(publishedArchived ? [] : [published]),
      archived,
    ], {
      totalElements: 21,
      totalPages: 2,
      hasNext: true,
    })))
  }
  if (url.pathname === '/api/v1/auth/csrf') return Promise.resolve(Response.json({ csrfToken: 'test-token' }))
  if (url.pathname === `/api/v1/me/quests/${draft.id}`) {
    return Promise.resolve(Response.json(draft, { headers: { ETag: '"3"' } }))
  }
  if (url.pathname === `/api/v1/me/quests/${published.id}`) {
    return Promise.resolve(Response.json(published, { headers: { ETag: '"4"' } }))
  }
  if (url.pathname === `/api/v1/quests/${draft.id}/publish`) {
    draftPublished = true
    return Promise.resolve(Response.json({ ...draft, status: 'PUBLISHED', publishedAt: '2025-01-04T10:00:00Z' }, { headers: { ETag: '"4"' } }))
  }
  if (url.pathname === `/api/v1/quests/${published.id}/archive`) {
    publishedArchived = true
    return Promise.resolve(Response.json({ ...published, status: 'ARCHIVED', archivedAt: '2025-01-04T10:00:00Z' }, { headers: { ETag: '"5"' } }))
  }
  if (url.pathname === '/api/v1/health') return Promise.resolve(Response.json({ status: 'UP', service: 'sidequest-api' }))
  return Promise.resolve(Response.json({}, { status: 404 }))
  }))
})

it('shows only the owner endpoint results and exposes draft-only edit navigation', async () => {
  const router = renderAt('/my-quests')
  expect(await screen.findByRole('heading', { name: 'Draft walk' })).toBeVisible()
  expect(screen.getByRole('heading', { name: 'Published walk' })).toBeVisible()
  expect(screen.getByRole('heading', { name: 'Archived walk' })).toBeVisible()
  expect(screen.getByRole('link', { name: 'Draft walk' })).toHaveAttribute('href', '/quests/draft-1/edit')
  expect(screen.getByRole('link', { name: 'Published walk' })).toHaveAttribute('href', '/quests/published-1')
  expect(screen.getByRole('link', { name: 'Archived walk' })).toHaveAttribute('href', '/quests/archived-1')
  expect(router.state.location.pathname).toBe('/my-quests')
})

it('updates status and page through the URL and paginates owner results', async () => {
  const user = userEvent.setup()
  const router = renderAt('/my-quests')
  await screen.findByRole('heading', { name: 'Draft walk' })
  await user.click(screen.getByRole('button', { name: 'Drafts' }))
  await waitFor(() => expect(router.state.location.search).toBe('?status=DRAFT'))
  expect(screen.getByRole('heading', { name: 'Draft walk' })).toBeVisible()
  expect(screen.queryByRole('heading', { name: 'Published walk' })).not.toBeInTheDocument()

  await user.click(screen.getByRole('button', { name: 'All quests' }))
  await waitFor(() => expect(router.state.location.search).toBe(''))
  await user.click(await screen.findByRole('button', { name: 'Next' }))
  await waitFor(() => expect(router.state.location.search).toBe('?page=1'))
  expect(screen.getByText('Page 2 of 2')).toBeVisible()
})

it('shows empty and error states and retries service failures', async () => {
  cleanup()
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/api/v1/auth/csrf') return Promise.resolve(Response.json({ csrfToken: 'test-token' }))
    if (url.pathname === '/api/v1/auth/me') return Promise.resolve(Response.json({
      id: '00000000-0000-4000-8000-000000000001', email: 'player@example.test', username: 'quest_player',
    }))
    if (url.pathname === '/api/v1/me/quests') return Promise.resolve(Response.json(page([])))
    return Promise.resolve(Response.json({ status: 'UP' }))
  }))
  renderAt('/my-quests')
  expect(await screen.findByText('You haven’t created a quest yet.')).toBeVisible()

  cleanup()
  let fail = true
  vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
    const url = new URL(String(input), 'http://localhost')
    if (url.pathname === '/api/v1/auth/csrf') return Promise.resolve(Response.json({ csrfToken: 'test-token' }))
    if (url.pathname === '/api/v1/auth/me') return Promise.resolve(Response.json({
      id: '00000000-0000-4000-8000-000000000001', email: 'player@example.test', username: 'quest_player',
    }))
    if (url.pathname === '/api/v1/me/quests') {
      if (fail) return Promise.resolve(Response.json({ detail: 'The quest service is unavailable.' }, { status: 503 }))
      return Promise.resolve(Response.json(page([draft])))
    }
    return Promise.resolve(Response.json({ status: 'UP' }))
  }))
  renderAt('/my-quests')
  expect(await screen.findByRole('alert')).toHaveTextContent('quest service is unavailable')
  fail = false
  fireEvent.click(screen.getByRole('button', { name: 'Try again' }))
  expect(await screen.findByRole('heading', { name: 'Draft walk' })).toBeVisible()
})

it('routes drafts through the safety review editor and confirms or cancels archive actions', async () => {
  const user = userEvent.setup()
  renderAt('/my-quests')
  await screen.findByRole('heading', { name: 'Draft walk' })
  expect(screen.getByRole('link', { name: 'Review / publish' })).toHaveAttribute('href', '/quests/draft-1/edit')
  expect(screen.queryByRole('button', { name: 'Publish' })).not.toBeInTheDocument()
  await screen.findByRole('heading', { name: 'Published walk' })
  const publishedCard = screen.getByRole('article', { name: 'Published walk' })
  await user.click(within(publishedCard).getByRole('button', { name: 'Archive' }))
  const dialog = await screen.findByRole('dialog', { name: 'Archive this quest?' })
  await user.click(within(dialog).getByRole('button', { name: 'Cancel' }))
  expect(screen.queryByRole('dialog', { name: 'Archive this quest?' })).not.toBeInTheDocument()
  await user.click(within(publishedCard).getByRole('button', { name: 'Archive' }))
  await user.click(within(await screen.findByRole('dialog', { name: 'Archive this quest?' })).getByRole('button', { name: 'Confirm archive' }))
  await waitFor(() => expect(screen.queryByRole('heading', { name: 'Published walk' })).not.toBeInTheDocument())
})

it('clears authenticated navigation after a protected read expires and preserves the destination', async () => {
  const original = vi.mocked(fetch).getMockImplementation()!
  vi.mocked(fetch).mockImplementation(async (input, init) => {
    if (String(input).includes('/api/v1/me/quests')) return Response.json({ code: 'unauthenticated' }, { status: 401 })
    return original(input, init)
  })
  const router = renderAt('/my-quests?status=ARCHIVED')
  expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
  expect(router.state.location.state.from.pathname).toBe('/my-quests')
  expect(router.state.location.state.from.search).toBe('?status=ARCHIVED')
  expect(screen.queryByText('@quest_player')).not.toBeInTheDocument()
})
