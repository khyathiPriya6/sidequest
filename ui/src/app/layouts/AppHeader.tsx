import { useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { NavigationMode } from '../navigation'
import { Badge } from '../../components/ui/Badge/Badge'
import { Button } from '../../components/ui/Button/Button'
import { useAuth } from '../../features/auth/AuthContext'
import { Brand } from './DesktopSidebar'
import styles from './AppShell.module.css'

export function AppHeader({ mode }: { mode: NavigationMode }) {
  const { state, logout } = useAuth()
  const navigate = useNavigate()
  const [logoutError, setLogoutError] = useState('')

  async function signOut() {
    setLogoutError('')
    const request = logout()
    navigate('/quests', { replace: true })
    try {
      await request
    } catch {
      setLogoutError('We couldn’t sign you out. Check your connection and try again.')
    }
  }

  return <header className={styles.header}>
    <div className={styles.mobileBrand}><Brand /></div>
    <span className={styles.headerCaption}>A little adventure goes a long way</span>
    {mode === 'guest' ? <div className={styles.guestActions}>
      <Link className={styles.signIn} to="/login">Sign in</Link>
      <Link className={styles.register} to="/register">Create account</Link>
    </div> : mode === 'member-preview' ? <Badge tone="accent">Member layout preview</Badge> : <div className={styles.memberActions}>
      {state.status === 'authenticated' && <span className={styles.username}>@{state.user.username}</span>}
      <Button variant="secondary" size="small" onClick={() => void signOut()}>Sign out</Button>
    </div>}
    {state.status === 'authenticated' && state.warning && <p className={styles.logoutError} role="alert">{state.warning}</p>}
    {logoutError && <p className={styles.logoutError} role="alert">{logoutError}</p>}
  </header>
}
