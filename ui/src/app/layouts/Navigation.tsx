import { NavLink } from 'react-router-dom'
import { navigation, type NavigationMode } from '../navigation'
import styles from './AppShell.module.css'

export function Navigation({ mode, mobile = false }: { mode: NavigationMode; mobile?: boolean }) {
  return <nav aria-label={mobile ? 'Mobile navigation' : 'Main navigation'} className={mobile ? styles.mobileNavigation : styles.navigation}>
    {navigation.filter(item => mode !== 'guest' || !item.memberOnly).map(item => {
      const Icon = item.icon
      return <NavLink key={item.to} to={item.to} end className={({ isActive }) => `${styles.navLink} ${isActive ? styles.active : ''}`}>
        <Icon size={20} aria-hidden="true" /><span>{item.label}</span>
      </NavLink>
    })}
  </nav>
}
