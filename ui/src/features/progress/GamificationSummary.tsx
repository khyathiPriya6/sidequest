import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button/Button'
import { Card } from '../../components/ui/Card/Card'
import { getRanking, getStreak, type Ranking, type Streak } from './gamificationApi'
import styles from './Gamification.module.css'

type State = { kind: 'loading' } | { kind: 'error' } | { kind: 'ready'; streak: Streak; ranking: Ranking }
export function GamificationSummary() {
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setState({ kind: 'loading' })
    void Promise.all([getStreak(controller.signal), getRanking('all', controller.signal)])
      .then(([streak, ranking]) => { if (!controller.signal.aborted) setState({ kind: 'ready', streak, ranking }) })
      .catch(() => { if (!controller.signal.aborted) setState({ kind: 'error' }) })
    return () => controller.abort()
  }, [attempt])
  return <Card>
    <h2>Daily activity and ranking</h2>
    {state.kind === 'loading' && <p role="status">Loading streak and rank…</p>}
    {state.kind === 'error' && <div role="alert"><p>Unable to load streak and rank.</p>
      <Button onClick={() => setAttempt(n => n + 1)}>Retry streak and rank</Button></div>}
    {state.kind === 'ready' && <>
      <dl className={styles.stats}>
        <div><dt>Current streak</dt><dd>{state.streak.currentStreak} days</dd></div>
        <div><dt>Longest streak</dt><dd>{state.streak.longestStreak} days</dd></div>
        <div><dt>All-time rank</dt><dd>{state.ranking.player ? `Rank ${state.ranking.player.rank}` : 'Unranked'}</dd></div>
      </dl>
      <p>Last completion: {state.streak.lastCompletionDate ?? 'No activity yet'} (UTC)</p>
      <p>One or more completions count as one active day. Yesterday’s streak stays current until today ends. Take breaks whenever you need.</p>
    </>}
    <Link to="/leaderboard">View leaderboard</Link>
  </Card>
}
