import { useRef, useState, type FormEvent } from 'react'
import { Link, Navigate, useLocation, useNavigate } from 'react-router-dom'
import { Button } from '../../components/ui/Button/Button'
import { Card } from '../../components/ui/Card/Card'
import { Input } from '../../components/ui/Input/Input'
import { useAuth } from './AuthContext'
import { ApiError } from './authApi'
import { authReturnPath } from './safeReturnPath'
import styles from './AuthPage.module.css'

type Mode = 'login' | 'register'
type Fields = { email: string; username: string; password: string }
type FieldErrors = Partial<Record<keyof Fields, string>>

function validate(fields: Fields, mode: Mode): FieldErrors {
  const errors: FieldErrors = {}
  const email = fields.email.trim()
  if (!email || email.length > 254 || !/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
    errors.email = 'Enter a valid email address of at most 254 characters.'
  }
  if (mode === 'register' && !/^[A-Za-z0-9_]{3,30}$/.test(fields.username)) {
    errors.username = 'Use 3–30 letters, digits, or underscores.'
  }
  const passwordLength = [...fields.password].length
  if (passwordLength < 12 || passwordLength > 128) {
    errors.password = 'Enter a password of 12–128 Unicode characters.'
  }
  return errors
}

function failureMessage(error: unknown, mode: Mode): string {
  if (!(error instanceof ApiError)) return 'We couldn’t reach the authentication service. Check your connection and try again.'
  if (error.status === 401 && mode === 'login') return 'We couldn’t sign you in with those details. Check your email and password.'
  if (error.status === 403) return 'Your security check expired. Please try again.'
  if (error.status === 409) return 'An account could not be created with those details.'
  return error.message
}

export function AuthPage({ mode }: { mode: Mode }) {
  const { state, login, register, refresh } = useAuth()
  const location = useLocation()
  const navigate = useNavigate()
  const returnTo = authReturnPath(location.state, location.search)
  const [fields, setFields] = useState<Fields>({ email: '', username: '', password: '' })
  const [errors, setErrors] = useState<FieldErrors>({})
  const [formError, setFormError] = useState('')
  const [pending, setPending] = useState(false)
  const form = useRef<HTMLFormElement>(null)

  if (state.status === 'authenticated') return <Navigate to={returnTo} replace />

  async function submit(event: FormEvent<HTMLFormElement>) {
    event.preventDefault()
    if (pending) return
    const validationErrors = validate(fields, mode)
    setErrors(validationErrors)
    setFormError('')
    const firstInvalidField = Object.keys(validationErrors)[0]
    if (firstInvalidField) {
      const control = form.current?.elements.namedItem(firstInvalidField)
      if (control instanceof HTMLElement) control.focus()
      return
    }

    setPending(true)
    try {
      if (mode === 'register') {
        await register({ email: fields.email.trim(), username: fields.username, password: fields.password })
      } else {
        await login({ email: fields.email.trim(), password: fields.password })
      }
      navigate(returnTo, { replace: true })
    } catch (error) {
      if (error instanceof ApiError && error.code === 'csrf_invalid') await refresh()
      const serverErrors: FieldErrors = {}
      if (error instanceof ApiError) {
        for (const fieldError of error.fieldErrors) {
          if (fieldError.field === 'email' || fieldError.field === 'username' || fieldError.field === 'password') {
            serverErrors[fieldError.field] ??= fieldError.message
          }
        }
      }
      setErrors(serverErrors)
      setFormError(failureMessage(error, mode))
      const firstServerError = Object.keys(serverErrors)[0]
      const control = firstServerError ? form.current?.elements.namedItem(firstServerError) : null
      if (control instanceof HTMLElement) control.focus()
    } finally {
      setPending(false)
    }
  }

  function update(field: keyof Fields, value: string) {
    setFields(current => ({ ...current, [field]: value }))
    setErrors(current => ({ ...current, [field]: undefined }))
    setFormError('')
  }

  const alternate = mode === 'login' ? 'register' : 'login'

  return <section className={styles.page} aria-labelledby="auth-title">
    <Card className={styles.card}>
      <p className={styles.eyebrow}>Your next adventure starts here</p>
      <h1 id="auth-title">{mode === 'login' ? 'Welcome back' : 'Create your account'}</h1>
      <p className={styles.description}>
        {mode === 'login' ? 'Sign in to pick up where your quest log left off.' : 'Make an account to create and share your quests.'}
      </p>
      <form ref={form} className={styles.form} onSubmit={submit} noValidate aria-busy={pending}>
        <Input
          label="Email"
          name="email"
          type="email"
          autoComplete="email"
          required
          value={fields.email}
          error={errors.email}
          onChange={event => update('email', event.target.value)}
        />
        {mode === 'register' && <Input
          label="Username"
          name="username"
          autoComplete="username"
          required
          hint="3–30 letters, numbers, or underscores. Your username is public."
          value={fields.username}
          error={errors.username}
          onChange={event => update('username', event.target.value)}
        />}
        <Input
          label="Password"
          name="password"
          type="password"
          autoComplete={mode === 'login' ? 'current-password' : 'new-password'}
          required
          hint={mode === 'register' ? 'Use 12–128 Unicode characters. Spaces are allowed.' : undefined}
          value={fields.password}
          error={errors.password}
          onChange={event => update('password', event.target.value)}
        />
        {formError && <p className={styles.formError} role="alert">{formError}</p>}
        <Button type="submit" className={styles.submit} loading={pending}>
          {pending ? 'Please wait…' : mode === 'login' ? 'Sign in' : 'Create account'}
        </Button>
      </form>
      <p className={styles.switch}>
        {mode === 'login' ? 'New to SideQuest?' : 'Already have an account?'}{' '}
        <Link to={`/${alternate}`} state={{ from: { pathname: returnTo } }}>
          {mode === 'login' ? 'Create an account' : 'Sign in'}
        </Link>
      </p>
    </Card>
  </section>
}
