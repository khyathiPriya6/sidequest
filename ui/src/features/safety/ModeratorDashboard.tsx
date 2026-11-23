import { useEffect, useState } from 'react'
import { Card } from '../../components/ui/Card/Card'
import { Button } from '../../components/ui/Button/Button'
import { ApiError } from '../../lib/api'
import { decideReview, listReviews, type ReviewItem, type ModerationAction, type ReviewPage } from './safetyApi'

export function ModeratorDashboard() {
  const [page, setPage] = useState(0)
  const [revision, setRevision] = useState(0)
  const [data, setData] = useState<ReviewPage | null>(null)
  const [loading, setLoading] = useState(true)
  const [error, setError] = useState('')
  const [restricted, setRestricted] = useState(false)
  const [busyId, setBusyId] = useState('')
  const [reason, setReason] = useState('DANGEROUS_ACT')
  const [confirm, setConfirm] = useState<{ item: ReviewItem; action: ModerationAction } | null>(null)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true); setError('')
    void listReviews(page, controller.signal).then(value => {
      if (!controller.signal.aborted) { setData(value); setRestricted(false) }
    }).catch(e => {
      if (!controller.signal.aborted) {
        setError(e instanceof Error ? e.message : 'Could not load review queue.')
        setRestricted(e instanceof ApiError && e.status === 403)
      }
    }).finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [page, revision])

  async function decide() {
    if (!confirm || busyId) return
    const chosen = confirm
    setBusyId(chosen.item.questId)
    setError('')
    try {
      await decideReview(chosen.item, chosen.action, chosen.action === 'APPROVE' ? '' : reason)
      setConfirm(null)
      setRevision(n => n + 1) // refresh authoritative server state, never replay
    } catch (e) {
      setConfirm(null)
      setError(e instanceof Error ? e.message : 'Could not save decision. Reload the queue before retrying.')
      setRevision(n => n + 1)
    } finally { setBusyId('') }
  }

  return <section aria-labelledby="moderation-title" style={{ display: 'grid', gap: 'var(--space-4)' }}>
    <h1 id="moderation-title">Quest moderation</h1>
    <p>Only authorized moderators can use this queue. Decisions are recorded in an audit log.</p>
    {loading && <Card role="status">Loading review queue…</Card>}
    {error && <Card role="alert"><p>{restricted ? 'You do not have moderator access.' : error}</p>
      {!restricted && <Button variant="secondary" onClick={() => setRevision(n => n + 1)}>Reload</Button>}
    </Card>}
    {!loading && !restricted && data?.content.length === 0 && <Card>No quests need review.</Card>}
    {!loading && !restricted && data?.content.map(item => <Card key={item.questId} style={{ display: 'grid', gap: 'var(--space-3)' }}>
      <h2>{item.title}</h2>
      <p style={{ whiteSpace: 'pre-wrap' }}>{item.description}</p>
      <p>Status: {item.state.replaceAll('_', ' ')} · Quest: {item.questStatus}</p>
      <p>Reports: {item.reports} · Review version: {item.version}</p>
      {item.reasonCode && <p>Screening flag: {item.reasonCode.replaceAll('_', ' ')}</p>}
      {item.appealReason && <p><strong>Creator appeal:</strong> {item.appealReason}</p>}
      <p>Review the full context and policy before taking action. Do not decide solely from keywords or report counts.</p>
      <div style={{ display: 'flex', flexWrap: 'wrap', gap: 'var(--space-2)' }}>
        {(item.state === 'PENDING_REVIEW' || item.state === 'UNDER_REVIEW') && <>
          <Button disabled={!!busyId} onClick={() => setConfirm({ item, action: 'APPROVE' })}>Approve</Button>
          <Button disabled={!!busyId} variant="secondary" onClick={() => setConfirm({ item, action: 'REJECT' })}>Reject</Button>
        </>}
        {item.state === 'APPROVED' && item.questStatus === 'PUBLISHED' && <>
          <Button disabled={!!busyId} variant="destructive" onClick={() => setConfirm({ item, action: 'HIDE' })}>Hide quest</Button>
          {item.reports > 0 && <Button disabled={!!busyId} variant="secondary"
            onClick={() => setConfirm({ item, action: 'DISMISS_REPORTS' })}>Dismiss reports</Button>}
        </>}
      </div>
    </Card>)}
    {!loading && !restricted && data && data.totalPages > 1 && <nav aria-label="Review queue pages"
      style={{ display: 'flex', gap: 'var(--space-3)', alignItems: 'center' }}>
      <Button variant="secondary" disabled={!data.hasPrevious} onClick={() => setPage(n => n - 1)}>Previous</Button>
      <span>Page {data.page + 1} of {data.totalPages}</span>
      <Button variant="secondary" disabled={!data.hasNext} onClick={() => setPage(n => n + 1)}>Next</Button>
    </nav>}
    {confirm && <Card role="group" aria-label="Confirm moderation decision" style={{ display: 'grid', gap: 'var(--space-3)' }}>
      <h2>Confirm {confirm.action.toLowerCase()}?</h2>
      <p>{confirm.item.title}</p>
      {confirm.action !== 'APPROVE' && <>
        <label htmlFor="moderation-reason">Reason code</label>
        <select id="moderation-reason" value={reason} onChange={e => setReason(e.target.value)}>
          {['DANGEROUS_ACT', 'SELF_HARM', 'ILLEGAL', 'VIOLENCE_HARASSMENT',
            'HATE', 'EXPLOITATION', 'FRAUD_SPAM', 'PRIVACY', 'OTHER',
            'NO_POLICY_VIOLATION', 'DUPLICATE_OR_ABUSIVE_REPORTS'].map(code =>
            <option key={code} value={code}>{code.replaceAll('_', ' ')}</option>)}
        </select>
      </>}
      <div style={{ display: 'flex', gap: 'var(--space-2)' }}>
        <Button disabled={!!busyId} onClick={() => void decide()}>Confirm decision</Button>
        <Button variant="secondary" disabled={!!busyId} onClick={() => setConfirm(null)}>Cancel</Button>
      </div>
    </Card>}
  </section>
}
