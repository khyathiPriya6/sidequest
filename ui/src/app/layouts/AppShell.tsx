import { useEffect, useRef, type ReactNode } from 'react'
import { Link, Outlet, useLocation, useMatches } from 'react-router-dom'
import type { NavigationMode } from '../navigation'
import { DesktopSidebar } from './DesktopSidebar'
import { AppHeader } from './AppHeader'
import { MobileNavigation } from './MobileNavigation'
import HealthStatus from '../../features/health/HealthStatus'
import { useAuth } from '../../features/auth/AuthContext'
import styles from './AppShell.module.css'

export function AppShell({ mode, children }: { mode?: NavigationMode; children?: ReactNode }) {
  const { pathname } = useLocation()
  const matches = useMatches()
  const { state: auth } = useAuth()
  const activeMode = mode ?? (auth.status === 'authenticated' ? 'authenticated' : 'guest')
  const main = useRef<HTMLElement>(null)
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
    <DesktopSidebar mode={activeMode} />
    <div className={styles.workspace}>
      <AppHeader mode={activeMode} />
      <main id="main-content" ref={main} tabIndex={-1} className={styles.main}>{children ?? <Outlet />}</main>
      <footer className={styles.footer}>
        <span>SideQuest · Your story, one quest at a time.</span>
        {import.meta.env.DEV && <Link to="/dev/components">Component preview</Link>}
        <details><summary>Service status</summary><HealthStatus /></details>
      </footer>
    </div>
    <MobileNavigation mode={activeMode} />
  </div>
}
