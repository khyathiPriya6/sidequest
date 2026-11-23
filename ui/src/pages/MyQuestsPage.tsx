import { useEffect, useState } from 'react'
import { ArrowUpRight, Plus } from 'lucide-react'
import { Link, useSearchParams } from 'react-router-dom'
import { Badge } from '../components/ui/Badge/Badge'
import { Button } from '../components/ui/Button/Button'
import { Card } from '../components/ui/Card/Card'
import { Modal } from '../components/ui/Modal/Modal'
import { getCsrfToken } from '../features/auth/authApi'
import {
  archiveQuest,
  getMyQuests,
  getOwnedQuest,
  publishQuest,
  QuestApiError,
  displayEnum,
  type Quest,
  type QuestPage,
  type QuestStatus,
} from '../features/quests/questApi'
import styles from './MyQuestsPage.module.css'
import { ParticipationListPage } from './ParticipationListPage'

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; page: QuestPage }
  | { kind: 'error'; message: string }

const statuses: Array<{ value: ''; label: string } | { value: QuestStatus; label: string }> = [
  { value: '', label: 'All quests' },
  { value: 'DRAFT', label: 'Drafts' },
  { value: 'PUBLISHED', label: 'Published' },
  { value: 'ARCHIVED', label: 'Archived' },
]

export function MyQuestsPage() {
  const [params, setParams] = useSearchParams()
  const tab = params.get('tab')
  function select(next: string) {
    const updated = new URLSearchParams()
    if (next) updated.set('tab', next)
    setParams(updated)
  }
  return <div className={styles.page}>
    <nav className={styles.tabs} aria-label="My quest sections">
      {[
        { value: 'in-progress', label: 'In Progress' },
        { value: 'completed', label: 'Completed' },
        { value: '', label: 'My Creations' },
      ].map(item => <button key={item.value} type="button" aria-current={(tab || '') === item.value ? 'page' : undefined}
        className={(tab || '') === item.value ? styles.active : ''} onClick={() => select(item.value)}>{item.label}</button>)}
    </nav>
    {tab === 'in-progress' ? <ParticipationListPage status="IN_PROGRESS" />
      : tab === 'completed' ? <ParticipationListPage status="COMPLETED" /> : <OwnedQuestsPage />}
  </div>
}

function OwnedQuestsPage() {
  const [searchParams, setSearchParams] = useSearchParams()
  const rawStatus = searchParams.get('status')
  const status: QuestStatus | '' = rawStatus === 'DRAFT' || rawStatus === 'PUBLISHED' || rawStatus === 'ARCHIVED'
    ? rawStatus
    : ''
  const page = Math.max(0, Number.parseInt(searchParams.get('page') ?? '0', 10) || 0)
  const [state, setState] = useState<LoadState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)

  useEffect(() => {
    const controller = new AbortController()
    let active = true
    setState({ kind: 'loading' })
    void getMyQuests(status, page, controller.signal).then(results => {
      if (active) setState({ kind: 'loaded', page: results })
    }).catch(error => {
      if (!active || controller.signal.aborted) return
      setState({ kind: 'error', message: error instanceof Error ? error.message : 'Unable to load your quests.' })
    })
    return () => {
      active = false
      controller.abort()
    }
  }, [status, page, attempt])

  function setStatus(nextStatus: QuestStatus | '') {
    const next = new URLSearchParams(searchParams)
    if (nextStatus) next.set('status', nextStatus)
    else next.delete('status')
    next.delete('page')
    setSearchParams(next)
  }

  function setPage(nextPage: number) {
    const next = new URLSearchParams(searchParams)
    if (nextPage > 0) next.set('page', String(nextPage))
    else next.delete('page')
    setSearchParams(next)
  }

  return <section className={styles.page} aria-labelledby="my-quests-title">
    <header className={styles.header}>
      <div>
        <p className={styles.eyebrow}>Your quest log</p>
        <h1 id="my-quests-title">My quests</h1>
      </div>
      <Link className={styles.create} to="/quests/new"><Plus size={18} aria-hidden="true" /> Create quest</Link>
    </header>

    <nav className={styles.tabs} aria-label="Quest status">
      {statuses.map(item => <button key={item.value || 'all'} type="button"
        aria-current={status === item.value ? 'page' : undefined}
        className={status === item.value ? styles.active : ''}
        onClick={() => setStatus(item.value)}>{item.label}</button>)}
    </nav>

    <div aria-live="polite" aria-busy={state.kind === 'loading'}>
      {state.kind === 'loading' && <Card className={styles.state} role="status">Loading your quests…</Card>}
      {state.kind === 'error' && <Card className={styles.state} role="alert">
        <h2>We couldn’t load your quests</h2>
        <p>{state.message}</p>
        <Button variant="secondary" onClick={() => setAttempt(value => value + 1)}>Try again</Button>
      </Card>}
      {state.kind === 'loaded' && state.page.content.length === 0 && <Card className={styles.empty}>
        <h2>{status ? `No ${status.toLowerCase()} quests yet.` : 'You haven’t created a quest yet.'}</h2>
        <p>{status
          ? 'Change the status filter or create a quest to add more to your log.'
          : 'Create your first quest and save it as a draft.'}</p>
        <Link className={styles.create} to="/quests/new"><Plus size={18} aria-hidden="true" /> Create quest</Link>
      </Card>}
      {state.kind === 'loaded' && state.page.content.length > 0 && <div className={styles.grid}>
        {state.page.content.map(quest => <OwnedQuestCard key={quest.id} quest={quest} onChanged={() => setAttempt(value => value + 1)} />)}
      </div>}
    </div>

    {state.kind === 'loaded' && state.page.totalPages > 1 && <nav className={styles.pagination} aria-label="My quest pages">
      <Button variant="secondary" disabled={!state.page.hasPrevious} onClick={() => setPage(state.page.page - 1)}>Previous</Button>
      <span aria-current="page">Page {state.page.page + 1} of {state.page.totalPages}</span>
      <Button variant="secondary" disabled={!state.page.hasNext} onClick={() => setPage(state.page.page + 1)}>Next</Button>
    </nav>}
  </section>
}

function OwnedQuestCard({ quest, onChanged }: { quest: Quest; onChanged: () => void }) {
  const [archiveOpen, setArchiveOpen] = useState(false)
  const [mutating, setMutating] = useState(false)
  const [actionError, setActionError] = useState('')
  const statusTone = quest.status === 'PUBLISHED' ? 'success' : quest.status === 'ARCHIVED' ? 'neutral' : 'accent'

  async function mutate(action: 'publish' | 'archive') {
    if (mutating) return
    setMutating(true)
    setActionError('')
    try {
      const owned = await getOwnedQuest(quest.id, new AbortController().signal)
      const csrfToken = (await getCsrfToken()).csrfToken
      if (action === 'publish') await publishQuest(quest.id, owned.etag, csrfToken)
      else await archiveQuest(quest.id, owned.etag, csrfToken)
      setArchiveOpen(false)
      onChanged()
    } catch (error) {
      if (error instanceof QuestApiError) {
        setActionError(error.status === 422
          ? 'Complete the publication fields before publishing.'
          : error.status === 412
            ? 'This quest changed in another tab or session. Reload before retrying.'
            : error.status === 409
              ? 'This action is not valid for the current quest state.'
              : error.message)
      } else {
        setActionError(error instanceof Error ? error.message : 'Unable to update this quest.')
      }
    } finally {
      setMutating(false)
    }
  }

  return <article aria-label={quest.title}>
    <Card className={styles.card}>
      <div className={styles.badges}>
        <Badge tone={statusTone}>{displayEnum(quest.status)}</Badge>
        {quest.category && <Badge>{displayEnum(quest.category)}</Badge>}
      </div>
      <h2>
        {quest.status === 'DRAFT'
          ? <Link className={styles.questLink} to={`/quests/${encodeURIComponent(quest.id)}${quest.status === 'DRAFT' ? '/edit' : ''}`}>
            {quest.title || 'Untitled quest'} <ArrowUpRight size={20} aria-hidden="true" />
          </Link>
          : <Link className={styles.questLink} to={`/quests/${encodeURIComponent(quest.id)}`}>
            {quest.title || 'Untitled quest'} <ArrowUpRight size={20} aria-hidden="true" />
          </Link>}
      </h2>
      <p>{quest.description || 'No description available.'}</p>
      {quest.rewardXp !== null && <Badge tone="reward">{quest.rewardXp} XP reward</Badge>}
      {quest.status !== 'ARCHIVED' && <div className={styles.actions}>
        {quest.status === 'DRAFT' && <Button size="small" loading={mutating} onClick={() => void mutate('publish')}>Publish</Button>}
        <Button size="small" variant="destructive" onClick={() => setArchiveOpen(true)}>Archive</Button>
      </div>}
      {actionError && <p className={styles.actionError} role="alert">{actionError}</p>}
      <Modal open={archiveOpen} onOpenChange={setArchiveOpen} title="Archive this quest?"
        description="The quest will leave public discovery. You can keep its draft or publication history in My quests.">
        <div className={styles.modalActions}>
          <Button variant="secondary" onClick={() => setArchiveOpen(false)}>Cancel</Button>
          <Button variant="destructive" loading={mutating} onClick={() => void mutate('archive')}>Confirm archive</Button>
        </div>
      </Modal>
    </Card>
  </article>
}
