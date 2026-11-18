import { useId } from 'react'
import styles from './XPProgressBar.module.css'

type Props = { earned: number; target: number; label?: string }
export function XPProgressBar({ earned, target, label = 'Earned XP progress' }: Props) {
  const id = useId()
  const valid = Number.isFinite(earned) && earned >= 0 && Number.isFinite(target) && target > 0
  const value = valid ? Math.min(earned, target) : 0
  return <div className={styles.container}>
    <div className={styles.labels}><span id={id}>{label}</span><span>{valid ? `${earned} / ${target} XP earned` : 'Progress unavailable'}</span></div>
    {valid && <progress className={styles.progress} aria-labelledby={id} value={value} max={target}
      aria-valuetext={`${earned} XP earned toward a ${target} XP target`} />}
  </div>
}
