import { useEffect, useState } from 'react'
import { Link, useNavigate, useParams } from 'react-router-dom'
import { Card } from '../../components/ui/Card/Card'
import { Button } from '../../components/ui/Button/Button'
import { ApiError } from '../../lib/api'
import { getPublishedQuests, type Quest } from '../quests/questApi'
import { createTeam, listTeams, invitations, decideInvite, getTeam, inviteMember,
  roster, leaveTeam, archiveTeam, removeMember, addChallenge, challenges, leaderboard, teamInvites, cancelInvite,
  type Team, type Invitation, type Member, type Challenge, type Score, type Page } from './groupsApi'

function message(e: unknown) {
  return e instanceof ApiError && (e.status === 0 || e.code === 'invalid_response')
    ? 'Result uncertain. Reload the saved team state before trying again.'
    : e instanceof Error ? e.message : 'Request failed. Please refresh.'
}
function Pages({ current, next, previous, onPage, label }: {
  current: number; next: boolean; previous: boolean; label: string; onPage: (value: number) => void
}) {
  return <nav className="sq-pagination" aria-label={label}>
    <Button variant="secondary" disabled={!previous} onClick={() => onPage(current - 1)}>Previous</Button>
    <span>Page {current + 1}</span>
    <Button variant="secondary" disabled={!next} onClick={() => onPage(current + 1)}>Next</Button>
  </nav>
}

export function TeamsPage() {
  const [name,setName] = useState('')
  const [teams,setTeams] = useState<Page<Team> | null>(null)
  const [pending,setPending] = useState<Page<Invitation> | null>(null)
  const [page,setPage] = useState(0)
  const [invitePage,setInvitePage] = useState(0)
  const [revision,setRevision] = useState(0)
  const [busy,setBusy] = useState(false)
  const [error,setError] = useState('')
  const [notice,setNotice] = useState('')
  const navigate = useNavigate()

  useEffect(() => {
    const controller=new AbortController()
    void Promise.all([listTeams(page,controller.signal),invitations(invitePage,controller.signal)])
      .then(([t,i]) => { if(!controller.signal.aborted) { setTeams(t);setPending(i);setError('') } })
      .catch(e => { if(!controller.signal.aborted) setError(message(e)) })
    return () => controller.abort()
  },[page,invitePage,revision])

  async function run(op: () => Promise<unknown>, noticeText: string) {
    if(busy)return
    setBusy(true);setError('');setNotice('')
    try { await op();setNotice(noticeText) } catch(e) {setError(message(e))}
    finally {setBusy(false);setRevision(n=>n+1)}
  }
  return <section style={{display:'grid',gap:16}} aria-labelledby="teams-title">
    <h1 id="teams-title">My teams</h1>
    <p>Private teams help friends work toward shared Quest challenges. Everyone joins voluntarily.</p>
    {error && <Card role="alert">{error}<Button onClick={()=>setRevision(n=>n+1)}>Refresh</Button></Card>}
    {notice && <p role="status">{notice}</p>}
    <Card style={{display:'grid',gap:12}}>
      <h2>Create a team</h2>
      <label htmlFor="team-name">Team name (3–80 characters)</label>
      <input id="team-name" value={name} maxLength={80} placeholder="e.g. Weekend Explorers" autoComplete="off" style={{width:'100%'}} onChange={e=>setName(e.target.value)}/>
      <Button disabled={busy||name.trim().length<3}
        onClick={()=>void run(async()=>{const team=await createTeam(name);navigate('/teams/'+team.id)},'Team created.')}>
        Create team
      </Button>
    </Card>
    <Card style={{display:'grid',gap:12}}>
      <h2>Team invitations</h2>
      {pending?.content.length===0 && <p>No pending team invitations.</p>}
      {pending?.content.map(i=><div key={i.id}>
        <p><strong>{i.teamName}</strong> · invited by {i.sender}</p>
        <Button disabled={busy} onClick={()=>void run(()=>decideInvite(i.id,'accept',i.version),'Joined team.')}>Accept</Button>{' '}
        <Button disabled={busy} variant="secondary"
          onClick={()=>void run(()=>decideInvite(i.id,'reject',i.version),'Invitation declined.')}>Reject</Button>
      </div>)}
      {pending && pending.totalPages>1 && <Pages current={pending.page} next={pending.hasNext}
        previous={pending.hasPrevious} label="Team invitation pages" onPage={setInvitePage}/>}
    </Card>
    <Card style={{display:'grid',gap:12}}>
      <h2>Your teams</h2>
      {!teams ? <p role="status">Loading teams…</p> : teams.content.length===0 ? <p>No teams yet.</p> :
        teams.content.map(t=><div key={t.id}>
          <Link to={'/teams/'+t.id}>{t.name}</Link> · {t.role.toLowerCase()} · {t.members} members · {t.state.toLowerCase()}
        </div>)}
      {teams && teams.totalPages>1 && <Pages current={teams.page} next={teams.hasNext}
        previous={teams.hasPrevious} label="Team pages" onPage={setPage}/>}
    </Card>
  </section>
}

export function TeamDetailPage() {
  const { id = '' } = useParams()
  const navigate = useNavigate()
  const [team,setTeam] = useState<Team | null>(null)
  const [people,setPeople] = useState<Page<Member> | null>(null)
  const [challengeList,setChallengeList] = useState<Page<Challenge> | null>(null)
  const [outgoing,setOutgoing] = useState<Page<Invitation> | null>(null)
  const [sentPage,setSentPage] = useState(0)
  const [ranked,setRanked] = useState<Page<Score> | null>(null)
  const [published,setPublished] = useState<Quest[]>([])
  const [username,setUsername] = useState('')
  const [questId,setQuestId] = useState('')
  const [days,setDays] = useState(7)
  const [target,setTarget] = useState(2)
  const [memberPage,setMemberPage] = useState(0)
  const [challengePage,setChallengePage] = useState(0)
  const [rankPage,setRankPage] = useState(0)
  const [revision,setRevision] = useState(0)
  const [busy,setBusy] = useState(false)
  const [loading,setLoading] = useState(true)
  const [error,setError] = useState('')
  const [notice,setNotice] = useState('')

  useEffect(() => {
    const controller=new AbortController()
    setLoading(true)
    void Promise.all([getTeam(id,controller.signal),roster(id,memberPage,controller.signal),
      challenges(id,challengePage,controller.signal),leaderboard(id,rankPage,controller.signal),
      getPublishedQuests('',controller.signal)])
      .then(([t,m,c,s,q])=>{
        if(controller.signal.aborted)return
        setTeam(t);setPeople(m);setChallengeList(c);setRanked(s);setPublished(q.content)
        setError('')
      }).catch(e=>{if(!controller.signal.aborted)setError(message(e))})
      .finally(()=>{if(!controller.signal.aborted)setLoading(false)})
    return ()=>controller.abort()
  },[id,memberPage,challengePage,rankPage,revision])

  useEffect(()=>{
    if(team?.role!=='OWNER'){setOutgoing(null);return}
    const controller=new AbortController()
    void teamInvites(id,sentPage,controller.signal)
      .then(value=>{if(!controller.signal.aborted)setOutgoing(value)})
      .catch(e=>{if(!controller.signal.aborted)setError(message(e))})
    return ()=>controller.abort()
  },[id,team?.role,sentPage,revision])

  async function run(op: ()=>Promise<unknown>, success: string) {
    if(busy)return
    setBusy(true);setError('');setNotice('')
    try { await op();setNotice(success) } catch(e){setError(message(e))}
    finally{setBusy(false);setRevision(n=>n+1)}
  }
  return <section style={{display:'grid',gap:16}} aria-labelledby="team-title">
    <Link to="/teams">Back to teams</Link>
    {loading && <Card role="status">Loading team information…</Card>}
    {error && <Card role="alert">{error} <Button onClick={()=>setRevision(n=>n+1)}>Retry</Button></Card>}
    {notice && <p role="status">{notice}</p>}
    {team && <>
      <h1 id="team-title">{team.name}</h1>
      <p>{team.members} members · {team.state.toLowerCase()} · Your role: {team.role.toLowerCase()}</p>
      <Card style={{display:'grid',gap:12}}>
        <h2>Team members</h2>
        {people?.content.map(p=><div key={p.id}>
          {p.username} · {p.role.toLowerCase()}{' '}
          {team.role==='OWNER' && p.role!=='OWNER' &&
            <Button variant="secondary" disabled={busy}
              onClick={()=>void run(()=>removeMember(team.id,p.id),'Member removed.')}>Remove</Button>}
        </div>)}
        {people?.content.length===0 && <p>No active members.</p>}
        {people && people.totalPages>1 && <Pages current={people.page} next={people.hasNext}
          previous={people.hasPrevious} label="Team member pages" onPage={setMemberPage}/>}
      </Card>
      {team.state==='ACTIVE' && team.role==='OWNER' && <Card style={{display:'grid',gap:12}}>
        <h2>Invite an accepted friend</h2>
        <label htmlFor="team-invite">Exact username</label>
        <input id="team-invite" value={username} maxLength={30} autoComplete="off" placeholder="Exact username" style={{width:'100%'}}
          onChange={e=>setUsername(e.target.value.toLowerCase())}/>
        <Button disabled={busy||!/^[a-z0-9_]{3,30}$/.test(username)}
          onClick={()=>void run(()=>inviteMember(team.id,username),'Team invitation sent.')}>Invite friend</Button>
      </Card>}
      {team.role==='OWNER' && <Card style={{display:'grid',gap:12}}>
        <h2>Pending team invitations</h2>
        {outgoing?.content.length===0 && <p>No pending invitations for this team.</p>}
        {outgoing?.content.map(i=><div key={i.id}>
          {i.recipient}{' '}
          <Button variant="secondary" disabled={busy}
            onClick={()=>void run(()=>cancelInvite(i.id,i.version),'Invitation cancelled.')}>Cancel invitation</Button>
        </div>)}
        {outgoing && outgoing.totalPages>1 && <Pages current={outgoing.page}
          next={outgoing.hasNext} previous={outgoing.hasPrevious}
          label="Outgoing team invitation pages" onPage={setSentPage}/>}
      </Card>}
      {team.state==='ACTIVE' && team.role==='OWNER' && <Card style={{display:'grid',gap:12}}>
        <h2>Launch a group Quest challenge</h2>
        <p>Only approved published Quests are eligible. Players join and complete individually; shared XP is not awarded twice.</p>
        <label htmlFor="team-quest">Published Quest</label>
        <select id="team-quest" style={{width:'100%'}} value={questId} onChange={e=>setQuestId(e.target.value)}>
          <option value="">Choose a Quest</option>
          {published.map(q=><option key={q.id} value={q.id}>{q.title}</option>)}
        </select>
        <label htmlFor="team-days">Challenge length (1–30 days)</label>
        <input id="team-days" style={{width:'100%'}} type="number" min={1} max={30} value={days}
          onChange={e=>setDays(Number(e.target.value))}/>
        <label htmlFor="team-target">Target completions (1–20)</label>
        <input id="team-target" style={{width:'100%'}} type="number" min={1} max={20} value={target}
          onChange={e=>setTarget(Number(e.target.value))}/>
        <Button disabled={busy||!questId||days<1||days>30||target<1||target>20}
          onClick={()=>void run(()=>addChallenge(team.id,questId,days,target),'Challenge launched.')}>
          Launch challenge
        </Button>
      </Card>}
      <Card style={{display:'grid',gap:12}}>
        <h2>Group challenges</h2>
        {challengeList?.content.length===0 && <p>No group challenges yet.</p>}
        {challengeList?.content.map(c=><article key={c.id}>
          <h3><Link to={'/quests/'+c.questId}>{c.title}</Link></h3>
          <p>{c.completed} of {c.targetCompletions} target completions · {c.earnedXp} eligible XP · {c.status.toLowerCase()}</p>
          <progress max={c.targetCompletions} value={Math.min(c.completed,c.targetCompletions)}
            aria-label={c.title+' team completion progress'}/>
          <p>Ends: {new Date(c.endsAt).toLocaleDateString()}</p>
        </article>)}
        {challengeList && challengeList.totalPages>1 && <Pages current={challengeList.page}
          next={challengeList.hasNext} previous={challengeList.hasPrevious}
          label="Team challenge pages" onPage={setChallengePage}/>}
      </Card>
      <Card style={{display:'grid',gap:12}}>
        <h2>Team leaderboard</h2>
        <p>Eligible Quest completions count once, using existing personal XP. Hidden or reported Quests are excluded.</p>
        {!ranked ? <p>Loading rankings…</p> : ranked.content.length===0 ? <p>No active members to rank.</p> :
          <ol>{ranked.content.map(s=><li key={s.username}>Rank {s.rank}: {s.username} · {s.earnedXp} XP · {s.completed} completions</li>)}</ol>}
        {ranked && ranked.totalPages>1 && <Pages current={ranked.page} next={ranked.hasNext}
          previous={ranked.hasPrevious} label="Team leaderboard pages" onPage={setRankPage}/>}
      </Card>
      {team.state==='ACTIVE' && <Card style={{display:'grid',gap:12}}>
        {team.role==='OWNER'
          ? <><h2>Archive team</h2><p>This permanently stops invitations and new challenges, retaining team history.</p>
            <Button variant="destructive" disabled={busy}
              onClick={()=>{if(window.confirm('Archive this team?'))void run(()=>archiveTeam(team.id),'Team archived.')}}>
              Archive team
            </Button></>
          : <><h2>Leave team</h2><p>Leaving revokes access. You'll need another invitation to return.</p>
            <Button variant="destructive" disabled={busy}
              onClick={()=>{if(window.confirm('Leave this team?'))void run(async()=>{await leaveTeam(team.id);navigate('/teams')},'Left team.')}}>
              Leave team
            </Button></>}
      </Card>}
    </>}
  </section>
}
