import type { NavigationMode } from '../navigation'
import { Navigation } from './Navigation'

export function MobileNavigation({ mode }: { mode: NavigationMode }) {
  return mode !== 'guest' ? <Navigation mode={mode} mobile /> : null
}
