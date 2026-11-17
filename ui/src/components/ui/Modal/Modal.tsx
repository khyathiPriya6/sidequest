import { useEffect, useId, useRef, type ReactNode } from 'react'
import { Button } from '../Button/Button'
import styles from './Modal.module.css'

type Props = { open: boolean; onOpenChange: (open: boolean) => void; title: string; description?: string; children: ReactNode }
let openDialogs = 0
let previousOverflow = ''

export function Modal({ open, onOpenChange, title, description, children }: Props) {
  const ref = useRef<HTMLDialogElement>(null)
  const titleId = useId()
  const descriptionId = useId()

  useEffect(() => {
    const dialog = ref.current
    if (!open || !dialog) return
    const trigger = document.activeElement instanceof HTMLElement ? document.activeElement : null
    dialog.showModal()
    if (openDialogs++ === 0) {
      previousOverflow = document.body.style.overflow
      document.body.style.overflow = 'hidden'
    }
    return () => {
      dialog.close()
      if (--openDialogs === 0) document.body.style.overflow = previousOverflow
      if (trigger?.isConnected) trigger.focus()
    }
  }, [open])

  return <dialog ref={ref} className={styles.modal} aria-labelledby={titleId}
    aria-describedby={description ? descriptionId : undefined}
    onKeyDown={event => {
      if (event.key !== 'Tab') return
      const controls = Array.from(event.currentTarget.querySelectorAll<HTMLElement>(
        'button, a[href], input, select, textarea, [tabindex]',
      )).filter(element => element.tabIndex >= 0 && !element.matches(':disabled') && element.getClientRects().length > 0)
      const first = controls[0]
      const last = controls.at(-1)
      if (event.shiftKey && document.activeElement === first) {
        event.preventDefault()
        last?.focus()
      } else if (!event.shiftKey && document.activeElement === last) {
        event.preventDefault()
        first?.focus()
      }
    }}
    onCancel={event => { event.preventDefault(); onOpenChange(false) }}>
    <div className={styles.header}>
      <h2 id={titleId}>{title}</h2>
      <Button variant="ghost" size="small" aria-label="Close dialog" onClick={() => onOpenChange(false)}>Close</Button>
    </div>
    {description && <p id={descriptionId}>{description}</p>}
    <div className={styles.content}>{children}</div>
  </dialog>
}
