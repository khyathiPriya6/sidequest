export interface ProblemFieldError { field: string; code: string; message: string }
export class ApiError extends Error {
  constructor(readonly status: number, readonly code: string, message: string,
    readonly fieldErrors: ProblemFieldError[] = []) { super(message); this.name = 'ApiError' }
}
export const authExpiredEvent = 'sidequest:auth-expired'
export const sessionCheckEvent = 'sidequest:session-check'
export function isRecord(value: unknown): value is Record<string, unknown> {
  return value !== null && typeof value === 'object' && !Array.isArray(value)
}

// One transport/error boundary. Never retries requests, including uncertain mutations.
export async function apiResponse(path: string, init?: RequestInit): Promise<{ body: unknown; response: Response }> {
  let response: Response
  try {
    const headers = new Headers(init?.headers)
    headers.set('Accept', 'application/json, application/problem+json')
    response = await fetch(path, { ...init, credentials: 'same-origin', headers })
  } catch (error) {
    if (init?.signal?.aborted) throw error
    throw new ApiError(0, 'network_error', 'Unable to reach the service. Check your connection. A submitted change may have been saved; reload before trying it again.')
  }
  // Expiration is authoritative even if the response body is malformed.
  if (response.status === 401 && !['/api/v1/auth/login', '/api/v1/auth/register'].includes(path)) {
    window.dispatchEvent(new Event(authExpiredEvent))
  }
  let body: unknown
  if (response.status !== 204) {
    try { body = await response.json() } catch {
      if (response.status === 403) window.dispatchEvent(new Event(sessionCheckEvent))
      throw new ApiError(response.ok ? 502 : response.status, 'invalid_response', 'The service returned an unreadable response. Reload before trying a change again.')
    }
  }
  if (!response.ok) {
    const problem = isRecord(body) ? body : {}
    const code = typeof problem.code === 'string' ? problem.code : 'request_failed'
    if (response.status === 403 && (code === 'csrf_invalid' || code === 'request_failed')) {
      window.dispatchEvent(new Event(sessionCheckEvent))
    }
    const errors = Array.isArray(problem.errors) ? problem.errors.filter((e): e is ProblemFieldError =>
      isRecord(e) && typeof e.field === 'string' && typeof e.code === 'string' && typeof e.message === 'string') : []
    throw new ApiError(response.status, code,
      typeof problem.detail === 'string' ? problem.detail : typeof problem.title === 'string' ? problem.title : 'The request could not be completed.', errors)
  }
  return { body, response }
}
export async function apiRequest<T>(path: string, init?: RequestInit): Promise<T> {
  return (await apiResponse(path, init)).body as T
}
