import { createContext, useCallback, useContext, useEffect, useRef, useState, type ReactNode } from 'react'
import { sessionCheckEvent } from '../../lib/api'
import * as authApi from './authApi'
import { ApiError, authExpiredEvent, type AuthUser } from './authApi'

type AuthState =
  | { status: 'loading' }
  | { status: 'anonymous' }
  | { status: 'error'; message: string }
  | { status: 'authenticated'; user: AuthUser; warning?: string }

interface AuthContextValue {
  state: AuthState
  refresh: () => Promise<void>
  login: (credentials: { email: string; password: string }) => Promise<AuthUser>
  register: (credentials: { email: string; username: string; password: string }) => Promise<AuthUser>
  logout: () => Promise<void>
}

export const beforeSignOutEvent = 'sidequest:before-sign-out'

const AuthContext = createContext<AuthContextValue | null>(null)

function messageOf(error: unknown): string {
  return error instanceof Error ? error.message : 'The authentication service is unavailable.'
}

export function AuthProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<AuthState>({ status: 'loading' })
  const csrfToken = useRef<string | null>(null)
  const authVersion = useRef(0)

  const refresh = useCallback(async () => {
    const version = ++authVersion.current
    setState({ status: 'loading' })
    try {
      const token = (await authApi.getCsrfToken()).csrfToken
      if (version !== authVersion.current) return
      csrfToken.current = token
      try {
        const user = await authApi.getCurrentUser()
        if (version !== authVersion.current) return
        setState({ status: 'authenticated', user })
      } catch (error) {
        if (version !== authVersion.current) return
        if (error instanceof ApiError && error.status === 401) {
          setState({ status: 'anonymous' })
          return
        }
        throw error
      }
    } catch (error) {
      if (version !== authVersion.current) return
      csrfToken.current = null
      setState({ status: 'error', message: messageOf(error) })
    }
  }, [])

  useEffect(() => {
    void refresh()
    const expire = () => {
      authVersion.current++
      csrfToken.current = null
      setState({ status: 'anonymous' })
    }
    const verify = async () => {
      csrfToken.current = null
      const version = ++authVersion.current
      try {
        const user = await authApi.getCurrentUser()
        if (version === authVersion.current) setState({ status: 'authenticated', user })
      } catch (error) {
        if (version !== authVersion.current) return
        if (error instanceof ApiError && error.status === 401) expire()
        else setState({ status: 'error', message: 'Your session could not be verified. Restore it before continuing.' })
      }
    }
    window.addEventListener(authExpiredEvent, expire)
    window.addEventListener(sessionCheckEvent, verify)
    return () => {
      authVersion.current++
      window.removeEventListener(authExpiredEvent, expire)
      window.removeEventListener(sessionCheckEvent, verify)
    }
  }, [refresh])

  const ensureCsrf = useCallback(async () => {
    if (csrfToken.current) return csrfToken.current
    csrfToken.current = (await authApi.getCsrfToken()).csrfToken
    return csrfToken.current
  }, [])

  const establish = useCallback(async (authenticate: (token: string) => Promise<AuthUser>) => {
    const user = await authenticate(await ensureCsrf())
    const version = ++authVersion.current
    csrfToken.current = null
    setState({ status: 'authenticated', user })
    try {
      const token = (await authApi.getCsrfToken()).csrfToken
      if (version === authVersion.current) csrfToken.current = token
    } catch (error) {
      if (version !== authVersion.current) return user
      setState({
        status: 'authenticated',
        user,
        warning: `You are signed in, but the security token could not be refreshed. Sign out and try again. (${messageOf(error)})`,
      })
    }
    return user
  }, [ensureCsrf])

  const signIn = useCallback((credentials: { email: string; password: string }) =>
    establish(token => authApi.login(credentials, token)), [establish])

  const signUp = useCallback((credentials: { email: string; username: string; password: string }) =>
    establish(token => authApi.register(credentials, token)), [establish])

  const signOut = useCallback(async () => {
    try {
      await authApi.logout(await ensureCsrf())
    } catch (error) {
      if (error instanceof ApiError && error.status === 403 && error.code === 'csrf_invalid') {
        csrfToken.current = null
        await ensureCsrf()
        throw error
      }
      if (!(error instanceof ApiError && error.status === 401)) throw error
    }
    authVersion.current++
    csrfToken.current = null
    setState({ status: 'anonymous' })
  }, [ensureCsrf])

  return <AuthContext.Provider value={{ state, refresh, login: signIn, register: signUp, logout: signOut }}>
    {children}
  </AuthContext.Provider>
}

export function useAuth(): AuthContextValue {
  const context = useContext(AuthContext)
  if (!context) throw new Error('useAuth must be used within AuthProvider')
  return context
}
