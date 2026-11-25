import { useEffect, useState } from 'react'
import { Link } from 'react-router-dom'
import { Button } from '../../components/ui/Button/Button'
import { Card } from '../../components/ui/Card/Card'
import { getAchievements, getPlayerProfile, getXpHistory, type Achievement, type Page, type PlayerProfile, type XpTransaction } from './progressApi'

type State = { kind: 'loading' } | { kind: 'error'; message: string } | {
  kind: 'ready'; profile: PlayerProfile; achievements: Achievement[]; history: Page<XpTransaction>
}
export function PlayerProfilePage() {
  const [state, setState] = useState<State>({ kind: 'loading' })
  const [page, setPage] = useState(0)
  const [attempt, setAttempt] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setState({ kind: 'loading' })
    void Promise.all([
      getPlayerProfile(controller.signal), getAchievements(controller.signal), getXpHistory(page, controller.signal),
    ]).then(([profile, catalogue, history]) => {
      if (!controller.signal.aborted) setState({ kind: 'ready', profile, achievements: catalogue.achievements, history })
    }).catch(error => {
      if (!controller.signal.aborted) setState({ kind: 'error', message: error instanceof Error ? error.message : 'Unable to load profile.' })
    })
    return () => controller.abort()
  }, [page, attempt])
  return <section aria-labelledby="profile-title" style={{ display: 'grid', gap: 'var(--space-6)' }}>
    <h1 id="profile-title">Your profile</h1>
    {state.kind === 'loading' && <Card role="status">Loading your progress…</Card>}
    {state.kind === 'error' && <Card role="alert"><p>{state.message}</p>
      <Button variant="secondary" onClick={() => setAttempt(n => n + 1)}>Try again</Button></Card>}
    {state.kind === 'ready' && <>
      <Card style={{ display: 'grid', gap: 'var(--space-3)' }}>
        <h2>{state.profile.username}</h2>
        <p>Level {state.profile.level} · {state.profile.totalXp} XP total</p>
        <label htmlFor="level-progress">{state.profile.xpIntoLevel} XP into this level · {state.profile.xpRemaining} to next level</label>
        <progress id="level-progress" max={100} value={state.profile.progressPercent} style={{ width: '100%' }} />
        <p>{state.profile.activeQuests} in progress · {state.profile.completedQuests} completed · {state.profile.createdQuests} created</p>
        <p>{state.profile.unlockedAchievementCount} achievements unlocked</p>
      </Card>
      <h2>Achievements</h2>
      {state.achievements.length === 0 && <Card>No achievements available yet.</Card>}
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(min(100%, 16rem), 1fr))', gap: 'var(--space-4)' }}>
        {state.achievements.map(a => <Card key={a.id}>
          <h3>{a.name} {a.unlocked ? '✓' : '🔒'}</h3>
          <p>{a.description}</p>
          <p>{a.progress} / {a.target}</p>
          {a.unlockedAt && <small>Unlocked {new Date(a.unlockedAt).toLocaleDateString()}</small>}
        </Card>)}
      </div>
      <h2>XP history</h2>
      {state.history.content.length === 0 && <Card>No XP earned yet. <Link to="/quests">Explore quests</Link></Card>}
      <div style={{ display: 'grid', gap: 'var(--space-3)' }}>
        {state.history.content.map(tx => <Card key={tx.id}>
          <strong>{tx.questTitle}</strong> · +{tx.amount} XP
          <p><small>{new Date(tx.awardedAt).toLocaleDateString()}</small></p>
        </Card>)}
      </div>
      {state.history.totalPages > 1 && <nav aria-label="XP history pages" style={{ display: 'flex', alignItems: 'center', gap: 'var(--space-3)' }}>
        <Button variant="secondary" disabled={!state.history.hasPrevious} onClick={() => setPage(n => n - 1)}>Previous</Button>
        <span>Page {state.history.page + 1} of {state.history.totalPages}</span>
        <Button variant="secondary" disabled={!state.history.hasNext} onClick={() => setPage(n => n + 1)}>Next</Button>
      </nav>}
    </>}
  </section>
}
