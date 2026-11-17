import { useId, type InputHTMLAttributes } from 'react'
import styles from './Input.module.css'

type Props = InputHTMLAttributes<HTMLInputElement> & { label: string; hint?: string; error?: string }

export function Input({ label, hint, error, id, className = '', required, 'aria-describedby': describedBy, ...props }: Props) {
  const generatedId = useId()
  const inputId = id ?? generatedId
  const description = [describedBy, hint && `${inputId}-hint`, error && `${inputId}-error`].filter(Boolean).join(' ')
  return <div className={styles.field}>
    <label htmlFor={inputId}>{label}{required && <span> (required)</span>}</label>
    <input {...props} id={inputId} required={required} aria-invalid={error ? true : props['aria-invalid']}
      aria-describedby={description || undefined} className={`${styles.input} ${className}`} />
    {hint && <p id={`${inputId}-hint`} className={styles.hint}>{hint}</p>}
    {error && <p id={`${inputId}-error`} className={styles.error}>{error}</p>}
  </div>
}
