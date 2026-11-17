import type { ButtonHTMLAttributes } from 'react'
import styles from './Button.module.css'

type Props = ButtonHTMLAttributes<HTMLButtonElement> & {
  variant?: 'primary' | 'secondary' | 'ghost' | 'destructive'
  size?: 'small' | 'medium'
  loading?: boolean
}

export function Button({ variant = 'primary', size = 'medium', loading = false,
  disabled, type = 'button', className = '', children, ...props }: Props) {
  return <button {...props} type={type} disabled={disabled || loading} aria-busy={loading || undefined}
    className={`${styles.button} ${styles[variant]} ${styles[size]} ${className}`}>
    {loading && <span className={styles.spinner} aria-hidden="true" />}{children}
  </button>
}
