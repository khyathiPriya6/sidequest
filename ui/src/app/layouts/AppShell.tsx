import { useEffect, useRef, type ReactNode } from 'react'
import { Link, Outlet, useLocation, useMatches } from 'react-router-dom'
import type { NavigationMode } from '../navigation'
import { DesktopSidebar } from './DesktopSidebar'
import { AppHeader } from './AppHeader'
import { MobileNavigation } from './MobileNavigation'
import HealthStatus from '../../features/health/HealthStatus'
import styles from './AppShell.module.css'

export function AppShell({ mode = 'guest', children }: { mode?: NavigationMode; children?: ReactNode }) {
  const { pathname } = useLocation()
  const matches = useMatches()
  const main = useRef<HTMLElement>(null)
  const title = (matches.at(-1)?.handle as { title?: string } | undefined)?.title ?? 'SideQuest'
  useEffect(() => {
    document.title = `${title} · SideQuest`
    // Run after the browser's history restoration so it cannot steal route focus.
    const frame = window.requestAnimationFrame(() => {
      main.current?.focus({ preventScroll: true })
      window.scrollTo({ top: 0, behavior: 'instant' })
    })
    return () => window.cancelAnimationFrame(frame)
  }, [pathname, title])

  return <div className={`${styles.shell} ${mode === 'member-preview' ? styles.member : ''}`}>
    <a className={styles.skip} href="#main-content">Skip to content</a>
    <DesktopSidebar mode={mode} />
    <div className={styles.workspace}>
      <AppHeader mode={mode} />
      <main id="main-content" ref={main} tabIndex={-1} className={styles.main}>{children ?? <Outlet />}</main>
      <footer className={styles.footer}>
        <span>SideQuest · Your story, one quest at a time.</span>
        {import.meta.env.DEV && <Link to="/dev/components">Component preview</Link>}
        <details><summary>Service status</summary><HealthStatus /></details>
      </footer>
    </div>
    <MobileNavigation mode={mode} />
  </div>
}
