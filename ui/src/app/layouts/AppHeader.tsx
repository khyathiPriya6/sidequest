import { useRef, useState } from 'react'
import { Link, useNavigate } from 'react-router-dom'
import type { NavigationMode } from '../navigation'
import { Badge } from '../../components/ui/Badge/Badge'
import { Button } from '../../components/ui/Button/Button'
import { beforeSignOutEvent, useAuth } from '../../features/auth/AuthContext'
import { Brand } from './DesktopSidebar'
import styles from './AppShell.module.css'

export function AppHeader({ mode }: { mode: NavigationMode }) {
  const { state, logout } = useAuth()
  const navigate = useNavigate()
  const signingOut = useRef(false)
  const [pending, setPending] = useState(false)
  const [logoutError, setLogoutError] = useState('')

  async function performSignOut() {
    if (signingOut.current) return
    signingOut.current = true
    setPending(true)
    setLogoutError('')
    try {
      const request = logout()
      navigate('/quests', { replace: true })
      await request
    } catch {
      setLogoutError('We couldn’t sign you out. Check your connection and try again.')
    } finally {
      signingOut.current = false
      setPending(false)
    }
  }
  function signOut() {
    const event = new CustomEvent<() => void>(beforeSignOutEvent, { cancelable: true, detail: () => void performSignOut() })
    if (window.dispatchEvent(event)) void performSignOut()
  }

  return <header className={styles.header}>
    <div className={styles.mobileBrand}><Brand /></div>
    <span className={styles.headerCaption}>A little adventure goes a long way</span>
    {mode === 'guest' ? <div className={styles.guestActions}>
      <Link className={styles.signIn} to="/login">Sign in</Link>
      <Link className={styles.register} to="/register">Create account</Link>
    </div> : mode === 'member-preview' ? <Badge tone="accent">Member layout preview</Badge> : <div className={styles.memberActions}>
      {state.status === 'authenticated' && <span className={styles.username}>@{state.user.username}</span>}
      <Button variant="secondary" size="small" loading={pending} onClick={signOut}>Sign out</Button>
    </div>}
    {state.status === 'authenticated' && state.warning && <p className={styles.logoutError} role="alert">{state.warning}</p>}
    {logoutError && <p className={styles.logoutError} role="alert">{logoutError}</p>}
  </header>
}
