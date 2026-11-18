// @vitest-environment jsdom
import { act, cleanup, fireEvent, render, screen, waitFor } from '@testing-library/react'
import { afterEach, describe, expect, it, vi } from 'vitest'
import HealthStatus from './HealthStatus'

afterEach(() => {
  cleanup()
  vi.unstubAllGlobals()
  vi.useRealTimers()
})

describe('API health connection', () => {
  it('shows loading until the actual backend response arrives', async () => {
    let respond!: (value: Response) => void
    const fetchMock = vi.fn(() => new Promise<Response>(resolve => { respond = resolve }))
    vi.stubGlobal('fetch', fetchMock)
    render(<HealthStatus />)
    expect(screen.getByRole('status').textContent).toContain('Connecting')
    respond(Response.json({ status: 'UP', service: 'sidequest-api' }))
    await waitFor(() => expect(screen.getByRole('status').textContent).toContain('API online'))
    expect(fetchMock).toHaveBeenCalledWith('/api/v1/health', { signal: expect.any(AbortSignal) })
  })

  it.each(['network', 'http', 'invalid-json', 'wrong-service', 'down'])(
    'shows a useful error for %s failures and allows recovery', async failure => {
      const fetchMock = vi.fn()
      if (failure === 'network') fetchMock.mockRejectedValueOnce(new TypeError('Failed to fetch'))
      else if (failure === 'http') fetchMock.mockResolvedValueOnce(new Response('', { status: 503 }))
      else if (failure === 'invalid-json') fetchMock.mockResolvedValueOnce(new Response('<html>Oops</html>'))
      else fetchMock.mockResolvedValueOnce(Response.json({
        status: failure === 'down' ? 'DOWN' : 'UP',
        service: failure === 'wrong-service' ? 'other-api' : 'sidequest-api',
      }))
      fetchMock.mockResolvedValueOnce(Response.json({ status: 'UP', service: 'sidequest-api' }))
      vi.stubGlobal('fetch', fetchMock)
      render(<HealthStatus />)
      const retry = await screen.findByRole('button', { name: 'Retry connection' })
      expect(screen.getByRole('status').textContent).toContain('Unable to reach')
      fireEvent.click(retry)
      await waitFor(() => expect(screen.getByRole('status').textContent).toContain('API online'))
    },
  )

  it('times out a stalled connection and aborts its request', () => {
    vi.useFakeTimers()
    const fetchMock = vi.fn(() => new Promise<Response>(() => {}))
    vi.stubGlobal('fetch', fetchMock)
    render(<HealthStatus />)
    act(() => { vi.advanceTimersByTime(8000) })
    expect(screen.getByRole('status').textContent).toContain('Unable to reach')
    expect((fetchMock.mock.calls[0] as unknown as [string, RequestInit])[1].signal?.aborted).toBe(true)
  })
})
