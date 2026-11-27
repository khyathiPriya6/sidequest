import type { NavigationMode } from '../navigation'
import { Navigation } from './Navigation'

export function MobileNavigation({ mode, isModerator = false }: { mode: NavigationMode; isModerator?: boolean }) {
  return mode !== 'guest' ? <Navigation mode={mode} mobile isModerator={isModerator} /> : null
}
