export interface AuthUser {
  id: string
  email: string
  username: string
}

import { ApiError, apiRequest, isRecord } from '../../lib/api'
export { ApiError, apiRequest, authExpiredEvent } from '../../lib/api'
export type { ProblemFieldError } from '../../lib/api'
const authPath = '/api/v1/auth'
function currentUser(body: unknown): AuthUser {
  if (!isRecord(body) || typeof body.id !== 'string' || !/^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$/i.test(body.id)
    || typeof body.email !== 'string' || !body.email.includes('@')
    || typeof body.username !== 'string' || !/^[a-z0-9_]{3,30}$/.test(body.username)) {
    throw new ApiError(502, 'invalid_response', 'The service returned an invalid account. Please restore your session again.')
  }
  return { id: body.id, email: body.email, username: body.username }
}

export function getCsrfToken(): Promise<{ csrfToken: string }> {
  return apiRequest<{ csrfToken: string }>(`${authPath}/csrf`).then(body => {
    if (!body || typeof body.csrfToken !== 'string' || body.csrfToken.length === 0) {
      throw new ApiError(502, 'invalid_csrf_response', 'The server did not provide a security token.')
    }
    return body
  })
}

export function getCurrentUser(): Promise<AuthUser> {
  return apiRequest(`${authPath}/me`).then(currentUser)
}

export function register(
  credentials: { email: string; username: string; password: string },
  csrfToken: string,
): Promise<AuthUser> {
  return apiRequest(`${authPath}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
    body: JSON.stringify(credentials),
  }).then(currentUser)
}

export function login(credentials: { email: string; password: string }, csrfToken: string): Promise<AuthUser> {
  return apiRequest(`${authPath}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
    body: JSON.stringify(credentials),
  }).then(currentUser)
}

export function logout(csrfToken: string): Promise<void> {
  return apiRequest(`${authPath}/logout`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
    body: '{}',
  })
}
