import { useEffect, useState } from 'react'
import { Button } from '../../components/ui/Button/Button'
import { Card } from '../../components/ui/Card/Card'
import { getLeaderboard, getRanking, type Leaderboard, type Ranking, type Period } from './gamificationApi'
import styles from './Gamification.module.css'

type State = { kind: 'loading' } | { kind: 'error'; message: string }
  | { kind: 'ready'; board: Leaderboard; ranking: Ranking }
const periods: { value: Period; label: string }[] = [
  { value: 'all', label: 'All-time' }, { value: 'weekly', label: 'Weekly' }, { value: 'monthly', label: 'Monthly' },
]
export function LeaderboardPage() {
  const [period, setPeriod] = useState<Period>('all')
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
  const [state, setState] = useState<State>({ kind: 'loading' })
  useEffect(() => {
    const controller = new AbortController()
    setState({ kind: 'loading' })
    void Promise.all([getLeaderboard(period, page, controller.signal), getRanking(period, controller.signal)])
      .then(([board, ranking]) => { if (!controller.signal.aborted) setState({ kind: 'ready', board, ranking }) })
      .catch(error => { if (!controller.signal.aborted) setState({ kind: 'error', message: error instanceof Error ? error.message : 'Unable to load leaderboard.' }) })
    return () => controller.abort()
  }, [period, page, attempt])
  return <section aria-labelledby="leaderboard-title" className={styles.page}>
    <h1 id="leaderboard-title">Leaderboard</h1>
    <p>Celebrate steady progress at your own pace. Weeks begin Monday and months begin on the first day, in UTC.</p>
    <div role="group" aria-label="Scoring period" className={styles.filters}>
      {periods.map(p => <Button key={p.value} variant="secondary" aria-pressed={period === p.value}
        onClick={() => { if (period === p.value) return; setState({ kind: 'loading' }); setPeriod(p.value); setPage(0) }}>{p.label}</Button>)}
    </div>
    <p>Equal XP shares a rank (1, 1, 3); tied players appear alphabetically. Only XP from approved Quests without open reports counts here, so ranking XP can differ from your lifetime XP.</p>
    {state.kind === 'loading' && <Card role="status">Loading leaderboard…</Card>}
    {state.kind === 'error' && <Card role="alert"><p>{state.message}</p>
      <Button onClick={() => setAttempt(n => n + 1)}>Retry leaderboard</Button></Card>}
    {state.kind === 'ready' && <>
      <Card><h2>Your position</h2>{state.ranking.player
        ? <p>Rank {state.ranking.player.rank} · {state.ranking.player.earnedXp} eligible XP</p>
        : <p>Unranked this period. Complete an eligible Quest to appear.</p>}</Card>
      {state.board.content.length === 0 ? <Card>No ranked players on this page yet.</Card> :
        <table className={styles.table}>
          <caption>{periods.find(p => p.value === period)?.label} player rankings</caption>
          <thead><tr><th scope="col">Rank</th><th scope="col">Player</th><th scope="col">Earned XP</th></tr></thead>
          <tbody>{state.board.content.map(row => <tr key={row.username}>
            <td>{row.rank}</td><th scope="row">{row.username}{row.username === state.ranking.player?.username ? ' (you)' : ''}</th><td>{row.earnedXp}</td>
          </tr>)}</tbody>
        </table>}
      {(state.board.totalPages > 1 || page > 0) && <nav aria-label="Leaderboard pages" className={styles.pagination}>
        <Button variant="secondary" disabled={!state.board.hasPrevious} onClick={() => setPage(n => n - 1)}>Previous</Button>
        <span>Page {page + 1} of {Math.max(1, state.board.totalPages)}</span>
        <Button variant="secondary" disabled={!state.board.hasNext} onClick={() => setPage(n => n + 1)}>Next</Button>
      </nav>}
    </>}
  </section>
}
