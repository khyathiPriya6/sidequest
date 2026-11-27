import React, { useCallback, useEffect, useState } from 'react'
import {
  ActivityIndicator, Alert, FlatList, Keyboard, Pressable, SafeAreaView,
  ScrollView, StatusBar, StyleSheet, Text, TextInput, View,
} from 'react-native'
import { api, type Achievement, type Challenge, type Connection,
  type Invitation, type LegacyQuest, type Member, type Participation, type Profile, type Review, type SocialSettings,
  type Score, type Team, type User } from './src/client'
import type { Quest } from './src/api'

const color = {
  bg: '#0B1020', card: '#151C30', raised: '#202A43', border: '#364460',
  text: '#F5F7FF', muted: '#C0CBE2', accent: '#A78BFA', cyan: '#67E8F9',
  reward: '#F6C665', success: '#74D9A0', danger: '#FB8794',
}
type Tab = 'Explore' | 'My Quests' | 'Create' | 'Profile' | 'Friends' | 'Teams' | 'Moderation'
const tabs: Tab[] = ['Explore','My Quests','Create','Profile','Friends','Teams']
const categories = ['ADVENTURE','CREATIVITY','FITNESS','LEARNING','FOOD','SOCIAL']
const difficulties = ['EASY','MEDIUM','HARD']

const message = (e: unknown) => e instanceof Error ? e.message : 'Something went wrong.'
function Label({children}:{children:React.ReactNode}) { return <Text style={styles.label}>{children}</Text> }
function Small({children}:{children:React.ReactNode}) { return <Text style={styles.muted}>{children}</Text> }
function Card({children}:{children:React.ReactNode}) { return <View style={styles.card}>{children}</View> }
function Action({label,onPress,disabled=false,kind='primary',compact=false}:{
  label:string;onPress:()=>void;disabled?:boolean;kind?:'primary'|'secondary'|'danger';compact?:boolean
}) {
  return <Pressable accessibilityRole="button" accessibilityLabel={label}
    accessibilityState={{disabled}} disabled={disabled} onPress={onPress}
    style={({pressed})=>[styles.button,compact&&styles.compactButton,
      kind==='secondary'&&styles.secondaryButton,kind==='danger'&&styles.dangerButton,
      (disabled||pressed)&&styles.pressedButton]}>
    <Text style={[styles.buttonText,kind!=='primary'&&{color:color.text}]}>{label}</Text>
  </Pressable>
}
function Field({label,value,onChange,secure=false,multiline=false,keyboardType='default',placeholder}:{
  label:string;value:string;onChange:(s:string)=>void;secure?:boolean;multiline?:boolean;
  keyboardType?:'default'|'email-address'|'numeric';placeholder?:string
}) {
  const [focused,setFocused]=useState(false)
  const identifier=label.toLowerCase()
  const isIdentity=keyboardType==='email-address'||identifier.includes('username')||identifier.includes('quest id')
  return <View style={styles.field}>
    <Label>{label}</Label>
    <TextInput
      accessibilityLabel={label} accessibilityHint={secure?'Password is hidden':undefined}
      value={value} onChangeText={onChange} secureTextEntry={secure}
      multiline={multiline} keyboardType={keyboardType}
      autoCapitalize={isIdentity||secure?'none':'sentences'}
      autoCorrect={!isIdentity&&!secure} onFocus={()=>setFocused(true)} onBlur={()=>setFocused(false)}
      placeholder={placeholder??('Enter '+label.toLowerCase())}
      placeholderTextColor="#8795B3"
      selectionColor={color.accent}
      style={[styles.input,focused&&styles.inputFocused,multiline&&styles.multilineInput]}
    />
  </View>
}
function Chips({values,selected,onChange}:{values:string[];selected:string;onChange:(v:string)=>void}) {
  return <View style={styles.chips} accessibilityRole="radiogroup">{values.map(v=><Pressable
    key={v} accessibilityRole="radio" accessibilityState={{checked:v===selected}}
    onPress={()=>onChange(v)} style={({pressed})=>[styles.chip,v===selected&&styles.selectedChip,
      pressed&&styles.pressedButton]}>
    <Text style={[styles.chipText,v===selected&&styles.selectedChipText]}>{v.replaceAll('_',' ')}</Text>
  </Pressable>)}</View>
}
function Section({title,children}:{title:string;children:React.ReactNode}) {
  return <View style={styles.section}>
    <View style={styles.sectionHeader}><Text accessibilityRole="header" style={styles.sectionTitle}>{title}</Text>
      <View style={styles.sectionAccent}/></View>
    {children}
  </View>
}
function formatQuest(q:Quest,onPress:()=>void) {
  return <Pressable key={q.id} accessibilityRole="button" onPress={onPress} style={styles.card}>
    <View style={styles.chips}><Text style={styles.tag}>{q.category??'QUEST'}</Text>
      <Text style={styles.tag}>{q.difficulty??'UNRATED'}</Text></View>
    <Text style={styles.heading}>{q.title}</Text>
    <Text style={styles.muted} numberOfLines={3}>{q.description||'Make today an adventure.'}</Text>
    <Text style={styles.reward}>✦ {q.rewardXp??0} XP <Text style={styles.actionLink}>  · View details →</Text></Text>
  </Pressable>
}

export default function App() {
  const [user,setUser] = useState<User|null>(null)
  const [authReady,setAuthReady] = useState(false)
  const [authMode,setAuthMode] = useState<'login'|'register'>('login')
  const [email,setEmail] = useState('')
  const [username,setUsername] = useState('')
  const [password,setPassword] = useState('')
  const [tab,setTab] = useState<Tab>('Explore')
  const [selected,setSelected] = useState<Quest|null>(null)
  const [selectedParticipation,setSelectedParticipation] = useState<Participation|null>(null)
  const [selectedTeam,setSelectedTeam] = useState<Team|null>(null)
  const [busy,setBusy] = useState(false)
  const [loading,setLoading] = useState(false)
  const [error,setError] = useState('')
  const [notice,setNotice] = useState('')
  const [revision,setRevision] = useState(0)
  const [moderator,setModerator] = useState(false)
  const [items,setItems] = useState<Quest[]>([])
  const [hasNext,setHasNext] = useState(false)
  const [page,setPage] = useState(0)
  const [search,setSearch] = useState('')
  const [searchTerm,setSearchTerm] = useState('')
  const [myQuests,setMyQuests] = useState<Quest[]>([])
  const [participations,setParticipations] = useState<Participation[]>([])
  const [profile,setProfile] = useState<Profile|null>(null)
  const [achievements,setAchievements] = useState<Achievement[]>([])
  const [xpHistory,setXpHistory] = useState<{id:string;questTitle:string;amount:number}[]>([])
  const [settings,setSettings] = useState<SocialSettings|null>(null)
  const [friends,setFriends] = useState<Connection[]>([])
  const [outgoing,setOutgoing] = useState<Connection[]>([])
  const [incoming,setIncoming] = useState<Connection[]>([])
  const [friendRanks,setFriendRanks] = useState<Score[]>([])
  const [teams,setTeams] = useState<Team[]>([])
  const [teamInvites,setTeamInvites] = useState<Invitation[]>([])
  const [sentInvites,setSentInvites] = useState<Invitation[]>([])
  const [members,setMembers] = useState<Member[]>([])
  const [challenges,setChallenges] = useState<Challenge[]>([])
  const [teamRanks,setTeamRanks] = useState<Score[]>([])
  const [reviews,setReviews] = useState<Review[]>([])
  const [legacyReviews,setLegacyReviews] = useState<LegacyQuest[]>([])
  const [draftName,setDraftName] = useState('')
  const [draftDescription,setDraftDescription] = useState('')
  const [category,setCategory] = useState('ADVENTURE')
  const [difficulty,setDifficulty] = useState('EASY')
  const [reward,setReward] = useState('30')
  const [editingId,setEditingId] = useState<string|null>(null)
  const [friendName,setFriendName] = useState('')
  const [blockName,setBlockName] = useState('')
  const [teamName,setTeamName] = useState('')
  const [inviteName,setInviteName] = useState('')
  const [challengeQuest,setChallengeQuest] = useState('')
  const [challengeDays,setChallengeDays] = useState('7')
  const [challengeTarget,setChallengeTarget] = useState('2')
  const [reason,setReason] = useState('DANGEROUS_ACT')
  const [reportReason,setReportReason] = useState('OTHER')

  const refresh=()=>setRevision(n=>n+1)
  const changeTab=(next:Tab)=>{setTab(next);setSelected(null);setSelectedTeam(null);setError('');setNotice('');setPage(0)}
  useEffect(()=>{
    let active=true
    void api.me().then(u=>{if(active)setUser(u)}).catch(()=>{}).finally(()=>{if(active)setAuthReady(true)})
    return ()=>{active=false}
  },[])
  useEffect(()=>{
    if(!user){setModerator(false);return}
    let active=true
    void api.moderation().then(()=>{if(active)setModerator(true)}).catch(()=>{if(active)setModerator(false)})
    return ()=>{active=false}
  },[user?.id])
  useEffect(()=>{
    let active=true
    setLoading(true);setError('')
    const run=async()=>{
      if(tab==='Explore') {
        const r=await api.quests(searchTerm,page)
        if(active){setItems(r.content);setHasNext(r.hasNext)}
      }
      if(!user)return
      if(tab==='My Quests') {
        const [quests,progress]=await Promise.all([api.myQuests(),api.participations()])
        if(active){setMyQuests(quests.content);setParticipations(progress.content)}
      }
      if(tab==='Profile'){
        const [p,a,x,s]=await Promise.all([api.profile(),api.achievements(),api.xp(),api.socialSettings()])
        if(active){setProfile(p);setAchievements(a.achievements);setXpHistory(x.content);setSettings(s)}
      }
      if(tab==='Friends'){
        const [f,i,o,r]=await Promise.all([api.friends(),api.requests('incoming'),api.requests('outgoing'),api.friendsBoard()])
        if(active){setFriends(f.content);setIncoming(i.content);setOutgoing(o.content);setFriendRanks(r.content)}
      }
      if(tab==='Teams'){
        const [t,i]=await Promise.all([api.teams(),api.teamInvitations()])
        if(active){setTeams(t.content);setTeamInvites(i.content)}
      }
      if(tab==='Moderation'&&moderator){
        const [r,l]=await Promise.all([api.moderation(),api.legacyReviews()])
        if(active){setReviews(r.content);setLegacyReviews(l.content)}
      }
    }
    void run().catch(e=>{if(active)setError(message(e))}).finally(()=>{if(active)setLoading(false)})
    return ()=>{active=false}
  },[tab,user?.id,searchTerm,page,revision,moderator])

  useEffect(()=>{
    if(!selectedTeam)return
    let active=true
    void Promise.all([api.teamMembers(selectedTeam.id),api.challenges(selectedTeam.id),api.teamBoard(selectedTeam.id),
      selectedTeam.role==='OWNER'?api.teamSentInvites(selectedTeam.id):Promise.resolve({content:[] as Invitation[]})])
      .then(([m,c,r,i])=>{if(active){setMembers(m.content);setChallenges(c.content);setTeamRanks(r.content);setSentInvites(i.content)}})
      .catch(e=>{if(active)setError(message(e))})
    return ()=>{active=false}
  },[selectedTeam?.id,revision])

  async function act(operation:()=>Promise<unknown>,success:string,after?:()=>void){
    if(busy)return
    setBusy(true);setError('');setNotice('')
    try { await operation();setNotice(success);after?.();refresh() }
    catch(e){setError(message(e));refresh()}
    finally{setBusy(false)}
  }
  function confirm(title:string,description:string,operation:()=>Promise<unknown>,success:string,after?:()=>void){
    Alert.alert(title,description,[{text:'Cancel',style:'cancel'},
      {text:'Confirm',onPress:()=>void act(operation,success,after)}])
  }
  async function authenticate(){
    if(busy)return
    setBusy(true);setError('')
    try{
      const u=authMode==='login'?await api.login(email.trim(),password):
        await api.register(email.trim(),username.trim(),password)
      setUser(u);setPassword('');setNotice('Signed in as @'+u.username);refresh()
    }catch(e){setError(message(e))}finally{setBusy(false)}
  }
  function openQuest(q:Quest){
    setSelected(q);setSelectedParticipation(null);setError('');setNotice('')
    void api.quest(q.id).then(setSelected).catch(e=>setError(message(e)))
    if(user) void api.byQuest(q.id).then(rows=>setSelectedParticipation(rows.content[0]??null))
      .catch(e=>setError(message(e)))
  }
  function startDraft(q?:Quest){
    setEditingId(q?.id??null);setDraftName(q?.title??'');setDraftDescription(q?.description??'')
    setCategory(q?.category??'ADVENTURE');setDifficulty(q?.difficulty??'EASY')
    setReward(String(q?.rewardXp??30));changeTab('Create')
  }
  async function saveDraft(){
    const points=Number(reward)
    if(!draftName.trim() || !Number.isInteger(points) || points<1 || points>1000){
      setError('Enter a title and an XP reward between 1 and 1000.');return
    }
    const body={title:draftName.trim(),description:draftDescription.trim(),category,difficulty,rewardXp:points}
    await act(async()=>{
      if(editingId){const previous=await api.draft(editingId);return api.updateDraft(editingId,body,previous.etag)}
      return api.createDraft(body)
    },'Quest draft saved.',()=>changeTab('My Quests'))
  }
  const visibleTabs=moderator?[...tabs,'Moderation' as Tab]:tabs

  return <SafeAreaView style={styles.root}>
    <StatusBar barStyle="light-content"/>
    <View style={styles.header}>
      <Text style={styles.brand}>✦ SideQuest</Text>
      <Text style={styles.muted}>{user?'@'+user.username+' · '+tab:'Your story, one Quest at a time.'}</Text>
    </View>
    {error!==''&&<View style={styles.banner}><Text style={styles.error}>{error}</Text>
      <Action label="Dismiss" kind="secondary" onPress={()=>setError('')}/></View>}
    {notice!==''&&<View style={styles.banner}><Text style={styles.success}>{notice}</Text>
      <Pressable onPress={()=>setNotice('')}><Text style={styles.actionLink}>Dismiss</Text></Pressable></View>}
    {!authReady?<ActivityIndicator style={{marginTop:40}} color={color.accent}/>:
    <ScrollView keyboardShouldPersistTaps="handled" contentContainerStyle={styles.content}>
      {selected ? <Section title={selected.title}>
        <Action kind="secondary" label="← Back" onPress={()=>setSelected(null)}/>
        <Card><Text style={styles.reward}>✦ {selected.rewardXp??0} XP</Text>
          <Small>{selected.category??'Quest'} · {selected.difficulty??'Not rated'}</Small>
          <Text style={styles.body}>{selected.description||'No description provided.'}</Text>
          {user&&selectedParticipation?.status==='COMPLETED'?<Text style={styles.success}>Completed</Text>:
          user&&selectedParticipation?<Action label="Complete Quest" disabled={busy}
            onPress={()=>confirm('Complete Quest?','Completion is permanent; XP is awarded once.',
              ()=>api.complete(selectedParticipation!.id),'Quest completed.',()=>{setSelected(null);changeTab('My Quests')})}/>:
          !user?<Action label="Sign in to join" onPress={()=>changeTab('Profile')}/>:
          selected.creatorId===user.id?<Small>You cannot join your own Quest.</Small>:
          <Action label="Join Quest" disabled={busy} onPress={()=>void act(()=>api.join(selected.id),'Quest joined.',
            ()=>{setSelected(null);changeTab('My Quests')})}/>}
        </Card>
        {user && selected.status==='PUBLISHED' && selected.creatorId!==user.id && <>
          <Label>Report Quest for safety review</Label>
          <Chips values={['DANGEROUS_ACT','SELF_HARM','ILLEGAL','HATE','FRAUD_SPAM','PRIVACY','OTHER']}
            selected={reportReason} onChange={setReportReason}/>
          <Action kind="secondary" label="Submit report" disabled={busy} onPress={()=>confirm(
            'Report Quest?','This is an official report about '+selected.title+'.',
            ()=>api.reportQuest(selected.id,reportReason),'Report submitted.')}/>
        </>}
      </Section> :
      tab==='Explore'?<Section title="Explore Quests">
        <Text style={styles.hero}>Make today a little more adventurous.</Text>
        <Field label="Search Quests" value={search} onChange={setSearch}/>
        <Action label="Search" onPress={()=>{setPage(0);setSearchTerm(search.trim());Keyboard.dismiss()}}/>
        {loading&&<ActivityIndicator color={color.accent}/>}
        {!loading&&items.length===0&&<Card><Small>No Quests found.</Small></Card>}
        {items.map(q=>formatQuest(q,()=>openQuest(q)))}
        <View style={styles.actions}><Action compact kind="secondary" label="Previous" disabled={page===0}
          onPress={()=>setPage(n=>n-1)}/><Text style={styles.muted}>Page {page+1}</Text>
          <Action compact kind="secondary" label="Next" disabled={!hasNext} onPress={()=>setPage(n=>n+1)}/></View>
      </Section>:
      !user?<Section title="Welcome to SideQuest">
        <Chips values={['login','register']} selected={authMode} onChange={x=>setAuthMode(x as 'login'|'register')}/>
        <Field label="Email" value={email} onChange={setEmail} keyboardType="email-address"/>
        {authMode==='register'&&<Field label="Username" value={username} onChange={setUsername}/>}
        <Field label="Password" value={password} onChange={setPassword} secure/>
        <Action label={busy?'Please wait…':authMode==='login'?'Sign in':'Create account'} disabled={busy}
          onPress={()=>void authenticate()}/>
        <Small>Accounts and Quests are shared with the SideQuest web application.</Small>
      </Section>:
      tab==='My Quests'?<Section title="My Quests">
        <Action label="Create a Quest" onPress={()=>startDraft()}/>
        <Text style={styles.subheading}>Joined Quests</Text>
        {participations.length===0&&<Small>No joined Quests yet.</Small>}
        {participations.map(p=><Card key={p.id}><Text style={styles.heading}>{p.quest.title}</Text>
          <Small>{p.status} · {p.rewardXp} XP</Small>
          {p.status==='IN_PROGRESS'&&<Action label="Complete" disabled={busy} onPress={()=>
            confirm('Complete Quest?','This action is permanent and awards XP once.',()=>api.complete(p.id),'Quest completed.')}/>}
        </Card>)}
        <Text style={styles.subheading}>Created Quests</Text>
        {myQuests.map(q=><Card key={q.id}><Text style={styles.heading}>{q.title}</Text><Small>{q.status}</Small>
          {q.status==='DRAFT'&&<><Action kind="secondary" label="Edit draft" onPress={()=>startDraft(q)}/>
            <Action label="Submit for review" disabled={busy} onPress={()=>confirm('Submit Quest?','A moderator must approve this Quest before publishing.',
              async()=>{const d=await api.draft(q.id);return api.submitReview(q.id,d.etag)},'Submitted for review.')}/>
            <Action kind="secondary" label="Publish (after approval)" disabled={busy}
              onPress={()=>void act(async()=>{const d=await api.draft(q.id);return api.publish(q.id,d.etag)},'Quest published.')}/></>}
          {q.status==='PUBLISHED'&&<Action kind="danger" label="Archive" disabled={busy} onPress={()=>
            confirm('Archive Quest?','Existing participation history is preserved.',
              async()=>{const d=await api.draft(q.id);return api.archive(q.id,d.etag)},'Quest archived.')}/>}
        </Card>)}
      </Section>:
      tab==='Create'?<Section title={editingId?'Edit Quest':'Create Quest'}>
        <Field label="Title" value={draftName} onChange={setDraftName}/>
        <Field label="Description" value={draftDescription} onChange={setDraftDescription} multiline/>
        <Label>Category</Label><Chips values={categories} selected={category} onChange={setCategory}/>
        <Label>Difficulty</Label><Chips values={difficulties} selected={difficulty} onChange={setDifficulty}/>
        <Field label="XP reward (1–1000)" value={reward} onChange={setReward} keyboardType="numeric"/>
        <Action label={busy?'Saving…':'Save draft'} disabled={busy} onPress={()=>void saveDraft()}/>
        <Small>Drafts must pass moderator review before publishing.</Small>
      </Section>:
      tab==='Profile'?<Section title="Your Profile">
        {profile&&<Card><Text style={styles.hero}>@{profile.username}</Text>
          <Text style={styles.reward}>✦ {profile.totalXp} XP · Level {profile.level}</Text>
          <Small>Level progress: {Math.round(profile.progressPercent)}%</Small>
          <Small>{profile.activeQuests} active · {profile.completedQuests} completed · {profile.joinedQuests} joined</Small></Card>}
        <Text style={styles.subheading}>Profile privacy</Text>
        {settings&&<Chips values={['PRIVATE','FRIENDS','PUBLIC']} selected={settings.visibility}
          onChange={v=>void act(()=>api.setVisibility(v,settings.version),'Privacy updated.')}/>}
        <Text style={styles.subheading}>Achievements</Text>
        {achievements.map(a=><Card key={a.id}><Text style={styles.heading}>{a.unlocked?'★ ':'☆ '}{a.name}</Text>
          <Small>{a.description}</Small><Small>{a.progress} / {a.target}</Small></Card>)}
        <Text style={styles.subheading}>XP History</Text>
        {xpHistory.map(x=><Card key={x.id}><Text style={styles.heading}>{x.questTitle}</Text>
          <Text style={styles.reward}>+{x.amount} XP</Text></Card>)}
        <Action kind="danger" label="Sign out" disabled={busy} onPress={()=>confirm('Sign out?','End your session on this device?',
          ()=>api.logout(),'Signed out.',()=>{setUser(null);setModerator(false);changeTab('Explore')})}/>
      </Section>:
      tab==='Friends'?<Section title="Friends & Leaderboard">
        <Field label="Friend username" value={friendName} onChange={setFriendName}/>
        <Action label="Send friend request" disabled={busy} onPress={()=>void act(()=>api.addFriend(friendName.trim()),'Friend request sent.',()=>setFriendName(''))}/>
        <Text style={styles.subheading}>Incoming requests</Text>
        {incoming.filter(c=>c.state==='PENDING').map(c=><Card key={c.id}><Text style={styles.heading}>@{c.username}</Text>
          <Action label="Accept" disabled={busy} onPress={()=>void act(()=>api.friendDecision(c.id,'accept',c.version),'Friend added.')}/>
          <Action kind="secondary" label="Reject" disabled={busy} onPress={()=>confirm('Reject request?','Reject this friend request?',
            ()=>api.friendDecision(c.id,'reject',c.version),'Request rejected.')}/></Card>)}
        <Text style={styles.subheading}>Outgoing requests</Text>
        {outgoing.filter(c=>c.state==='PENDING').map(c=><Card key={c.id}>
          <Small>Waiting for @{c.username}</Small>
          <Action kind="secondary" label="Cancel request" disabled={busy}
            onPress={()=>confirm('Cancel request?','Cancel the pending friend request?',
              ()=>api.friendDecision(c.id,'cancel',c.version),'Request cancelled.')}/>
        </Card>)}
        <Text style={styles.subheading}>My Friends</Text>
        {friends.map(f=><Card key={f.id}><Text style={styles.heading}>@{f.username}</Text>
          <Action kind="danger" label="Remove" disabled={busy} onPress={()=>confirm('Remove friend?','Remove @'+f.username+'?',
            ()=>api.friendDecision(f.id,'remove',f.version),'Friend removed.')}/></Card>)}
        <Text style={styles.subheading}>Block a user</Text>
        <Field label="Username to block" value={blockName} onChange={setBlockName}/>
        <Action kind="danger" label="Block user" disabled={busy} onPress={()=>confirm('Block user?',
          'Block @'+blockName+' from social interactions?',()=>api.block(blockName.trim()),'User blocked.',()=>setBlockName(''))}/>
        <Text style={styles.subheading}>XP Leaderboard</Text>
        {friendRanks.map(r=><Card key={r.username}><Text style={styles.body}>#{r.rank} @{r.username} · {r.earnedXp} XP</Text></Card>)}
      </Section>:
      tab==='Teams'?<Section title={selectedTeam?selectedTeam.name:'Teams & Challenges'}>
        {selectedTeam?<><Action kind="secondary" label="← All teams" onPress={()=>setSelectedTeam(null)}/>
          <Card><Small>{selectedTeam.state} · {selectedTeam.role} · {selectedTeam.members} members</Small></Card>
          <Text style={styles.subheading}>Members</Text>
          {members.map(m=><Card key={m.id}><Text style={styles.body}>@{m.username} · {m.role}</Text>
            {selectedTeam.role==='OWNER'&&m.role!=='OWNER'&&<Action kind="danger" label="Remove member" disabled={busy} onPress={()=>
              confirm('Remove member?','Remove @'+m.username+' from this team?',()=>api.removeMember(selectedTeam.id,m.id),'Member removed.')}/>}</Card>)}
          {selectedTeam.role==='OWNER'&&selectedTeam.state==='ACTIVE'&&<>
            <Field label="Invite a friend by username" value={inviteName} onChange={setInviteName}/>
            <Action label="Invite to team" disabled={busy} onPress={()=>void act(()=>api.inviteTeam(selectedTeam.id,inviteName.trim()),'Invitation sent.',()=>setInviteName(''))}/>
            <Text style={styles.subheading}>Sent invitations</Text>
            {sentInvites.filter(i=>i.status==='PENDING').map(i=><Card key={i.id}>
              <Small>Waiting for @{i.recipient}</Small>
              <Action kind="secondary" label="Cancel invitation" disabled={busy}
                onPress={()=>confirm('Cancel invitation?','Cancel the pending team invitation?',
                  ()=>api.cancelTeamInvite(i.id,i.version),'Invitation cancelled.')}/>
            </Card>)}
            <Text style={styles.subheading}>New group challenge</Text>
            <Field label="Published Quest ID" value={challengeQuest} onChange={setChallengeQuest}/>
            <Field label="Duration (days)" value={challengeDays} onChange={setChallengeDays} keyboardType="numeric"/>
            <Field label="Target completions" value={challengeTarget} onChange={setChallengeTarget} keyboardType="numeric"/>
            <Action label="Create challenge" disabled={busy} onPress={()=>void act(
              ()=>api.addChallenge(selectedTeam.id,challengeQuest.trim(),Number(challengeDays),Number(challengeTarget)),'Challenge created.')}/>
            <Action kind="danger" label="Archive team" disabled={busy} onPress={()=>confirm('Archive team?','This restricts future team activity.',
              ()=>api.archiveTeam(selectedTeam.id),'Team archived.',()=>setSelectedTeam(null))}/>
          </>}
          {selectedTeam.role==='MEMBER'&&selectedTeam.state==='ACTIVE'&&
            <Action kind="danger" label="Leave team" disabled={busy} onPress={()=>confirm('Leave team?','You will lose access to private team data.',
              ()=>api.leaveTeam(selectedTeam.id),'Left team.',()=>setSelectedTeam(null))}/>}
          <Text style={styles.subheading}>Challenges</Text>
          {challenges.map(c=><Card key={c.id}><Text style={styles.heading}>{c.title}</Text>
            <Small>{c.status} · {c.completed}/{c.targetCompletions} completions</Small>
            <Text style={styles.reward}>{c.earnedXp} XP</Text></Card>)}
          <Text style={styles.subheading}>Team Leaderboard</Text>
          {teamRanks.map(r=><Card key={r.username}><Text style={styles.body}>#{r.rank} @{r.username} · {r.earnedXp} XP · {r.completed} completed</Text></Card>)}
        </>:<>
          <Field label="New team name" value={teamName} onChange={setTeamName}/>
          <Action label="Create team" disabled={busy} onPress={()=>void act(()=>api.createTeam(teamName.trim()),'Team created.',()=>setTeamName(''))}/>
          <Text style={styles.subheading}>Invitations</Text>
          {teamInvites.filter(i=>i.status==='PENDING').map(i=><Card key={i.id}><Text style={styles.heading}>{i.teamName}</Text>
            <Small>From @{i.sender}</Small>
            <Action label="Accept invitation" disabled={busy} onPress={()=>void act(()=>api.inviteDecision(i.id,'accept',i.version),'Joined team.')}/>
            <Action kind="secondary" label="Reject" disabled={busy} onPress={()=>confirm('Reject invitation?','Reject invitation to '+i.teamName+'?',
              ()=>api.inviteDecision(i.id,'reject',i.version),'Invitation rejected.')}/></Card>)}
          <Text style={styles.subheading}>My Teams</Text>
          {teams.map(t=><Pressable key={t.id} accessibilityRole="button" style={styles.card} onPress={()=>setSelectedTeam(t)}>
            <Text style={styles.heading}>{t.name}</Text><Small>{t.role} · {t.members} members · {t.state}</Small>
            <Text style={styles.actionLink}>Open team →</Text></Pressable>)}
        </>}
      </Section>:
      tab==='Moderation'&&moderator?<Section title="Quest moderation">
        <Small>Only assigned moderators can approve, reject or hide Quests. Decisions are audited.</Small>
        {reviews.length===0&&!loading&&<Card><Small>No Quests require review.</Small></Card>}
        {legacyReviews.length>0&&<Text style={styles.subheading}>Previously published Quests</Text>}
        {legacyReviews.map(q=><Card key={q.questId}>
          <Text style={styles.heading}>{q.title}</Text><Small>{q.description}</Small>
          <Action kind="secondary" disabled={busy} label="Intake for review" onPress={()=>confirm(
            'Send for review?','This queues the Quest; it does not approve it.',
            ()=>api.intakeLegacy(q.questId),'Quest queued for review.')}/>
        </Card>)}
        {reviews.map(r=><Card key={r.questId}><Text style={styles.heading}>{r.title}</Text>
          <Text style={styles.body}>{r.description}</Text>
          <Small>{r.state} · {r.reports} open reports</Small>
          {['PENDING_REVIEW','UNDER_REVIEW'].includes(r.state)&&<>
            <Action label="Approve" disabled={busy} onPress={()=>confirm('Approve Quest?','Confirm you reviewed '+r.title+'.',
              ()=>api.moderate(r,'APPROVE',''),'Quest approved.')}/>
            <Label>Rejection reason</Label><Chips values={['DANGEROUS_ACT','SELF_HARM','ILLEGAL','FRAUD_SPAM','PRIVACY','OTHER']}
              selected={reason} onChange={setReason}/>
            <Action kind="danger" label="Reject" disabled={busy} onPress={()=>confirm('Reject Quest?',r.title+' · '+reason,
              ()=>api.moderate(r,'REJECT',reason),'Quest rejected.')}/>
          </>}
          {r.state==='APPROVED'&&r.questStatus==='PUBLISHED'&&<>
            <Action kind="danger" label="Hide Quest" disabled={busy} onPress={()=>confirm('Hide Quest?',r.title,
              ()=>api.moderate(r,'HIDE',reason),'Quest hidden.')}/>
            {r.reports>0&&<Action kind="secondary" label="Dismiss reports" disabled={busy} onPress={()=>confirm('Dismiss reports?',r.title,
              ()=>api.moderate(r,'DISMISS_REPORTS','NO_POLICY_VIOLATION'),'Reports dismissed.')}/>}
          </>}
        </Card>)}
      </Section>:null}
      {loading&&tab!=='Explore'&&<ActivityIndicator color={color.accent}/>}
      {!loading&&user&&<Action kind="secondary" label="Refresh" onPress={refresh}/>}
    </ScrollView>}
    <View style={styles.nav}>
      <FlatList horizontal showsHorizontalScrollIndicator={false} data={user?visibleTabs:['Explore','Profile'] as Tab[]}
        keyExtractor={i=>i} contentContainerStyle={styles.navContent}
        renderItem={({item})=><Pressable accessibilityRole="tab" accessibilityState={{selected:tab===item}}
          onPress={()=>changeTab(item)} style={[styles.navItem,tab===item&&styles.navActive]}>
          <Text style={[styles.navText,tab===item&&{color:color.accent}]}>{item}</Text></Pressable>}/>
    </View>
  </SafeAreaView>
}

const styles=StyleSheet.create({
  root:{flex:1,backgroundColor:color.bg},
  header:{paddingHorizontal:22,paddingTop:20,paddingBottom:16,borderBottomWidth:1,borderColor:color.border,gap:5},
  brand:{fontSize:25,fontWeight:'800',letterSpacing:-0.5,color:color.accent},
  content:{paddingHorizontal:20,paddingTop:24,paddingBottom:48,gap:20},
  section:{gap:18},
  sectionHeader:{gap:11,marginBottom:1},
  sectionTitle:{color:color.text,fontSize:26,fontWeight:'800',letterSpacing:-0.6,lineHeight:34},
  sectionAccent:{width:38,height:3,borderRadius:2,backgroundColor:color.accent},
  hero:{color:color.text,fontSize:29,fontWeight:'800',lineHeight:38,letterSpacing:-0.5},
  heading:{color:color.text,fontSize:18,fontWeight:'700',lineHeight:25},
  subheading:{color:color.cyan,fontSize:17,fontWeight:'700',marginTop:18,marginBottom:2},
  label:{color:color.text,fontSize:14,fontWeight:'600',letterSpacing:0.15},
  muted:{color:color.muted,fontSize:14,lineHeight:21},
  body:{color:color.text,fontSize:15,lineHeight:24},
  reward:{color:color.reward,fontSize:17,fontWeight:'700'},
  card:{backgroundColor:color.card,borderColor:color.border,borderWidth:1,borderRadius:18,padding:20,gap:14},
  field:{gap:9,alignSelf:'stretch',width:'100%'},
  input:{color:color.text,backgroundColor:color.card,borderColor:color.border,borderWidth:1.5,
    borderRadius:12,minHeight:54,paddingHorizontal:15,paddingVertical:12,fontSize:16,width:'100%'},
  inputFocused:{borderColor:color.accent,backgroundColor:color.raised},
  multilineInput:{minHeight:118,textAlignVertical:'top'},
  button:{minHeight:50,paddingHorizontal:16,paddingVertical:13,borderRadius:12,backgroundColor:color.accent,
    alignItems:'center',justifyContent:'center',alignSelf:'stretch',width:'100%'},
  compactButton:{alignSelf:'auto',width:'auto',minHeight:44,paddingHorizontal:14},
  secondaryButton:{backgroundColor:color.raised,borderWidth:1,borderColor:color.border},
  dangerButton:{backgroundColor:'#7b3046',borderWidth:1,borderColor:color.danger},
  pressedButton:{opacity:0.56},
  buttonText:{fontSize:15,fontWeight:'700',color:color.bg,textAlign:'center'},
  actionLink:{color:color.cyan,fontWeight:'700'},
  chips:{flexDirection:'row',gap:8,flexWrap:'wrap'},
  chip:{backgroundColor:color.raised,paddingHorizontal:14,paddingVertical:11,borderRadius:22,
    borderWidth:1,borderColor:color.border,minHeight:42,justifyContent:'center'},
  selectedChip:{backgroundColor:color.accent,borderColor:color.accent},
  chipText:{color:color.muted,fontSize:12,fontWeight:'700'},
  selectedChipText:{color:color.bg},
  tag:{color:color.accent,fontSize:11,fontWeight:'700',backgroundColor:color.raised,padding:8,borderRadius:20},
  actions:{flexDirection:'row',alignItems:'center',justifyContent:'space-between',gap:8},
  banner:{padding:14,backgroundColor:color.raised,gap:8,borderBottomWidth:1,borderColor:color.border},
  error:{color:color.danger,fontSize:14},success:{color:color.success,fontSize:14},
  nav:{borderTopWidth:1,borderColor:color.border,backgroundColor:color.card,minHeight:66},
  navContent:{paddingHorizontal:10,alignItems:'center',gap:6},
  navItem:{minHeight:45,paddingHorizontal:13,justifyContent:'center',borderRadius:12},
  navActive:{backgroundColor:color.raised},navText:{fontSize:12,fontWeight:'700',color:color.muted},
})
