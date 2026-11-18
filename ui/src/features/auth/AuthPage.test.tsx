import { fireEvent, render, screen, waitFor } from '@testing-library/react'
import userEvent from '@testing-library/user-event'
import { createMemoryRouter, RouterProvider } from 'react-router-dom'
import { beforeEach, expect, it, vi } from 'vitest'
import { createAppRoutes } from '../../app/router'
import { AuthProvider } from './AuthContext'
import type { AuthUser } from './authApi'

const user: AuthUser = { id: 'user-1', email: 'player@example.test', username: 'quest_player' }
let activeUser: AuthUser | null
let csrfToken: string
let rejectNextWrite: boolean
let nextRegisterResponse: Response | null
let blockRegister = false
let releaseRegister: ((response: Response) => void) | null

function problem(status: number, detail: string, code: string, errors: unknown[] = []) {
  return Response.json({ type: 'about:blank', title: 'Request failed', status, detail, code, errors }, { status })
}

beforeEach(() => {
  activeUser = null
  csrfToken = 'anonymous-csrf'
  rejectNextWrite = false
  nextRegisterResponse = null
  blockRegister = false
  releaseRegister = null
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL, init?: RequestInit) => {
    const path = String(input)
    if (path.endsWith('/csrf')) return Response.json({ csrfToken })
    if (path.endsWith('/me')) return activeUser ? Response.json(activeUser) : problem(401, 'Authentication is required.', 'unauthenticated')
    if (path.endsWith('/register') || path.endsWith('/login')) {
      if (rejectNextWrite || new Headers(init?.headers).get('X-CSRF-TOKEN') !== csrfToken) {
        rejectNextWrite = false
        return problem(403, 'Request is not permitted.', 'csrf_invalid')
      }
      const body = JSON.parse(String(init?.body)) as Record<string, string>
      if (path.endsWith('/register') && blockRegister) {
        return new Promise<Response>(resolve => { releaseRegister = resolve })
      }
      if (path.endsWith('/register') && nextRegisterResponse) {
        const response = nextRegisterResponse
        nextRegisterResponse = null
        return response
      }
      if (path.endsWith('/register') && body.username === user.username) {
        activeUser = user
        csrfToken = 'authenticated-csrf'
        return Response.json(user, { status: 201 })
      }
      if (path.endsWith('/login') && body.email === user.email && body.password === 'correct horse battery') {
        activeUser = user
        csrfToken = 'authenticated-csrf'
        return Response.json(user)
      }
      return problem(path.endsWith('/login') ? 401 : 409, 'Authentication could not be completed.', 'unauthenticated')
    }
    if (path.endsWith('/logout')) {
      if (new Headers(init?.headers).get('X-CSRF-TOKEN') !== csrfToken) return problem(403, 'Request is not permitted.', 'csrf_invalid')
      activeUser = null
      return new Response(null, { status: 204 })
    }
    return Response.json({ status: 'UP', service: 'sidequest-api' })
  }))
})

function renderAt(path: string) {
  const router = createMemoryRouter(createAppRoutes(), { initialEntries: [path] })
  render(<AuthProvider><RouterProvider router={router} /></AuthProvider>)
  return router
}

it('validates username and Unicode password length without sending invalid credentials', async () => {
  const actor = userEvent.setup()
  renderAt('/register')
  await screen.findByRole('heading', { name: 'Create your account' })
  fireEvent.change(screen.getByRole('textbox', { name: /Email/ }), { target: { value: 'player@example.test' } })
  fireEvent.change(screen.getByRole('textbox', { name: /Username/ }), { target: { value: 'no' } })
  fireEvent.change(screen.getByLabelText(/Password/), { target: { value: 'short' } })
  await actor.click(screen.getByRole('button', { name: 'Create account' }))
  expect(await screen.findByText('Use 3–30 letters, digits, or underscores.')).toBeVisible()
  expect(screen.getByText('Enter a password of 12–128 Unicode characters.')).toBeVisible()
  expect(screen.getByRole('textbox', { name: /Email/ })).toHaveValue('player@example.test')
  expect(screen.getByRole('textbox', { name: /Username/ })).toHaveFocus()
  expect(vi.mocked(fetch)).not.toHaveBeenCalledWith('/api/v1/auth/register', expect.anything())
})

it('shows generic login errors and preserves entered values', async () => {
  const actor = userEvent.setup()
  renderAt('/login')
  fireEvent.change(await screen.findByRole('textbox', { name: /Email/ }), { target: { value: 'player@example.test' } })
  expect(screen.getByRole('textbox', { name: /Email/ })).toHaveValue('player@example.test')
  fireEvent.change(screen.getByLabelText(/Password/), { target: { value: 'wrong password value' } })
  await actor.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('We couldn’t sign you in with those details.')
  expect(screen.getByRole('textbox', { name: /Email/ })).toHaveValue('player@example.test')
  expect(screen.getByLabelText(/Password/)).toHaveValue('wrong password value')
})

it('reports CSRF rejection and keeps form values available for retry', async () => {
  const actor = userEvent.setup()
  rejectNextWrite = true
  renderAt('/login')
  fireEvent.change(await screen.findByRole('textbox', { name: /Email/ }), { target: { value: 'player@example.test' } })
  fireEvent.change(screen.getByLabelText(/Password/), { target: { value: 'correct horse battery' } })
  await actor.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Your security check expired.')
  expect(screen.getByRole('textbox', { name: /Email/ })).toHaveValue('player@example.test')
  expect(screen.getByLabelText(/Password/)).toHaveValue('correct horse battery')
})

it('maps RFC 9457 field errors without clearing valid registration fields', async () => {
  const actor = userEvent.setup()
  nextRegisterResponse = problem(422, 'Correct the invalid fields.', 'validation_failed', [
    { field: 'username', code: 'invalid', message: 'That username is unavailable.' },
  ])
  renderAt('/register')
  fireEvent.change(await screen.findByRole('textbox', { name: /Email/ }), { target: { value: 'player@example.test' } })
  fireEvent.change(screen.getByRole('textbox', { name: /Username/ }), { target: { value: 'quest_player' } })
  fireEvent.change(screen.getByLabelText(/Password/), { target: { value: 'correct horse battery' } })
  await actor.click(screen.getByRole('button', { name: 'Create account' }))
  expect(await screen.findByRole('alert')).toHaveTextContent('Correct the invalid fields.')
  expect(screen.getByText('That username is unavailable.')).toBeVisible()
  expect(screen.getByRole('textbox', { name: /Username/ })).toHaveFocus()
  expect(screen.getByRole('textbox', { name: /Email/ })).toHaveValue('player@example.test')
  expect(screen.getByRole('textbox', { name: /Username/ })).toHaveValue('quest_player')
  expect(screen.getByLabelText(/Password/)).toHaveValue('correct horse battery')
})

it('disables duplicate registration while the real request is pending', async () => {
  const actor = userEvent.setup()
  blockRegister = true
  renderAt('/register')
  fireEvent.change(await screen.findByRole('textbox', { name: /Email/ }), { target: { value: user.email } })
  fireEvent.change(screen.getByRole('textbox', { name: /Username/ }), { target: { value: user.username } })
  fireEvent.change(screen.getByLabelText(/Password/), { target: { value: 'correct horse battery' } })
  const submit = screen.getByRole('button', { name: 'Create account' })
  await actor.click(submit)
  expect(await screen.findByRole('button', { name: 'Please wait…' })).toBeDisabled()
  await actor.click(screen.getByRole('button', { name: 'Please wait…' }))
  expect(vi.mocked(fetch).mock.calls.filter(([path]) => String(path).endsWith('/register'))).toHaveLength(1)
  activeUser = user
  csrfToken = 'authenticated-csrf'
  releaseRegister?.(Response.json(user, { status: 201 }))
  expect(await screen.findByText('@quest_player')).toBeVisible()
})

it('registers, returns to the originally requested protected route, and logs out', async () => {
  const actor = userEvent.setup()
  const router = renderAt('/my-quests?tab=drafts')
  expect(await screen.findByRole('heading', { name: 'Welcome back' })).toBeVisible()
  await actor.click(screen.getByRole('link', { name: 'Create an account' }))
  fireEvent.change(await screen.findByRole('textbox', { name: /Email/ }), { target: { value: user.email } })
  fireEvent.change(screen.getByRole('textbox', { name: /Username/ }), { target: { value: user.username } })
  fireEvent.change(screen.getByLabelText(/Password/), { target: { value: 'correct horse battery' } })
  await actor.click(screen.getByRole('button', { name: 'Create account' }))
  expect(await screen.findByRole('heading', { name: 'My quests' })).toBeVisible()
  expect(router.state.location.pathname).toBe('/my-quests')
  expect(screen.getByText('@quest_player')).toBeVisible()
  await actor.click(screen.getByRole('button', { name: 'Sign out' }))
  await waitFor(() => expect(router.state.location.pathname).toBe('/quests'))
  await actor.click(screen.getByRole('link', { name: 'Sign in' }))
  fireEvent.change(await screen.findByRole('textbox', { name: /Email/ }), { target: { value: user.email } })
  fireEvent.change(screen.getByLabelText(/Password/), { target: { value: 'correct horse battery' } })
  await actor.click(screen.getByRole('button', { name: 'Sign in' }))
  expect(await screen.findByText('@quest_player')).toBeVisible()
})
