import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Card } from '../../components/ui/Card/Card'
import { Button } from '../../components/ui/Button/Button'
import { ApiError } from '../../lib/api'
import { block, decide, getFriendsBoard, getPublicProfile, getSettings, invite, listFriends, listRequests,
  setVisibility, type Connection, type FriendBoard, type Period, type Settings, type Visibility,
  type PublicProfile, type Page } from './socialApi'

export function FriendsPage() {
  const [settings, setSettings] = useState<Settings | null>(null)
  const [incoming, setIncoming] = useState<Page<Connection> | null>(null)
  const [incomingPage,setIncomingPage] = useState(0)
  const [outgoing, setOutgoing] = useState<Page<Connection> | null>(null)
  const [outgoingPage,setOutgoingPage] = useState(0)
  const [friends, setFriends] = useState<Page<Connection> | null>(null)
  const [friendsPage,setFriendsPage] = useState(0)
  const [username, setUsername] = useState('')
  const [busy, setBusy] = useState(false)
  const [revision, setRevision] = useState(0)
  const [error, setError] = useState('')
  const [notice, setNotice] = useState('')
  const [loading, setLoading] = useState(true)

  useEffect(() => {
    const controller = new AbortController()
    setLoading(true)
    void Promise.all([getSettings(controller.signal), listRequests('incoming', incomingPage, controller.signal),
      listRequests('outgoing', outgoingPage, controller.signal), listFriends(friendsPage, controller.signal)])
      .then(([s, i, o, f]) => {
        if (controller.signal.aborted) return
        setSettings(s); setIncoming(i); setOutgoing(o); setFriends(f)
        setError('')
      }).catch(e => { if (!controller.signal.aborted) setError(e instanceof Error ? e.message : 'Could not load friends.') })
      .finally(() => { if (!controller.signal.aborted) setLoading(false) })
    return () => controller.abort()
  }, [revision,incomingPage,outgoingPage,friendsPage])

  async function action(run: () => Promise<unknown>, message: string) {
    if (busy) return
    setBusy(true); setError('');setNotice('')
    try {
      await run()
      setNotice(message)
    } catch(e) {
      setError(e instanceof ApiError && (e.status === 0 || e.code === 'invalid_response')
        ? 'Result uncertain. Refresh to check the saved state before trying again.'
        : e instanceof Error ? e.message : 'Could not save the change.')
    } finally {
      setBusy(false)
      setRevision(n => n + 1)
    }
  }

  return <section style={{ display:'grid', gap:'var(--space-4)' }} aria-labelledby="friends-title">
    <h1 id="friends-title">Friends and social profile</h1>
    <p>Social profiles are private by default. Only accepted friends can view Friends-only profiles.</p>
    {loading && <Card role="status">Loading social settings and friends…</Card>}
    {error && <Card role="alert">{error} <Button variant="secondary" onClick={() => setRevision(n => n + 1)}>Refresh</Button></Card>}
    {notice && <p role="status">{notice}</p>}
    {settings && <Card style={{ display:'grid',gap:'var(--space-3)' }}>
      <h2>Your social privacy</h2>
      <label htmlFor="visibility">Who can see your social profile?</label>
      <select id="visibility" value={settings.visibility} disabled={busy}
        onChange={e => void action(() => setVisibility(e.target.value as Visibility,settings.version), 'Privacy updated.')}>
        <option value="PRIVATE">Private (only me)</option>
        <option value="FRIENDS">Friends only</option>
        <option value="PUBLIC">Public to signed-in users</option>
      </select>
      <Link to={`/players/${settings.profileId}`}>View your social profile</Link>
      <small>Changing this does not hide your username from the existing authenticated leaderboard.</small>
    </Card>}
    <Card style={{ display:'grid',gap:'var(--space-3)' }}>
      <h2>Invite a friend</h2>
      <p>Use their exact username. There is no public people directory.</p>
      <label htmlFor="friend-name">Username</label>
      <input id="friend-name" value={username} maxLength={30} autoComplete="off" onChange={e => setUsername(e.target.value.toLowerCase())}/>
      <Button disabled={busy || !/^[a-z0-9_]{3,30}$/.test(username)}
        onClick={() => void action(() => invite(username), 'Request sent.')}>Send request</Button>
      <Button variant="secondary" disabled={busy || !/^[a-z0-9_]{3,30}$/.test(username)}
        onClick={() => void action(() => block(username), 'Account blocked. Existing friendship removed.')}>Block user</Button>
    </Card>
    <Card style={{ display:'grid',gap:'var(--space-3)' }}>
      <h2>Incoming requests</h2>
      {incoming && !incoming.content.length && <p>No incoming requests.</p>}
      {incoming?.content.map(c => <div key={c.id}>
        <strong>{c.username}</strong>{' '}
        <Button disabled={busy} onClick={() => void action(() => decide(c.id,'accept',c.version),'Friend request accepted.')}>Accept</Button>{' '}
        <Button variant="secondary" disabled={busy} onClick={() => void action(() => decide(c.id,'reject',c.version),'Request declined.')}>Reject</Button>
      </div>)}
      {incoming && incoming.totalPages > 1 && <nav aria-label="Incoming request pages">
        <Button variant="secondary" disabled={!incoming.hasPrevious || busy} onClick={() => setIncomingPage(n => n - 1)}>Previous</Button>
        <span> Page {incoming.page + 1} of {incoming.totalPages} </span>
        <Button variant="secondary" disabled={!incoming.hasNext || busy} onClick={() => setIncomingPage(n => n + 1)}>Next</Button>
      </nav>}
    </Card>
    <Card style={{ display:'grid',gap:'var(--space-3)' }}>
      <h2>Outgoing requests</h2>
      {outgoing && !outgoing.content.length && <p>No pending invitations.</p>}
      {outgoing?.content.map(c => <div key={c.id}><span>{c.username}</span>{' '}
        <Button variant="secondary" disabled={busy} onClick={() => void action(() => decide(c.id,'cancel',c.version),'Request cancelled.')}>Cancel</Button></div>)}
    </Card>
    <Card style={{ display:'grid',gap:'var(--space-3)' }}>
      <h2>Your friends</h2>
      {friends && !friends.content.length && <p>No friends yet. Invite someone using their exact username.</p>}
      {friends?.content.map(c => <div key={c.id}>
        <Link to={`/players/${c.profileId}`}>{c.username}</Link>{' '}
        <Button variant="secondary" disabled={busy} onClick={() => void action(() => decide(c.id,'remove',c.version),'Friend removed.')}>Remove</Button>
      </div>)}
      {friends && friends.totalPages > 1 && <nav aria-label="Friend list pages">
        <Button variant="secondary" disabled={!friends.hasPrevious || busy} onClick={() => setFriendsPage(n => n - 1)}>Previous</Button>
        <span> Page {friends.page + 1} of {friends.totalPages} </span>
        <Button variant="secondary" disabled={!friends.hasNext || busy} onClick={() => setFriendsPage(n => n + 1)}>Next</Button>
      </nav>}
    </Card>
    <Link to="/friends/leaderboard">View friends leaderboard</Link>
  </section>
}

export function FriendLeaderboardPage() {
  const [period,setPeriod] = useState<Period>('all')
  const [page,setPage] = useState(0)
  const [board,setBoard] = useState<FriendBoard | null>(null)
  const [error,setError] = useState('')
  const [retry,setRetry] = useState(0)
  useEffect(() => {
    const controller = new AbortController()
    setBoard(null);setError('')
    void getFriendsBoard(period,page,controller.signal)
      .then(b => { if(!controller.signal.aborted) setBoard(b) })
      .catch(e => { if(!controller.signal.aborted) setError(e instanceof Error ? e.message : 'Could not load leaderboard.') })
    return () => controller.abort()
  },[period,page,retry])
  return <section style={{display:'grid',gap:'var(--space-4)'}}>
    <h1>Friends leaderboard</h1>
    <p>Ranks include your eligible XP and accepted friends only. Archived but approved Quests can count.</p>
    <label htmlFor="friends-period">Ranking period</label>
    <select id="friends-period" value={period} onChange={e => {setPage(0);setPeriod(e.target.value as Period)}}>
      <option value="all">All time</option><option value="weekly">This week (UTC)</option>
      <option value="monthly">This month (UTC)</option>
    </select>
    {error && <Card role="alert">{error} <Button onClick={() => setRetry(n => n + 1)}>Retry</Button></Card>}
    {!board && !error && <Card role="status">Loading friends leaderboard…</Card>}
    {board && <Card>
      {board.content.length === 0 ? <p>No eligible XP yet.</p> : <ol>
        {board.content.map(x => <li key={x.username}>Rank {x.rank}: {x.username} · {x.earnedXp} XP</li>)}
      </ol>}
      {board.totalPages>1 && <nav aria-label="Friends leaderboard pages">
        <Button variant="secondary" disabled={!board.hasPrevious} onClick={() => setPage(n => n - 1)}>Previous</Button>
        <span> Page {board.page+1} of {board.totalPages} </span>
        <Button variant="secondary" disabled={!board.hasNext} onClick={() => setPage(n => n + 1)}>Next</Button>
      </nav>}
    </Card>}
    <Link to="/friends">Back to friends</Link>
  </section>
}

export function PublicSocialProfilePage() {
  const { id = '' } = useParams()
  const [profile,setProfile] = useState<PublicProfile | null>(null)
  const [error,setError] = useState('')
  useEffect(() => {
    const controller = new AbortController()
    setProfile(null);setError('')
    void getPublicProfile(id,controller.signal).then(p => { if(!controller.signal.aborted) setProfile(p) })
      .catch(e => { if(!controller.signal.aborted) setError(e instanceof ApiError && e.status === 404
        ? 'This profile is private or unavailable.' : e instanceof Error ? e.message : 'Unable to load profile.') })
    return () => controller.abort()
  },[id])
  return <section>
    <Link to="/friends">Back to friends</Link>
    {error ? <Card role="alert">{error}</Card> : profile
      ? <Card><h1>{profile.displayName}</h1><p>SideQuest player</p></Card>
      : <Card role="status">Loading player profile…</Card>}
  </section>
}
