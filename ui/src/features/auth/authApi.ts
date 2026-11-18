export interface AuthUser {
  id: string
  email: string
  username: string
}

export interface ProblemFieldError {
  field: string
  code: string
  message: string
}

interface ProblemDetails {
  title?: string
  detail?: string
  code?: string
  errors?: ProblemFieldError[]
}

export class ApiError extends Error {
  constructor(
    readonly status: number,
    readonly code: string,
    message: string,
    readonly fieldErrors: ProblemFieldError[] = [],
  ) {
    super(message)
    this.name = 'ApiError'
  }
}

const authPath = '/api/v1/auth'
export const authExpiredEvent = 'sidequest:auth-expired'

export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  const response = await fetch(path, {
    ...init,
    credentials: 'same-origin',
    headers: {
      Accept: 'application/json, application/problem+json',
      ...init?.headers,
    },
  })

  let body: unknown
  if (response.status !== 204) {
    try {
      body = await response.json()
    } catch {
      throw new ApiError(response.status, 'invalid_response', 'The server returned an unreadable response.')
    }
  }

  if (!response.ok) {
    const problem = body && typeof body === 'object' ? body as ProblemDetails : {}
    if (response.status === 401 && !path.endsWith('/login') && !path.endsWith('/register') && !path.endsWith('/me')) {
      window.dispatchEvent(new Event(authExpiredEvent))
    }
    const detail = typeof problem.detail === 'string' ? problem.detail : undefined
    const title = typeof problem.title === 'string' ? problem.title : undefined
    const code = typeof problem.code === 'string' ? problem.code : 'request_failed'
    const fieldErrors = Array.isArray(problem.errors) ? problem.errors.filter((error): error is ProblemFieldError =>
      error !== null && typeof error === 'object'
      && typeof error.field === 'string' && typeof error.code === 'string' && typeof error.message === 'string') : []
    throw new ApiError(
      response.status,
      code,
      detail ?? title ?? 'The request could not be completed.',
      fieldErrors,
    )
  }
  return body as T
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
  return apiRequest(`${authPath}/me`)
}

export function register(
  credentials: { email: string; username: string; password: string },
  csrfToken: string,
): Promise<AuthUser> {
  return apiRequest(`${authPath}/register`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
    body: JSON.stringify(credentials),
  })
}

export function login(credentials: { email: string; password: string }, csrfToken: string): Promise<AuthUser> {
  return apiRequest(`${authPath}/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
    body: JSON.stringify(credentials),
  })
}

export function logout(csrfToken: string): Promise<void> {
  return apiRequest(`${authPath}/logout`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json', 'X-CSRF-TOKEN': csrfToken },
    body: '{}',
  })
}
