import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { createAppRoutes } from '../app/router'
import { AuthProvider } from '../features/auth/AuthContext'

const quest = {
  id: 'draft-1', creatorId: 'user-1', title: 'A valid title', description: 'A valid description',
  category: 'ADVENTURE', difficulty: 'EASY', rewardXp: 25, status: 'DRAFT',
  createdAt: '2025-01-01T10:00:00Z', updatedAt: '2025-01-01T10:00:00Z',
  publishedAt: null, archivedAt: null,
}

function renderAt(path: string) {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  render(<AuthProvider><RouterProvider router={router} /></AuthProvider>)
  return router
}

beforeEach(() => vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL, init?: RequestInit) => {
  const url = new URL(String(input), 'http://localhost')
  if (url.pathname === '/api/v1/auth/csrf') return Promise.resolve(Response.json({ csrfToken: 'test-token' }))
  if (url.pathname === '/api/v1/auth/me') return Promise.resolve(Response.json({
    id: 'user-1', email: 'player@example.test', username: 'quest_player',
  }))
  if (url.pathname === '/api/v1/me/quests/draft-1') {
    return Promise.resolve(Response.json(quest, { status: 200, headers: { ETag: '"4"' } }))
  }
  if (url.pathname === '/api/v1/quests' && init?.method === 'POST') {
    return Promise.resolve(Response.json(quest, { status: 201, headers: { ETag: '"0"', Location: '/api/v1/quests/draft-1' } }))
  }
  if (url.pathname === '/api/v1/quests/draft-1' && init?.method === 'PATCH') {
    if (init.headers && new Headers(init.headers).get('If-Match') !== '"4"') {
      return Promise.resolve(Response.json({
        detail: 'The draft changed in another session.', code: 'precondition_failed',
      }, { status: 412 }))
    }
    return Promise.resolve(Response.json({ ...quest, title: 'Updated title' }, {
      status: 200,
      headers: { ETag: '"5"' },
    }))
  }
  if (url.pathname === '/api/v1/health') return Promise.resolve(Response.json({ status: 'UP', service: 'sidequest-api' }))
  return Promise.resolve(Response.json({}, { status: 404 }))
})))

it('creates a real draft and preserves the edit flow', async () => {
  const user = userEvent.setup()
  const router = renderAt('/quests/new')
  fireEvent.change(await screen.findByRole('textbox', { name: 'Title' }), { target: { value: 'A valid title' } })
  fireEvent.change(screen.getByRole('textbox', { name: 'Description' }), { target: { value: 'A valid description' } })
  await user.click(screen.getByRole('button', { name: 'Save draft' }))
  await waitFor(() => expect(router.state.location.pathname).toBe('/quests/draft-1/edit'))
  expect(await screen.findByText('Saved')).toBeVisible()
})

it('maps accessible validation errors and preserves failed input', async () => {
  const user = userEvent.setup()
  renderAt('/quests/new')
  await screen.findByRole('heading', { name: 'Create a quest' })
  fireEvent.change(screen.getByRole('textbox', { name: 'Title' }), { target: { value: 'x' } })
  fireEvent.change(screen.getByRole('textbox', { name: 'Description' }), { target: { value: 'short' } })
  fireEvent.change(screen.getByRole('spinbutton', { name: 'Reward XP' }), { target: { value: '1001' } })
  await user.click(screen.getByRole('button', { name: 'Save draft' }))
  expect(await screen.findByText('Use 3–120 Unicode characters.')).toBeVisible()
  expect(screen.getByText('Use 10–3000 Unicode characters.')).toBeVisible()
  expect(screen.getByText('Use a whole number from 1 to 1000.')).toBeVisible()
  expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('x')
})

it('loads an owned draft, sends If-Match, and reports stale conflicts', async () => {
  const user = userEvent.setup()
  renderAt('/quests/draft-1/edit')
  expect(await screen.findByRole('textbox', { name: 'Title' })).toHaveValue('A valid title')
  fireEvent.change(screen.getByRole('textbox', { name: 'Title' }), { target: { value: 'Updated title' } })
  vi.mocked(fetch).mockImplementationOnce(() => Promise.resolve(Response.json({ csrfToken: 'test-token' })))
  vi.mocked(fetch).mockImplementationOnce(() => Promise.resolve(Response.json(
    { detail: 'The draft changed.', code: 'precondition_failed' }, { status: 412 })))
  await user.click(screen.getByRole('button', { name: 'Save changes' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('changed in another tab')
  expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Updated title')
})
