import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, Sparkles } from 'lucide-react'
import { Link, useLocation, useNavigate, useParams } from 'react-router-dom'
import { useAuth } from '../features/auth/AuthContext'
import { safeReturnPath } from '../features/auth/safeReturnPath'
import { ApiError } from '../lib/api'
import { Badge } from '../components/ui/Badge/Badge'
import { Button } from '../components/ui/Button/Button'
import { Card } from '../components/ui/Card/Card'
import { Modal } from '../components/ui/Modal/Modal'
import { completeParticipation, getParticipation, getParticipations, joinQuest, type Participation } from '../features/progress/progressApi'
import { QuestApiError, displayEnum, getQuest, type Quest } from '../features/quests/questApi'
import styles from './QuestDetailPage.module.css'
import { ReportQuest } from '../features/safety/ReportQuest'

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; quest: Quest; participation: Participation | null }
  | { kind: 'unavailable' }
  | { kind: 'error'; message: string }

export function QuestDetailPage() {
  const { id = '' } = useParams()
  const { state: auth } = useAuth()
  const location = useLocation()
  const [state, setState] = useState<LoadState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const returnTo = typeof location.state === 'object' && location.state !== null
    && 'from' in location.state && typeof location.state.from === 'string'
    ? safeReturnPath(location.state.from)
    : `/quests${location.search}`

  const load = useCallback(async (signal: AbortSignal) => {
    setState({ kind: 'loading' })
    try {
      const quest = await getQuest(id, signal)
      const existing = auth.status === 'authenticated'
        ? await getParticipations({ questId: id }, signal)
        : null
      if (!signal.aborted) setState({ kind: 'loaded', quest, participation: existing?.content[0] ?? null })
    } catch (error) {
      if (signal.aborted) return
      if (error instanceof QuestApiError && error.status === 404) setState({ kind: 'unavailable' })
      else setState({ kind: 'error', message: error instanceof Error ? error.message : 'Unable to load this quest.' })
    }
  }, [auth.status, id])

  useEffect(() => {
    if (auth.status === 'loading') return
    const controller = new AbortController()
    void load(controller.signal)
    return () => controller.abort()
  }, [load, attempt, auth.status])

  return <section className={styles.page} aria-labelledby="quest-detail-title">
    <Link className={styles.back} to={returnTo}><ArrowLeft size={18} aria-hidden="true" /> Back to Explore</Link>
    {(auth.status === 'loading' || state.kind === 'loading') && <Card className={styles.state} role="status">Loading quest details…</Card>}
    {state.kind === 'unavailable' && <Card className={styles.state} role="status">
      <h1 id="quest-detail-title">Quest unavailable</h1>
      <p>This quest may have been archived or the link may be incorrect. Existing participants can find archived quests in My Quests.</p>
      <Link to="/quests">Return to Explore</Link>
    </Card>}
    {state.kind === 'error' && <Card className={styles.state} role="alert">
      <h1 id="quest-detail-title">We couldn’t load this quest</h1>
      <p>{state.message}</p>
      <Button variant="secondary" onClick={() => setAttempt(value => value + 1)}>Try again</Button>
    </Card>}
    {state.kind === 'loaded' && <QuestDetail quest={state.quest} participation={state.participation}
      onChanged={() => setAttempt(value => value + 1)} />}
  </section>
}

function QuestDetail({ quest, participation, onChanged }: {
  quest: Quest; participation: Participation | null; onChanged: () => void
}) {
  const { state: auth } = useAuth()
  const navigate = useNavigate()
  const location = useLocation()
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [pending, setPending] = useState(false)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [shareNotice, setShareNotice] = useState('')
  const isCreator = auth.status === 'authenticated' && auth.user.id === quest.creatorId

  async function act(action: 'join' | 'complete') {
    if (pending || (action === 'complete' && !participation)) return
    setPending(true); setError(''); setNotice('')
    try {
      const updated = action === 'join' ? await joinQuest(quest.id) : await completeParticipation(participation!.id)
      setConfirmOpen(false)
      setNotice(action === 'join' ? 'Quest joined! Find it under My Quests.' :
        updated.awardedXp > 0 ? `Quest completed. Awarded ${updated.awardedXp} XP.` : 'Quest already completed.')
      onChanged()
    } catch (e) {
      setConfirmOpen(false)
      if (e instanceof ApiError && (e.status === 0 || e.code === 'invalid_response')) {
        setError('The result is uncertain. We will check your saved progress. Do not submit again until you confirm the result.')
        onChanged() // read-only recovery; never automatically replay a mutation
      } else {
        setError(e instanceof Error ? e.message : 'Unable to update quest.')
      }
    } finally { setPending(false) }
  }

  function signIn() {
    navigate('/login', { state: { from: { pathname: location.pathname, search: location.search } } })
  }

  return <article className={styles.detail} aria-labelledby="quest-detail-title">
    <Card className={styles.summary}>
      <div className={styles.badges}>
        <Badge>{displayEnum(quest.category)}</Badge>
        <Badge tone="accent">{displayEnum(quest.difficulty)}</Badge>
      </div>
      <h1 id="quest-detail-title">{quest.title}</h1>
      <p className={styles.subtitle}>A challenge to make your next day a little more adventurous.</p>
      <Badge tone="reward"><Sparkles size={15} aria-hidden="true" /> {quest.rewardXp ?? '—'} XP reward</Badge>
      {participation?.status === 'COMPLETED' ? <p role="status">Completed · {participation.awardedXp} XP earned</p>
        : participation ? <div><p>In Progress</p><Button loading={pending} onClick={() => setConfirmOpen(true)}>Complete quest</Button></div>
        : isCreator ? <p>You created this quest; creators cannot join their own quests.</p>
        : auth.status === 'authenticated' ? <Button loading={pending} onClick={() => void act('join')}>Join quest</Button>
        : <Button onClick={signIn}>Sign in to join</Button>}
      {notice && <p role="status">{notice}</p>}
      {error && <p role="alert">{error}</p>}
    </Card>
    {quest.status === 'PUBLISHED' && <div>
      <Button variant="secondary" onClick={() => {
        const url = new URL(`/quests/${quest.id}`, window.location.origin).toString()
        void navigator.clipboard.writeText(url).then(() => setShareNotice('Quest link copied.'))
          .catch(() => setShareNotice(`Copy this link: ${url}`))
      }}>Share this quest</Button>
      {shareNotice && <p role="status">{shareNotice}</p>}
    </div>}
    <Card className={styles.description}>
      <p className={styles.eyebrow}>The quest</p>
      <h2>Description</h2>
      <p>{quest.description || 'The creator has not added a description yet.'}</p>
    </Card>
    {quest.status === 'PUBLISHED' && !isCreator && <ReportQuest questId={quest.id} />}
    <Modal open={confirmOpen} onOpenChange={setConfirmOpen} title="Complete this quest?"
      description="Completion is self-reported and permanent. You can earn the quest's original XP reward only once.">
      <div style={{ display: 'flex', gap: 'var(--space-3)', justifyContent: 'flex-end' }}>
        <Button variant="secondary" disabled={pending} onClick={() => setConfirmOpen(false)}>Cancel</Button>
        <Button loading={pending} onClick={() => void act('complete')}>Confirm completion</Button>
      </div>
    </Modal>
  </article>
}

export function ParticipationDetailPage() {
  const { id = '' } = useParams()
  const [state, setState] = useState<{ status: 'loading' | 'error' | 'ready'; item?: Participation; error?: string }>({ status: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const [pending, setPending] = useState(false)
  const [confirmOpen, setConfirmOpen] = useState(false)
  const [notice, setNotice] = useState('')
  const reload = useCallback(async (signal?: AbortSignal) => {
    setState({ status: 'loading' })
    try {
      const item = await getParticipation(id, signal)
      if (!signal?.aborted) setState({ status: 'ready', item })
    } catch (error) {
      if (!signal?.aborted) setState({ status: 'error', error: error instanceof Error ? error.message : 'Participation unavailable.' })
    }
  }, [id])
  useEffect(() => {
    const controller = new AbortController()
    void reload(controller.signal)
    return () => controller.abort()
  }, [reload, attempt])

  async function complete() {
    if (pending || state.status !== 'ready' || !state.item || state.item.status === 'COMPLETED') return
    setPending(true); setNotice('')
    try {
      const result = await completeParticipation(state.item.id)
      setConfirmOpen(false)
      setState({ status: 'ready', item: result })
      setNotice(`Quest completed. Awarded ${result.awardedXp} XP.`)
    } catch (error) {
      setConfirmOpen(false)
      const uncertain = error instanceof ApiError && (error.status === 0 || error.code === 'invalid_response')
      setNotice(uncertain ? 'Result uncertain; checking saved progress without retrying the completion.' :
        error instanceof Error ? error.message : 'Unable to complete quest.')
      if (uncertain) setAttempt(n => n + 1)
    } finally { setPending(false) }
  }

  return <section className={styles.page}>
    <Link className={styles.back} to="/my-quests?tab=in-progress">Back to My Quests</Link>
    {state.status === 'loading' && <Card role="status">Loading participation…</Card>}
    {state.status === 'error' && <Card role="alert">{state.error} <Button onClick={() => setAttempt(n => n + 1)}>Retry</Button></Card>}
    {state.status === 'ready' && state.item && <Card className={styles.summary}>
      <Badge tone="accent">{displayEnum(state.item.quest.status)}</Badge>
      <h1>{state.item.quest.title}</h1>
      <p>{state.item.quest.description}</p>
      <p>{state.item.status === 'COMPLETED' ? `Completed · ${state.item.awardedXp} XP awarded` : `In Progress · ${state.item.rewardXp} XP available`}</p>
      {state.item.status === 'IN_PROGRESS' && <Button loading={pending} onClick={() => setConfirmOpen(true)}>Complete quest</Button>}
      {notice && <p role="status">{notice}</p>}
    </Card>}
    <Modal open={confirmOpen} onOpenChange={setConfirmOpen} title="Complete this quest?"
      description="Completion is self-reported and permanent. XP is awarded only once.">
      <div style={{ display: 'flex', justifyContent: 'flex-end', gap: 'var(--space-3)' }}>
        <Button variant="secondary" disabled={pending} onClick={() => setConfirmOpen(false)}>Cancel</Button>
        <Button loading={pending} onClick={() => void complete()}>Confirm completion</Button>
      </div>
    </Modal>
  </section>
}
