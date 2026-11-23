import { useState } from 'react'
import { Link, useLocation } from 'react-router-dom'
import { Button } from '../../components/ui/Button/Button'
import { useAuth } from '../auth/AuthContext'
import { reportReasons, sendReport, type ReportReason } from './safetyApi'

export function ReportQuest({ questId }: { questId: string }) {
  const { state } = useAuth()
  const location = useLocation()
  const [reason, setReason] = useState<ReportReason>('DANGEROUS_ACT')
  const [open, setOpen] = useState(false)
  const [busy, setBusy] = useState(false)
  const [message, setMessage] = useState('')
  if (state.status !== 'authenticated') return <p>See something unsafe? <Link to="/login"
    state={{ from: { pathname: location.pathname, search: location.search } }}>Sign in to report</Link>.</p>
  async function submit() {
    if (busy) return
    setBusy(true); setMessage('')
    try {
      await sendReport(questId, reason)
      setMessage('Report received. A moderator can review this Quest.')
      setOpen(false)
    } catch (error) {
      setMessage(error instanceof Error ? error.message : 'Unable to submit report.')
    } finally { setBusy(false) }
  }
  return <section aria-label="Report unsafe quest" style={{ display: 'grid', gap: 'var(--space-3)' }}>
    {!open && <Button variant="secondary" onClick={() => setOpen(true)}>Report this quest</Button>}
    {open && <div style={{ display: 'grid', gap: 'var(--space-3)' }}>
      <label htmlFor="report-reason">Why are you reporting this quest?</label>
      <select id="report-reason" value={reason} onChange={event => setReason(event.target.value as ReportReason)}>
        {reportReasons.map(value => <option key={value} value={value}>{value.replaceAll('_', ' ').toLowerCase()}</option>)}
      </select>
      <p>Reports are private. Submitting a report does not automatically remove the Quest.</p>
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        <Button disabled={busy} onClick={() => void submit()}>{busy ? 'Submitting…' : 'Submit report'}</Button>
        <Button variant="secondary" disabled={busy} onClick={() => setOpen(false)}>Cancel</Button>
      </div>
    </div>}
    {message && <p role="status">{message}</p>}
  </section>
}
