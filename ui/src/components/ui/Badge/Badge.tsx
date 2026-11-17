import type { HTMLAttributes } from 'react'
import styles from './Badge.module.css'

type Props = HTMLAttributes<HTMLSpanElement> & { tone?: 'neutral' | 'accent' | 'reward' | 'success' | 'danger' }
export function Badge({ tone = 'neutral', className = '', ...props }: Props) {
  return <span {...props} className={`${styles.badge} ${styles[tone]} ${className}`} />
}
