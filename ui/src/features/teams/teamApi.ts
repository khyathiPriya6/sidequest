import { apiRequest, ApiError, isRecord } from '../../lib/api'
import { getCsrfToken } from '../auth/authApi'
export type Team = { id: string; name: string; owner: boolean; members: number }
export type Invite = { id: string; teamId: string; teamName: string; state: string; version: number }
export type OutgoingInvite = { id: string; username: string; version: number }
export type Challenge = { id: string; questId: string; title: string; startsAt: string; endsAt: string;
  completedMembers: number; earnedXp: number }
export type Score = { username: string; earnedXp: number; rank: number }
export type Page<T> = { content: T[]; page: number; size: number; totalElements: number;
  totalPages: number; hasNext: boolean; hasPrevious: boolean }
const invalid = () => new ApiError(502,'invalid_team_response','Unexpected team response. Refresh.')
const team = (v: unknown): v is Team => isRecord(v) && typeof v.id === 'string' &&
 typeof v.name === 'string' && typeof v.owner === 'boolean' && Number.isInteger(v.members)
const invite = (v: unknown): v is Invite => isRecord(v) && typeof v.id==='string' &&
 typeof v.teamId==='string' && typeof v.teamName==='string' &&
 typeof v.state==='string' && Number.isInteger(v.version)
const outgoingInvite = (v: unknown): v is OutgoingInvite => isRecord(v) &&
 typeof v.id==='string' && typeof v.username==='string' && Number.isInteger(v.version)
const challenge = (v: unknown): v is Challenge => isRecord(v) && typeof v.id==='string' &&
 typeof v.questId==='string' && typeof v.title==='string' &&
 typeof v.startsAt==='string' && typeof v.endsAt==='string' &&
 Number.isInteger(v.completedMembers) && Number.isInteger(v.earnedXp)
const score = (v: unknown): v is Score => isRecord(v) && typeof v.username==='string' &&
 Number.isInteger(v.earnedXp) && Number.isInteger(v.rank)
function isPage<T>(v: unknown,valid:(v: unknown)=>v is T):v is Page<T>{
 return isRecord(v) && Array.isArray(v.content) && v.content.every(valid) &&
 Number.isInteger(v.page) && Number.isInteger(v.size) &&
 Number.isInteger(v.totalElements) && Number.isInteger(v.totalPages) &&
 typeof v.hasNext==='boolean' && typeof v.hasPrevious==='boolean'
}
async function read<T>(url:string,valid:(v:unknown)=>v is T,signal?:AbortSignal):Promise<T>{
 const data:unknown=await apiRequest(url,{signal})
 if(!valid(data))throw invalid()
 return data
}
async function mutate<T>(url:string,method:'POST'|'DELETE',body:object|undefined,valid?:((v:unknown)=>v is T)):Promise<T>{
 const csrf=(await getCsrfToken()).csrfToken
 const data:unknown=await apiRequest(url,{
   method,headers:{'X-CSRF-TOKEN':csrf,...(body?{'Content-Type':'application/json'}:{})},
   ...(body?{body:JSON.stringify(body)}:{})
 })
 if(valid && !valid(data))throw invalid()
 return data as T
}
const qs=(page:number)=>'?page='+page+'&size=20'
export const myTeams=(page=0,signal?:AbortSignal)=>read('/api/v1/me/teams'+qs(page),(v):v is Page<Team>=>isPage(v,team),signal)
export const createTeam=(name:string)=>mutate('/api/v1/me/teams','POST',{name},team)
export const teamInfo=(id:string,signal?:AbortSignal)=>read('/api/v1/teams/'+encodeURIComponent(id),team,signal)
export const teamMembers=(id:string,page=0,signal?:AbortSignal)=>read('/api/v1/teams/'+encodeURIComponent(id)+'/members'+qs(page),
 (v):v is Page<string>=>isPage(v,(s):s is string=>typeof s==='string'),signal)
export const outgoingInvites=(id:string,page=0,signal?:AbortSignal)=>read('/api/v1/teams/'+encodeURIComponent(id)+'/invites'+qs(page),
 (v):v is Page<OutgoingInvite>=>isPage(v,outgoingInvite),signal)
export const cancelTeamInvite=(team:string,id:string,expectedVersion:number)=>mutate<void>(
 '/api/v1/teams/'+encodeURIComponent(team)+'/invites/'+encodeURIComponent(id)+'/cancel','POST',{expectedVersion})
export const inviteToTeam=(id:string,username:string)=>mutate('/api/v1/teams/'+encodeURIComponent(id)+'/invites',
 'POST',{username},invite)
export const myInvites=(page=0,signal?:AbortSignal)=>read('/api/v1/me/team-invites'+qs(page),(v):v is Page<Invite>=>isPage(v,invite),signal)
export const respondInvite=(id:string,action:'accept'|'decline',expectedVersion:number)=>
 mutate('/api/v1/me/team-invites/'+encodeURIComponent(id)+'/'+action,'POST',{expectedVersion},invite)
export const removeMember=(id:string,username:string)=>mutate<void>('/api/v1/teams/'+encodeURIComponent(id)+
 '/members/'+encodeURIComponent(username),'DELETE',undefined)
export const createChallenge=(id:string,questId:string,startsAt:string,endsAt:string)=>mutate(
 '/api/v1/teams/'+encodeURIComponent(id)+'/challenges','POST',
 {questId,startsAt,endsAt},challenge)
export const challenges=(id:string,page=0,signal?:AbortSignal)=>read('/api/v1/teams/'+encodeURIComponent(id)+'/challenges'+qs(page),
 (v):v is Page<Challenge>=>isPage(v,challenge),signal)
export const scores=(id:string,page=0,signal?:AbortSignal)=>read('/api/v1/teams/'+encodeURIComponent(id)+'/leaderboard'+qs(page),
 (v):v is Page<Score>=>isPage(v,score),signal)
