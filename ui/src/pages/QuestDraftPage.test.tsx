import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { createAppRoutes } from '../app/router'
import { AuthProvider } from '../features/auth/AuthContext'

const quest = {
  id: 'draft-1', creatorId: '00000000-0000-4000-8000-000000000001', title: 'A valid title', description: 'A valid description',
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
    id: '00000000-0000-4000-8000-000000000001', email: 'player@example.test', username: 'quest_player',
  }))
  if (url.pathname === '/api/v1/me/quests/draft-1/moderation') {
    return Promise.resolve(Response.json({ state: 'UNREVIEWED', reasonCode: null }))
  }
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

function intercept(handler: (path: string, init?: RequestInit) => Response | undefined) {
  const original = vi.mocked(fetch).getMockImplementation()!
  vi.mocked(fetch).mockImplementation(async (input, init) => handler(new URL(String(input), 'http://localhost').pathname, init) ?? original(input, init))
}

it.each(['', '   ', '😀😀', '😀'.repeat(121)])('rejects invalid required title locally (%s)', async title => {
  renderAt('/quests/new')
  fireEvent.change(await screen.findByRole('textbox', { name: 'Title' }), { target: { value: title } })
  await userEvent.click(screen.getByRole('button', { name: 'Save draft' }))
  expect(screen.getByText('Use 3–120 Unicode characters.')).toBeVisible()
  expect(vi.mocked(fetch).mock.calls.some(([, init]) => init?.method === 'POST')).toBe(false)
})

it('requires a saved draft and approval before publish; submission uses saved ETag', async () => {
  let submissionEtag = ''
  let published = false
  intercept((path, init) => {
    if (init?.method === 'PATCH') {
      return Response.json({ ...quest, title: 'Latest saved title' }, { headers: { ETag: '"5"' } })
    }
    if (path.endsWith('/submit-review')) {
      submissionEtag = new Headers(init?.headers).get('If-Match') ?? ''
      return Response.json({ state: 'PENDING_REVIEW', reasonCode: null }, { status: 202 })
    }
    if (path.endsWith('/publish')) {
      published = true
      return Response.json({ ...quest, status: 'PUBLISHED' }, { headers: { ETag: '"6"' } })
    }
  })
  renderAt('/quests/draft-1/edit')
  fireEvent.change(await screen.findByRole('textbox', { name: 'Title' }), { target: { value: 'Latest saved title' } })
  expect(screen.getByRole('button', { name: 'Publish approved quest' })).toBeDisabled()
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  await waitFor(() => expect(screen.getByRole('button', { name: 'Submit for safety review' })).toBeEnabled())
  await userEvent.click(screen.getByRole('button', { name: 'Submit for safety review' }))
  expect(await screen.findByText('Awaiting safety review')).toBeVisible()
  expect(submissionEtag).toBe('"5"')
  expect(screen.getByRole('button', { name: 'Publish approved quest' })).toBeDisabled()
  expect(published).toBe(false)
})

it('allows approved Quest to publish only after review state is confirmed', async () => {
  intercept((path) => {
    if (path.endsWith('/moderation')) return Response.json({ state: 'APPROVED', reasonCode: null })
    if (path.endsWith('/publish')) return Response.json(
      { ...quest, status: 'PUBLISHED', publishedAt: '2026-01-01T12:00:00Z' },
      { headers: { ETag: '"5"' } })
  })
  const router = renderAt('/quests/draft-1/edit')
  const publish = await screen.findByRole('button', { name: 'Publish approved quest' })
  await waitFor(() => expect(publish).toBeEnabled())
  await userEvent.click(publish)
  await waitFor(() => expect(router.state.location.pathname).toBe('/quests/draft-1'))
})

it('preserves edits and forbids publication after an uncertain save response', async () => {
  let patches = 0
  intercept((_path, init) => {
    if (init?.method === 'PATCH') { patches++; return Response.json(quest) } // Missing required ETag.
  })
  renderAt('/quests/draft-1/edit')
  fireEvent.change(await screen.findByRole('textbox', { name: 'Title' }), { target: { value: 'Keep my edits' } })
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('valid version')
  expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Keep my edits')
  expect(screen.getByRole('button', { name: 'Save changes' })).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Publish approved quest' })).toBeDisabled()
  expect(patches).toBe(1)
  await userEvent.click(screen.getByRole('button', { name: 'Reload saved quest' }))
  await userEvent.click(screen.getByRole('button', { name: 'Keep editing' }))
  expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Keep my edits')
  await userEvent.click(screen.getByRole('button', { name: 'Reload saved quest' }))
  await userEvent.click(screen.getByRole('button', { name: 'Reload and discard local changes' }))
  await waitFor(() => expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue(quest.title))
})

it('shows publication validation from the safety submission step', async () => {
  intercept(path => path.endsWith('/submit-review') ? Response.json({
    code: 'validation_failed', detail: 'Complete the Quest fields.',
    errors: [{ field: 'description', code: 'required', message: 'Add a description.' }],
  }, { status: 422 }) : undefined)
  renderAt('/quests/draft-1/edit')
  const submit = await screen.findByRole('button', { name: 'Submit for safety review' })
  await userEvent.click(submit)
  expect(await screen.findByRole('alert')).toHaveTextContent('Complete the Quest fields.')
})

it('warns before navigating away from unsaved input and can cancel', async () => {
  const router = renderAt('/quests/draft-1/edit')
  fireEvent.change(await screen.findByRole('textbox', { name: 'Title' }), { target: { value: 'Unsaved title' } })
  await userEvent.click(screen.getByRole('link', { name: 'Back to My quests' }))
  expect(await screen.findByRole('dialog', { name: 'Discard unsaved changes?' })).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Keep editing' }))
  expect(router.state.location.pathname).toBe('/quests/draft-1/edit')
  expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Unsaved title')
})

it.each([401, 403])('expires auth on a %s write rejection without resubmission, preserving return path', async status => {
  let expired = false
  let writes = 0
  intercept((path, init) => {
    if (init?.method === 'PATCH') {
      writes++; expired = true
      return Response.json({ code: status === 403 ? 'csrf_invalid' : 'unauthenticated' }, { status })
    }
    if (expired && path.endsWith('/auth/me')) return Response.json({}, { status: 401 })
  })
  const router = renderAt('/quests/draft-1/edit?from=review')
  fireEvent.change(await screen.findByRole('textbox', { name: 'Title' }), { target: { value: 'Unsaved title' } })
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
  expect(router.state.location.state.from.pathname).toBe('/quests/draft-1/edit')
  expect(router.state.location.state.from.search).toBe('?from=review')
  expect(screen.queryByText('@quest_player')).not.toBeInTheDocument()
  expect(writes).toBe(1)
})

it('keeps authenticated state for a CSRF rejection when the session is still valid', async () => {
  let writes = 0
  intercept((_path, init) => {
    if (init?.method === 'PATCH') { writes++; return Response.json({ code: 'csrf_invalid', detail: 'Security check expired.' }, { status: 403 }) }
  })
  renderAt('/quests/draft-1/edit')
  fireEvent.change(await screen.findByRole('textbox', { name: 'Title' }), { target: { value: 'Preserved text' } })
  await userEvent.click(screen.getByRole('button', { name: 'Save changes' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Security check expired.')
  expect(screen.getByText('@quest_player')).toBeVisible()
  expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Preserved text')
  expect(writes).toBe(1)
})

it('does not log out or lose edits when the unsaved sign-out warning is cancelled', async () => {
  renderAt('/quests/draft-1/edit')
  fireEvent.change(await screen.findByRole('textbox', { name: 'Title' }), { target: { value: 'Keep editing this' } })
  await userEvent.click(screen.getByRole('button', { name: 'Sign out' }))
  expect(await screen.findByRole('dialog', { name: 'Discard changes and sign out?' })).toBeVisible()
  await userEvent.click(screen.getByRole('button', { name: 'Keep editing' }))
  expect(screen.getByRole('textbox', { name: 'Title' })).toHaveValue('Keep editing this')
  expect(vi.mocked(fetch).mock.calls.some(([input]) => String(input).endsWith('/logout'))).toBe(false)
})

it('prevents duplicate saves and edits while a save is pending', async () => {
  const original = vi.mocked(fetch).getMockImplementation()!
  let finish: ((response: Response) => void) | undefined
  let patches = 0
  vi.mocked(fetch).mockImplementation((input, init) => {
    if (init?.method === 'PATCH') {
      patches++
      return new Promise<Response>(resolve => { finish = resolve })
    }
    return original(input, init)
  })
  renderAt('/quests/draft-1/edit')
  const title = await screen.findByRole('textbox', { name: 'Title' })
  fireEvent.change(title, { target: { value: 'Saved once' } })
  fireEvent.submit(title.closest('form')!)
  fireEvent.submit(title.closest('form')!)
  await waitFor(() => expect(patches).toBe(1))
  expect(title).toBeDisabled()
  expect(screen.getByRole('button', { name: 'Publish approved quest' })).toBeDisabled()
  finish?.(Response.json({ ...quest, title: 'Saved once' }, { headers: { ETag: '"5"' } }))
  await waitFor(() => expect(title).toBeEnabled())
  expect(title).toHaveValue('Saved once')
})
