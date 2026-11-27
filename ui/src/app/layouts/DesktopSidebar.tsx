import { Link } from 'react-router-dom'
import { Compass } from 'lucide-react'
import type { NavigationMode } from '../navigation'
import { Navigation } from './Navigation'
import styles from './AppShell.module.css'

export function Brand() {
  return <Link to="/quests" className={styles.brand} aria-label="SideQuest home">
    <span className={styles.brandIcon}><Compass size={24} aria-hidden="true" /></span>SideQuest
  </Link>
}

export function DesktopSidebar({ mode, isModerator = false }: { mode: NavigationMode; isModerator?: boolean }) {
  return <aside className={styles.sidebar}>
    <Brand />
    <div className={styles.navSection}><p className={styles.eyebrow}>Your adventure</p><Navigation mode={mode} isModerator={isModerator} /></div>
    <div className={styles.sidebarFoot}><span className={styles.eyebrow}>One quest at a time</span><p>Small challenges.<br />Real momentum.</p></div>
  </aside>
}
