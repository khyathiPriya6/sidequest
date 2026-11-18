import { Navigate, Outlet, useLocation } from 'react-router-dom'
import { useAuth } from './AuthContext'

export function RequireAuth() {
  const { state, refresh } = useAuth()
  const location = useLocation()

  if (state.status === 'loading') return <p role="status">Restoring your session…</p>
  if (state.status === 'error') return <section aria-labelledby="session-error-title">
    <h1 id="session-error-title">We couldn’t restore your session</h1>
    <p>{state.message}</p>
    <button type="button" onClick={() => void refresh()}>Try again</button>
  </section>
  if (state.status === 'anonymous') return <Navigate to="/login" replace state={{ from: location }} />
  return <Outlet />
}
