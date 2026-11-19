import { render, screen, waitFor, within } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { createAppRoutes } from './router'

const quest = {
  id: '123', creatorId: '456', title: 'Walk a new route', description: 'A new route worth exploring.',
  category: 'ADVENTURE', difficulty: 'EASY', rewardXp: 50, status: 'PUBLISHED',
  createdAt: '2025-01-01T10:00:00Z', updatedAt: '2025-01-01T10:00:00Z',
  publishedAt: '2025-01-01T10:00:00Z', archivedAt: null,
}

beforeEach(() => vi.stubGlobal('fetch', vi.fn((input: RequestInfo | URL) => {
  const path = new URL(String(input), 'http://localhost').pathname
  if (path === '/api/v1/health') return Promise.resolve(Response.json({ status: 'UP', service: 'sidequest-api' }))
  if (path === '/api/v1/quests/123') return Promise.resolve(Response.json(quest))
  if (path === '/api/v1/quests') return Promise.resolve(Response.json({
    content: [quest], page: 0, size: 20, totalElements: 1, totalPages: 1, hasNext: false, hasPrevious: false,
  }))
  return Promise.resolve(Response.json({}, { status: 404 }))
})))

it.each([
  ['/', 'Make today a quest.'], ['/quests/new', 'Create a quest'], ['/quests/123/edit', 'Edit a quest'],
  ['/my-quests', 'My quests'], ['/profile', 'Your profile'],
  ['/login', 'Sign in'], ['/register', 'Create an account'], ['/missing', 'That page isn’t here.'],
])('resolves %s to the correct route skeleton', async (path, title) => {
  render(<RouterProvider router={createMemoryRouter(createAppRoutes(), { initialEntries: [path] })} />)
  expect(await screen.findByRole('heading', { name: title, level: 1 })).toBeVisible()
})

it('uses active navigation and moves focus/title after navigation', async () => {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: ['/quests'] })
  render(<RouterProvider router={router} />)
  expect(within(screen.getByRole('navigation', { name: 'Main navigation' })).getByRole('link', { name: 'Explore' })).toHaveAttribute('aria-current', 'page')
  expect(screen.queryByRole('navigation', { name: 'Mobile navigation' })).not.toBeInTheDocument()
  await userEvent.click(screen.getByRole('link', { name: 'Sign in' }))
  expect(await screen.findByRole('heading', { name: 'Sign in', level: 1 })).toBeVisible()
  await waitFor(() => expect(screen.getByRole('main')).toHaveFocus())
  expect(document.title).toBe('Sign in · SideQuest')
  await userEvent.click(screen.getByRole('link', { name: 'Back to Explore' }))
  expect(await screen.findByRole('heading', { name: 'Make today a quest.' })).toBeVisible()
})
