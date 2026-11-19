import { useCallback, useEffect, useState } from 'react'
import { ArrowLeft, Sparkles } from 'lucide-react'
import { Link, useLocation, useParams } from 'react-router-dom'
import { Badge } from '../components/ui/Badge/Badge'
import { Button } from '../components/ui/Button/Button'
import { Card } from '../components/ui/Card/Card'
import { QuestApiError, displayEnum, getQuest, type Quest } from '../features/quests/questApi'
import styles from './QuestDetailPage.module.css'

type LoadState =
  | { kind: 'loading' }
  | { kind: 'loaded'; quest: Quest }
  | { kind: 'unavailable' }
  | { kind: 'error'; message: string }

export function QuestDetailPage() {
  const { id = '' } = useParams()
  const location = useLocation()
  const [state, setState] = useState<LoadState>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  const returnTo = typeof location.state === 'object' && location.state !== null
    && 'from' in location.state && typeof location.state.from === 'string'
    ? location.state.from
    : `/quests${location.search}`

  const load = useCallback(async (signal: AbortSignal) => {
    setState({ kind: 'loading' })
    try {
      const quest = await getQuest(id, signal)
      if (!signal.aborted) setState({ kind: 'loaded', quest })
    } catch (error) {
      if (signal.aborted) return
      if (error instanceof QuestApiError && error.status === 404) {
        setState({ kind: 'unavailable' })
      } else {
        setState({
          kind: 'error',
          message: error instanceof Error ? error.message : 'Unable to load this quest.',
        })
      }
    }
  }, [id])

  useEffect(() => {
    const controller = new AbortController()
    void load(controller.signal)
    return () => controller.abort()
  }, [load, attempt])

  return <section className={styles.page} aria-labelledby="quest-detail-title">
    <Link className={styles.back} to={returnTo}><ArrowLeft size={18} aria-hidden="true" /> Back to Explore</Link>
    {state.kind === 'loading' && <Card className={styles.state} role="status">Loading quest details…</Card>}
    {state.kind === 'unavailable' && <Card className={styles.state} role="status">
      <h1 id="quest-detail-title">Quest unavailable</h1>
      <p>This quest may have been removed from public discovery or the link may be incorrect.</p>
      <Link to="/quests">Return to Explore</Link>
    </Card>}
    {state.kind === 'error' && <Card className={styles.state} role="alert">
      <h1 id="quest-detail-title">We couldn’t load this quest</h1>
      <p>{state.message}</p>
      <Button variant="secondary" onClick={() => setAttempt(value => value + 1)}>Try again</Button>
    </Card>}
    {state.kind === 'loaded' && <QuestDetail quest={state.quest} />}
  </section>
}

function QuestDetail({ quest }: { quest: Quest }) {
  return <article className={styles.detail} aria-labelledby="quest-detail-title">
    <Card className={styles.summary}>
      <div className={styles.badges}>
        <Badge>{displayEnum(quest.category)}</Badge>
        <Badge tone="accent">{displayEnum(quest.difficulty)}</Badge>
      </div>
      <h1 id="quest-detail-title">{quest.title}</h1>
      <p className={styles.subtitle}>A challenge to make your next day a little more adventurous.</p>
      <Badge tone="reward"><Sparkles size={15} aria-hidden="true" /> {quest.rewardXp ?? '—'} XP reward</Badge>
    </Card>

    <Card className={styles.description}>
      <p className={styles.eyebrow}>The quest</p>
      <h2>Description</h2>
      <p>{quest.description || 'The creator has not added a description yet.'}</p>
    </Card>

    <p className={styles.note}>Joining and completion are not part of this release.</p>
  </article>
}
