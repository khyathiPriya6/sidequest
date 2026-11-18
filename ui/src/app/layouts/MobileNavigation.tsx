import type { NavigationMode } from '../navigation'
import { Navigation } from './Navigation'

export function MobileNavigation({ mode }: { mode: NavigationMode }) {
  return mode === 'member-preview' ? <Navigation mode={mode} mobile /> : null
}
