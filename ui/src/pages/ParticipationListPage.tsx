import { useEffect, useState } from 'react'
import { Link, useSearchParams } from 'react-router-dom'
import { Badge } from '../components/ui/Badge/Badge'
import { Button } from '../components/ui/Button/Button'
import { Card } from '../components/ui/Card/Card'
import { getParticipations, type Page, type Participation, type ParticipationStatus } from '../features/progress/progressApi'
import styles from './MyQuestsPage.module.css'

export function ParticipationListPage({ status }: { status: ParticipationStatus }) {
  const [params, setParams] = useSearchParams()
  const page = Math.max(0, Number.parseInt(params.get('page') ?? '0', 10) || 0)
  const [state, setState] = useState<{ kind: 'loading' | 'error' | 'ready'; data?: Page<Participation>; message?: string }>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setState({ kind: 'loading' })
    void getParticipations({ status, page }, controller.signal).then(data => {
      if (!controller.signal.aborted) setState({ kind: 'ready', data })
    }).catch(error => {
      if (!controller.signal.aborted) setState({ kind: 'error', message: error instanceof Error ? error.message : 'Unable to load participations.' })
    })
    return () => controller.abort()
  }, [status, page, attempt])
  const label = status === 'IN_PROGRESS' ? 'In Progress' : 'Completed'
  function changePage(next: number) {
    const updated = new URLSearchParams(params)
    if (next > 0) updated.set('page', String(next))
    else updated.delete('page')
    setParams(updated)
  }
  return <section className={styles.page} aria-labelledby="my-quests-title">
    <h1 id="my-quests-title">{label} quests</h1>
    {state.kind === 'loading' && <Card role="status">Loading your {label.toLowerCase()} quests…</Card>}
    {state.kind === 'error' && <Card role="alert"><p>{state.message}</p><Button variant="secondary" onClick={() => setAttempt(v => v + 1)}>Try again</Button></Card>}
    {state.kind === 'ready' && state.data?.content.length === 0 && <Card className={styles.empty}>
      <h2>No {label.toLowerCase()} quests yet</h2>
      <Link to="/quests">Explore quests</Link>
    </Card>}
    {state.kind === 'ready' && <div className={styles.grid}>
      {state.data?.content.map(item => <Card key={item.id} className={styles.card}>
        <Badge tone={item.status === 'COMPLETED' ? 'success' : 'accent'}>{label}</Badge>
        <h2><Link to={`/participations/${encodeURIComponent(item.id)}`}>{item.quest.title}</Link></h2>
        <p>{item.quest.description}</p>
        <Badge tone="reward">{item.status === 'COMPLETED' ? item.awardedXp : item.rewardXp} XP</Badge>
        {item.quest.status === 'ARCHIVED' && <p>Archived quest · Your participation is still accessible.</p>}
        <Link to={`/participations/${encodeURIComponent(item.id)}`}>View participation</Link>
      </Card>)}
    </div>}
    {state.kind === 'ready' && state.data && state.data.totalPages > 1 && <nav className={styles.pagination} aria-label="Participation pages">
      <Button variant="secondary" disabled={!state.data.hasPrevious} onClick={() => changePage(page - 1)}>Previous</Button>
      <span>Page {state.data.page + 1} of {state.data.totalPages}</span>
      <Button variant="secondary" disabled={!state.data.hasNext} onClick={() => changePage(page + 1)}>Next</Button>
    </nav>}
  </section>
}
