import { beforeEach, expect, it, vi } from 'vitest'
import { createTeam, inviteMember, listTeams, addChallenge, decideInvite } from './groupsApi'

const id='123e4567-e89b-42d3-a456-426614174000'
const other='123e4567-e89b-42d3-a456-426614174001'
const row={id,name:'Trail Pack',state:'ACTIVE',role:'OWNER',version:0,members:1}
const envelope={content:[row],page:0,size:20,totalElements:1,totalPages:1,hasNext:false,hasPrevious:false}
const invite={id,teamId:other,teamName:'Trail Pack',sender:'tester',recipient:'friend',
  status:'PENDING',version:0}
const challenge={id,questId:other,title:'Morning walk',targetCompletions:2,completed:0,
  earnedXp:0,createdAt:'2026-11-26T00:00:00Z',endsAt:'2026-12-03T00:00:00Z',status:'ACTIVE'}

beforeEach(() => {
  vi.stubGlobal('fetch', vi.fn(async (input: RequestInfo | URL) => {
    const path=String(input)
    if(path.endsWith('/auth/csrf'))return Response.json({csrfToken:'team-csrf'})
    if(path.includes('/me/teams?'))return Response.json(envelope)
    if(path.endsWith('/teams'))return Response.json(row,{status:201})
    if(path.endsWith('/invitations'))return Response.json(invite)
    if(path.includes('/team-invitations/'))return Response.json({...invite,status:'ACCEPTED',version:1})
    if(path.endsWith('/challenges'))return Response.json(challenge,{status:201})
    return Response.json({code:'not_found',detail:'Not available'}, {status:404})
  }))
})

it('reads authenticated private team pagination',async () => {
  const result=await listTeams()
  expect(result.content[0].name).toBe('Trail Pack')
  expect(vi.mocked(fetch).mock.calls.some(([input]) =>
    String(input).includes('/me/teams?page=0'))).toBe(true)
})

it('sends team writes with CSRF and never invents actor identifiers',async () => {
  await createTeam('Trail Pack')
  await inviteMember(id,'friend')
  await decideInvite(id,'accept',0)
  await addChallenge(id,other,7,2)
  const writes=vi.mocked(fetch).mock.calls.filter(([,init])=>init?.method==='POST')
  expect(writes).toHaveLength(4)
  for(const [,init] of writes)expect(new Headers(init?.headers).get('X-CSRF-TOKEN')).toBe('team-csrf')
  expect(String(writes[1][1]?.body)).not.toContain('senderId')
  expect(String(writes[2][1]?.body)).toContain('expectedVersion')
})

it('does not accept unvalidated team API responses',async () => {
  vi.mocked(fetch).mockResolvedValue(Response.json({content:[{...row,members:-1}]}))
  await expect(listTeams()).rejects.toMatchObject({code:'invalid_response'})
})

it('never replays a failed team creation automatically',async () => {
  let sent=0
  vi.mocked(fetch).mockImplementation(async input=>{
    if(String(input).endsWith('/auth/csrf'))return Response.json({csrfToken:'ok'})
    sent++;throw Error('network reset')
  })
  await expect(createTeam('Trail Pack')).rejects.toMatchObject({code:'network_error'})
  expect(sent).toBe(1)
})
