import { Link } from 'react-router-dom'
import type { NavigationMode } from '../navigation'
import { Badge } from '../../components/ui/Badge/Badge'
import { Brand } from './DesktopSidebar'
import styles from './AppShell.module.css'

export function AppHeader({ mode }: { mode: NavigationMode }) {
  return <header className={styles.header}>
    <div className={styles.mobileBrand}><Brand /></div>
    <span className={styles.headerCaption}>A little adventure goes a long way</span>
    {mode === 'guest' ? <Link className={styles.signIn} to="/login">Sign in</Link> : <Badge tone="accent">Member layout preview</Badge>}
  </header>
}
