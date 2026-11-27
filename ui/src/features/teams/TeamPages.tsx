import { useEffect, useState } from 'react'
import { Link, useParams } from 'react-router-dom'
import { Card } from '../../components/ui/Card/Card'
import { Button } from '../../components/ui/Button/Button'
import { createTeam, myTeams, myInvites, respondInvite, teamInfo, teamMembers,
 inviteToTeam, createChallenge, challenges, scores, removeMember,
 type Team, type Invite, type Challenge, type Score, type Page } from './teamApi'

function errorText(error:unknown){return error instanceof Error ? error.message : 'Request failed. Refresh and try again.'}
function Pager({ data,onPage }: {data:Page<unknown>|null,onPage:(page:number)=>void}){
 if(!data||data.totalPages<2)return null
 return <nav aria-label="Results pages" style={{display:'flex',gap:'var(--space-2)',alignItems:'center'}}>
   <Button variant="secondary" disabled={!data.hasPrevious} onClick={()=>onPage(data.page-1)}>Previous</Button>
   <span>Page {data.page+1} of {data.totalPages}</span>
   <Button variant="secondary" disabled={!data.hasNext} onClick={()=>onPage(data.page+1)}>Next</Button>
 </nav>
}
export function TeamsPage(){
 const [teams,setTeams]=useState<Page<Team>|null>(null)
 const [invitations,setInvitations]=useState<Page<Invite>|null>(null)
 const [teamPage,setTeamPage]=useState(0),[invitePage,setInvitePage]=useState(0)
 const [name,setName]=useState(''),[revision,setRevision]=useState(0)
 const [pending,setPending]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('')
 useEffect(()=>{
  const controller=new AbortController()
  void Promise.all([myTeams(teamPage,controller.signal),myInvites(invitePage,controller.signal)])
    .then(([mine,invites])=>{if(!controller.signal.aborted){setTeams(mine);setInvitations(invites);setError('')}})
    .catch(e=>{if(!controller.signal.aborted)setError(errorText(e))})
  return ()=>controller.abort()
 },[teamPage,invitePage,revision])
 async function act(op:()=>Promise<unknown>,msg:string){
  if(pending)return
  setPending(true);setError('');setNotice('')
  try{await op();setNotice(msg)}catch(e){setError(errorText(e))}
  finally{setPending(false);setRevision(n=>n+1)}
 }
 return <section style={{display:'grid',gap:'var(--space-4)'}}>
  <h1>Teams and group challenges</h1>
  <p>Teams are invitation-only. Every member chooses whether to join.</p>
  {error && <Card role="alert">{error}<Button variant="secondary" onClick={()=>setRevision(n=>n+1)}>Refresh</Button></Card>}
  {notice && <p role="status">{notice}</p>}
  <Card style={{display:'grid',gap:'var(--space-2)'}}>
   <h2>Create a team</h2>
   <label htmlFor="team-name">Team name</label>
   <input id="team-name" maxLength={80} value={name} onChange={e=>setName(e.target.value)}/>
   <Button disabled={pending||name.trim().length<3} onClick={()=>void act(async()=>{await createTeam(name);setName('')},'Team created.')}>Create team</Button>
  </Card>
  <Card style={{display:'grid',gap:'var(--space-2)'}}>
   <h2>Incoming team invitations</h2>
   {!invitations && <p role="status">Loading invitations…</p>}
   {invitations?.content.length===0 && <p>No pending team invitations.</p>}
   {invitations?.content.map(inv=><div key={inv.id} style={{display:'flex',alignItems:'center',gap:'var(--space-2)',flexWrap:'wrap'}}>
    <span>{inv.teamName}</span>
    <Button disabled={pending} onClick={()=>void act(()=>respondInvite(inv.id,'accept',inv.version),'Team invitation accepted.')}>Accept</Button>
    <Button variant="secondary" disabled={pending} onClick={()=>void act(()=>respondInvite(inv.id,'decline',inv.version),'Invitation declined.')}>Decline</Button>
   </div>)}
   <Pager data={invitations} onPage={setInvitePage}/>
  </Card>
  <Card style={{display:'grid',gap:'var(--space-2)'}}>
   <h2>My teams</h2>
   {!teams && <p role="status">Loading teams…</p>}
   {teams?.content.length===0 && <p>You're not in a team yet.</p>}
   {teams?.content.map(team=><p key={team.id}>
    <Link to={`/teams/${team.id}`}>{team.name}</Link> · {team.members} members {team.owner?'· Owner':''}
   </p>)}
   <Pager data={teams} onPage={setTeamPage}/>
  </Card>
 </section>
}
export function TeamDetailsPage(){
 const { id='' }=useParams()
 const [team,setTeam]=useState<Team|null>(null)
 const [members,setMembers]=useState<Page<string>|null>(null)
 const [challengeData,setChallengeData]=useState<Page<Challenge>|null>(null)
 const [rankData,setRankData]=useState<Page<Score>|null>(null)
 const [memberPage,setMemberPage]=useState(0),[challengePage,setChallengePage]=useState(0),[rankPage,setRankPage]=useState(0)
 const [username,setUsername]=useState(''),[questId,setQuestId]=useState('')
 const [start,setStart]=useState(''),[end,setEnd]=useState('')
 const [pending,setPending]=useState(false),[error,setError]=useState(''),[notice,setNotice]=useState('')
 const [revision,setRevision]=useState(0)
 useEffect(()=>{
  const ctrl=new AbortController()
  setError('')
  void Promise.all([teamInfo(id,ctrl.signal),teamMembers(id,memberPage,ctrl.signal),
   challenges(id,challengePage,ctrl.signal),scores(id,rankPage,ctrl.signal)])
   .then(([t,m,c,s])=>{if(!ctrl.signal.aborted){setTeam(t);setMembers(m);setChallengeData(c);setRankData(s)}})
   .catch(e=>{if(!ctrl.signal.aborted)setError(errorText(e))})
  return ()=>ctrl.abort()
 },[id,revision,memberPage,challengePage,rankPage])
 async function act(op:()=>Promise<unknown>,message:string){
  if(pending)return
  setPending(true);setError('');setNotice('')
  try{await op();setNotice(message)}catch(e){setError(errorText(e))}
  finally{setPending(false);setRevision(n=>n+1)}
 }
 const validUsername=/^[a-z0-9_]{3,30}$/.test(username)
 const validQuest=/^[0-9a-f-]{36}$/i.test(questId)
 return <section style={{display:'grid',gap:'var(--space-4)'}}>
  <Link to="/teams">Back to teams</Link>
  <h1>{team?.name||'Team details'}</h1>
  {error && <Card role="alert">{error}<Button variant="secondary" onClick={()=>setRevision(n=>n+1)}>Retry</Button></Card>}
  {notice && <p role="status">{notice}</p>}
  {team && <p>{team.members} members · {team.owner?'You own this team':'Team member'}</p>}
  <Card style={{display:'grid',gap:'var(--space-2)'}}>
    <h2>Team members</h2>
    {members?.content.map(username=><p key={username}>{username}</p>)}
    {members?.content.length===0 && <p>No members found.</p>}
    <Pager data={members} onPage={setMemberPage}/>
  </Card>
  {team?.owner && <Card style={{display:'grid',gap:'var(--space-2)'}}>
    <h2>Invite a player</h2>
    <p>Enter an exact username. Invites require acceptance.</p>
    <label htmlFor="team-invite-name">Username</label>
    <input id="team-invite-name" value={username} maxLength={30} autoComplete="off"
      onChange={e=>setUsername(e.target.value.toLowerCase())}/>
    <Button disabled={pending||!validUsername} onClick={()=>void act(()=>inviteToTeam(id,username),'Invitation sent.')}>Send invitation</Button>
    <Button variant="secondary" disabled={pending||!validUsername} onClick={()=>void act(()=>removeMember(id,username),'Member removed if present.')}>Remove member</Button>
  </Card>}
  {team?.owner && <Card style={{display:'grid',gap:'var(--space-2)'}}>
   <h2>Create group challenge</h2>
   <p>Only a published, approved Quest can become a challenge. This doesn't award extra XP.</p>
   <label htmlFor="team-quest-id">Published Quest ID</label>
   <input id="team-quest-id" value={questId} onChange={e=>setQuestId(e.target.value.trim())}/>
   <label htmlFor="team-challenge-start">Starts at (your local time)</label>
   <input id="team-challenge-start" type="datetime-local" value={start} onChange={e=>setStart(e.target.value)}/>
   <label htmlFor="team-challenge-end">Ends at (your local time)</label>
   <input id="team-challenge-end" type="datetime-local" value={end} onChange={e=>setEnd(e.target.value)}/>
   <Button disabled={pending||!validQuest||!start||!end||Date.parse(end)<=Date.parse(start)}
     onClick={()=>void act(()=>createChallenge(id,questId,new Date(start).toISOString(),new Date(end).toISOString()),'Challenge added.')}>
     Create challenge
   </Button>
  </Card>}
  <Card style={{display:'grid',gap:'var(--space-2)'}}>
   <h2>Group challenges</h2>
   {challengeData?.content.length===0 && <p>No active or scheduled challenges yet.</p>}
   {challengeData?.content.map(ch=><article key={ch.id}>
    <h3>{ch.title}</h3>
    <p>{ch.completedMembers} completed · {ch.earnedXp} eligible XP</p>
    <p>From {new Date(ch.startsAt).toLocaleString()} to {new Date(ch.endsAt).toLocaleString()}</p>
    <Link to={`/quests/${ch.questId}`}>Open Quest</Link>
   </article>)}
   <Pager data={challengeData} onPage={setChallengePage}/>
  </Card>
  <Card style={{display:'grid',gap:'var(--space-2)'}}>
   <h2>Team leaderboard</h2>
   <p>Based on eligible, once-awarded XP from group challenge Quest completions.</p>
   {rankData?.content.length===0 && <p>No team scores yet.</p>}
   <ol>{rankData?.content.map(row=><li key={row.username}>
    Rank {row.rank}: {row.username} · {row.earnedXp} XP
   </li>)}</ol>
   <Pager data={rankData} onPage={setRankPage}/>
  </Card>
 </section>
}
