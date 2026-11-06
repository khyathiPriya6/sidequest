import { useEffect, useState } from 'react'

type Connection = 'loading' | 'online' | 'offline'

export default function HealthStatus() {
  const [connection, setConnection] = useState<Connection>('loading')
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    setConnection('loading')
    const timeout = window.setTimeout(() => {
      controller.abort()
      if (active) setConnection('offline')
    }, 8000)

    async function checkHealth() {
      try {
        const response = await fetch('/api/v1/health', { signal: controller.signal })
        if (!response.ok) throw new Error('Health request failed')
        const body: unknown = await response.json()
        if (!body || typeof body !== 'object' || !('status' in body)
          || body.status !== 'UP' || !('service' in body) || body.service !== 'sidequest-api') {
          throw new Error('Unexpected health response')
        }
        if (active && !controller.signal.aborted) setConnection('online')
      } catch {
        if (active) setConnection('offline')
      } finally {
        window.clearTimeout(timeout)
      }
    }

    void checkHealth()
    return () => {
      active = false
      window.clearTimeout(timeout)
      controller.abort()
    }
  }, [attempt])

  return (
    <div className="api-status">
      <span role="status" aria-live="polite">
        <span className={`status-dot ${connection}`} aria-hidden="true" />
        {connection === 'loading' ? 'Connecting to API…'
          : connection === 'online' ? 'API online'
            : 'Unable to reach the API. Please try again.'}
      </span>
      {connection === 'offline' && (
        <button type="button" onClick={() => setAttempt(value => value + 1)}>Retry connection</button>
      )}
    </div>
  )
}
