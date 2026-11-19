export function safeReturnPath(value: unknown, origin = window.location.origin): string {
  if (typeof value !== 'string' || !value.startsWith('/') || value.startsWith('//') || value.includes('\\')
    || /[\u0000-\u001f]/.test(value)) return '/quests'

  try {
    const target = new URL(value, origin)
    if (target.origin !== origin || target.pathname === '/login' || target.pathname === '/register') return '/quests'
    return `${target.pathname}${target.search}${target.hash}`
  } catch {
    return '/quests'
  }
}

export function authReturnPath(state: unknown, search: string, origin = window.location.origin): string {
  if (state && typeof state === 'object' && 'from' in state) {
    const from = (state as { from?: unknown }).from
    if (from && typeof from === 'object' && 'pathname' in from) {
      const location = from as { pathname?: unknown; search?: unknown; hash?: unknown }
      const pathname = typeof location.pathname === 'string' ? location.pathname : ''
      const query = typeof location.search === 'string' ? location.search : ''
      const hash = typeof location.hash === 'string' ? location.hash : ''
      return safeReturnPath(`${pathname}${query}${hash}`, origin)
    }
  }
  return safeReturnPath(new URLSearchParams(search).get('returnTo'), origin)
}
