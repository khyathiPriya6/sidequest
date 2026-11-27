import { useEffect, useRef, useState, type ReactNode } from 'react'
import { Link, Outlet, useLocation, useMatches } from 'react-router-dom'
import type { NavigationMode } from '../navigation'
import { DesktopSidebar } from './DesktopSidebar'
import { AppHeader } from './AppHeader'
import { MobileNavigation } from './MobileNavigation'
import HealthStatus from '../../features/health/HealthStatus'
import { useAuth } from '../../features/auth/AuthContext'
import { listReviews } from '../../features/safety/safetyApi'
import styles from './AppShell.module.css'

export function AppShell({ mode, children }: { mode?: NavigationMode; children?: ReactNode }) {
  const { pathname } = useLocation()
  const matches = useMatches()
  const { state: auth } = useAuth()
  const activeMode = mode ?? (auth.status === 'authenticated' ? 'authenticated' : 'guest')
  const main = useRef<HTMLElement>(null)
  const [moderatorId, setModeratorId] = useState<string | null>(null)
  const userId = auth.status === 'authenticated' ? auth.user.id : null
  useEffect(() => {
    const controller = new AbortController()
    if (mode === 'member-preview' || !userId) return () => controller.abort()
    // Authorization remains on the API: the UI link is only a convenience.
    void listReviews(0, controller.signal)
      .then(() => { if (!controller.signal.aborted) setModeratorId(userId) })
      .catch(() => { if (!controller.signal.aborted) setModeratorId(null) })
    return () => controller.abort()
  }, [userId, mode])
  const isModerator = userId !== null && moderatorId === userId
  const title = (matches.at(-1)?.handle as { title?: string } | undefined)?.title ?? 'SideQuest'
  useEffect(() => {
    document.title = `${title} · SideQuest`
    const frame = window.requestAnimationFrame(() => {
      if (!main.current?.contains(document.activeElement)) main.current?.focus({ preventScroll: true })
      window.scrollTo({ top: 0, behavior: 'instant' })
    })
    return () => window.cancelAnimationFrame(frame)
  }, [pathname, title])

  return <div className={`${styles.shell} ${activeMode !== 'guest' ? styles.member : ''}`}>
    <a className={styles.skip} href="#main-content">Skip to content</a>
    <DesktopSidebar mode={activeMode} isModerator={isModerator} />
    <div className={styles.workspace}>
      <AppHeader mode={activeMode} />
      <main id="main-content" ref={main} tabIndex={-1} className={styles.main}>{children ?? <Outlet />}</main>
      <footer className={styles.footer}>
        <span>SideQuest · Your story, one quest at a time.</span>
        {import.meta.env.DEV && <Link to="/dev/components">Component preview</Link>}
        <details><summary>Service status</summary><HealthStatus /></details>
      </footer>
    </div>
    <MobileNavigation mode={activeMode} isModerator={isModerator} />
  </div>
}
