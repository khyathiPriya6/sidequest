package com.sidequest.team;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class TeamService {
    public static final class Failure extends RuntimeException {
        public final int status; public final String code;
        Failure(int status,String code,String message){super(message);this.status=status;this.code=code;}
    }
    public record Team(UUID id,String name,boolean owner,int members) {}
    public record Invite(UUID id,UUID teamId,String teamName,String state,long version) {}
    public record OutgoingInvite(UUID id,String username,long version) {}
    public record Challenge(UUID id,UUID questId,String title,Instant startsAt,Instant endsAt,int completedMembers,long earnedXp) {}
    public record Score(String username,long earnedXp,long rank) {}
    public record Page<T>(List<T> content,int page,int size,long totalElements,int totalPages,boolean hasNext,boolean hasPrevious) {}
    private final JdbcTemplate jdbc; private final Clock clock;
    public TeamService(JdbcTemplate jdbc,Clock clock){this.jdbc=jdbc;this.clock=clock;}
    private static void validPage(int page,int size){
        if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE) throw bad();
    }
    private static <T> Page<T> page(List<T> items,int index,int size,long total){
        int pages=(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size);
        return new Page<>(items,index,size,total,pages,(long)index+1<pages,index>0);
    }
    private static Failure bad(){return new Failure(400,"invalid_team_request","Invalid team request.");}
    private static Failure hidden(){return new Failure(404,"not_found","Team or invitation unavailable.");}
    private static Failure conflict(){return new Failure(409,"team_conflict","Team state changed; refresh.");}
    private static Failure limited(){return new Failure(429,"team_limit","Team invitation limit reached.");}
    private void active(UUID user){
        Integer n=jdbc.queryForObject("SELECT count(*) FROM users WHERE id=? AND status='ACTIVE'",Integer.class,user);
        if(n==null||n!=1)throw hidden();
    }
    private void member(UUID actor,UUID team) {
        active(actor);
        if(jdbc.queryForObject("SELECT count(*) FROM team_members WHERE team_id=? AND user_id=?",Integer.class,team,actor)!=1)
            throw hidden();
    }
    private void owner(UUID actor,UUID team) {
        member(actor,team);
        if(jdbc.queryForObject("SELECT count(*) FROM teams WHERE id=? AND owner_id=?",Integer.class,team,actor)!=1)
            throw hidden();
    }
    private UUID lookup(String username) {
        if(username==null||!username.matches("[a-z0-9_]{3,30}"))throw bad();
        return jdbc.query("SELECT id FROM users WHERE username=? AND status='ACTIVE'",
            rs -> rs.next()?rs.getObject(1,UUID.class):null,username);
    }
    private Team teamRow(UUID actor,UUID team){
        Team found=jdbc.query("""
          SELECT t.id,t.name,t.owner_id,(SELECT count(*) FROM team_members m WHERE m.team_id=t.id) member_count
          FROM teams t WHERE t.id=?
          """,rs->rs.next()?new Team(rs.getObject("id",UUID.class),rs.getString("name"),
            actor.equals(rs.getObject("owner_id",UUID.class)),rs.getInt("member_count")):null,team);
        if(found==null)throw hidden();return found;
    }
    @Transactional
    public Team create(UUID actor,String name){
        active(actor);
        if(name==null||name.trim().length()<3||name.trim().length()>80)throw bad();
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO teams(id,owner_id,name) VALUES(?,?,?)",id,actor,name.trim());
        jdbc.update("INSERT INTO team_members(team_id,user_id) VALUES(?,?)",id,actor);
        return teamRow(actor,id);
    }
    @Transactional(readOnly=true)
    public Team detail(UUID actor,UUID id){member(actor,id);return teamRow(actor,id);}
    @Transactional(readOnly=true)
    public Page<Team> mine(UUID actor,int index,int size){
        validPage(index,size);active(actor);
        long count=jdbc.queryForObject("SELECT count(*) FROM team_members WHERE user_id=?",Long.class,actor);
        List<Team> rows=jdbc.query("""
          SELECT t.id,t.name,t.owner_id,(SELECT count(*) FROM team_members x WHERE x.team_id=t.id) member_count
          FROM teams t JOIN team_members m ON m.team_id=t.id
          WHERE m.user_id=? ORDER BY t.created_at DESC,t.id LIMIT ? OFFSET ?
          """,(rs,i)->new Team(rs.getObject("id",UUID.class),rs.getString("name"),
              actor.equals(rs.getObject("owner_id",UUID.class)),rs.getInt("member_count")),
              actor,size,(long)index*size);
        return page(rows,index,size,count);
    }
    @Transactional(readOnly=true)
    public Page<String> roster(UUID actor,UUID id,int index,int size){
        validPage(index,size);member(actor,id);
        long count=jdbc.queryForObject("SELECT count(*) FROM team_members WHERE team_id=?",Long.class,id);
        List<String> users=jdbc.query("""
          SELECT u.username FROM team_members m JOIN users u ON u.id=m.user_id
          WHERE m.team_id=? AND u.status='ACTIVE' ORDER BY u.username COLLATE "C" LIMIT ? OFFSET ?
          """,(rs,i)->rs.getString(1),id,size,(long)index*size);
        return page(users,index,size,count);
    }
    private void lockTeam(UUID team){
        jdbc.query("SELECT id FROM teams WHERE id=? FOR UPDATE",rs->null,team);
    }
    @Transactional
    public Invite invite(UUID actor,UUID team,String username){
        owner(actor,team);
        UUID peer=lookup(username);
        if(peer==null)throw hidden();
        if(peer.equals(actor))throw bad();
        lockTeam(team);
        if(jdbc.queryForObject("SELECT count(*) FROM team_members WHERE team_id=?",Integer.class,team)>=50)throw limited();
        if(jdbc.queryForObject("SELECT count(*) FROM team_invites WHERE team_id=? AND state='PENDING'",Integer.class,team)>=50)throw limited();
        Integer blocks=jdbc.queryForObject("""
          SELECT count(*) FROM social_blocks WHERE
           (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)
          """,Integer.class,actor,peer,peer,actor);
        if(blocks!=null&&blocks>0)throw hidden();
        if(jdbc.queryForObject("SELECT count(*) FROM team_members WHERE team_id=? AND user_id=?",Integer.class,team,peer)>0)
            throw conflict();
        Invite existing=jdbc.query("""
          SELECT i.id,i.team_id,t.name,i.state,i.version FROM team_invites i
          JOIN teams t ON t.id=i.team_id WHERE i.team_id=? AND i.invitee_id=? FOR UPDATE OF i
          """,rs->rs.next()?new Invite(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
            rs.getString(3),rs.getString(4),rs.getLong(5)):null,team,peer);
        if(existing!=null) {
            if(existing.state().equals("PENDING"))return existing;
            throw conflict(); // no unsolicited repeat invites after a decline
        }
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO team_invites(id,team_id,invitee_id,inviter_id) VALUES(?,?,?,?)",id,team,peer,actor);
        return new Invite(id,team,teamRow(actor,team).name(),"PENDING",0);
    }
    @Transactional(readOnly=true)
    public Page<Invite> incoming(UUID actor,int index,int size){
        validPage(index,size);active(actor);
        String where=" FROM team_invites i JOIN teams t ON t.id=i.team_id WHERE i.invitee_id=? AND i.state='PENDING' ";
        long n=jdbc.queryForObject("SELECT count(*) "+where,Long.class,actor);
        List<Invite> rows=jdbc.query("SELECT i.id,i.team_id,t.name,i.state,i.version "+where+
          " ORDER BY i.created_at DESC,i.id LIMIT ? OFFSET ?",
            (rs,k)->new Invite(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
                rs.getString(3),rs.getString(4),rs.getLong(5)),actor,size,(long)index*size);
        return page(rows,index,size,n);
    }
    @Transactional(readOnly=true)
    public Page<OutgoingInvite> outgoing(UUID actor,UUID team,int index,int size){
        validPage(index,size);owner(actor,team);
        String where="""
          FROM team_invites i JOIN users u ON u.id=i.invitee_id
          WHERE i.team_id=? AND i.state='PENDING'
          """;
        long n=jdbc.queryForObject("SELECT count(*) "+where,Long.class,team);
        List<OutgoingInvite> rows=jdbc.query("""
          SELECT i.id,u.username,i.version
          """+where+" ORDER BY i.created_at DESC,i.id LIMIT ? OFFSET ?",
            (rs,k)->new OutgoingInvite(rs.getObject(1,UUID.class),rs.getString(2),rs.getLong(3)),
            team,size,(long)index*size);
        return page(rows,index,size,n);
    }

    @Transactional
    public Invite respond(UUID actor,UUID id,boolean accept,long version){
        active(actor);
        UUID team=jdbc.query("SELECT team_id FROM team_invites WHERE id=? AND invitee_id=?",
            rs->rs.next()?rs.getObject(1,UUID.class):null,id,actor);
        if(team==null)throw hidden();
        lockTeam(team);
        var record=jdbc.queryForMap("SELECT state,version,inviter_id FROM team_invites WHERE id=? FOR UPDATE",id);
        if(!"PENDING".equals(record.get("state")))throw hidden();
        if(((Number)record.get("version")).longValue()!=version)throw conflict();
        if(accept) {
            if(jdbc.queryForObject("SELECT count(*) FROM team_members WHERE team_id=?",Integer.class,team)>=50)throw limited();
            UUID inviter=(UUID)record.get("inviter_id");
            Integer blocked=jdbc.queryForObject("""
              SELECT count(*) FROM social_blocks WHERE
              (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)
              """,Integer.class,actor,inviter,inviter,actor);
            if(blocked!=null&&blocked>0)throw hidden();
            jdbc.update("INSERT INTO team_members(team_id,user_id) VALUES(?,?) ON CONFLICT DO NOTHING",team,actor);
        }
        String next=accept?"ACCEPTED":"DECLINED";
        jdbc.update("UPDATE team_invites SET state=?,version=version+1,updated_at=? WHERE id=?",
            next,Timestamp.from(clock.instant()),id);
        return new Invite(id,team,teamRow(actor,team).name(),next,version+1);
    }
    @Transactional
    public void cancelInvite(UUID actor,UUID team,UUID inviteId,long version){
        owner(actor,team);
        if(version<0)throw bad();
        lockTeam(team);
        int changed=jdbc.update("""
          UPDATE team_invites SET state='CANCELLED',version=version+1,updated_at=?
           WHERE id=? AND team_id=? AND state='PENDING' AND version=?
          """,Timestamp.from(clock.instant()),inviteId,team,version);
        if(changed!=1)throw conflict();
    }

    @Transactional
    public void leaveOrRemove(UUID actor,UUID team,String username){
        member(actor,team);
        UUID peer=lookup(username);
        if(peer==null)throw hidden();
        Team t=teamRow(actor,team);
        if(!peer.equals(actor)&&!t.owner())throw hidden();
        if(t.owner() && peer.equals(actor))throw conflict();
        int deleted=jdbc.update("DELETE FROM team_members WHERE team_id=? AND user_id=?",team,peer);
        if(deleted!=1)throw hidden();
    }
    @Transactional
    public Challenge createChallenge(UUID actor,UUID team,UUID quest,Instant start,Instant end){
        owner(actor,team);
        if(start==null||end==null||!end.isAfter(start)||end.isAfter(start.plusSeconds(90L*86400)))
            throw bad();
        Integer allowed=jdbc.queryForObject("""
          SELECT count(*) FROM quests q JOIN quest_moderation m ON m.quest_id=q.id
          WHERE q.id=? AND q.status='PUBLISHED' AND m.state='APPROVED'
          AND NOT EXISTS (SELECT 1 FROM quest_reports r WHERE r.quest_id=q.id AND r.resolution='OPEN')
          """,Integer.class,quest);
        if(allowed==null||allowed!=1)throw hidden();
        UUID id=UUID.randomUUID();
        try {
            jdbc.update("""
              INSERT INTO team_challenges(id,team_id,quest_id,creator_id,starts_at,ends_at)
              VALUES(?,?,?,?,?,?)
              """,id,team,quest,actor,Timestamp.from(start),Timestamp.from(end));
        } catch(org.springframework.dao.DuplicateKeyException e) {throw conflict();}
        return challenge(id,team);
    }
    private Challenge challenge(UUID id,UUID team){
        Challenge c=jdbc.query("""
          SELECT c.id,c.quest_id,q.title,c.starts_at,c.ends_at,
          count(DISTINCT p.user_id) AS completed_members,COALESCE(sum(x.amount),0) AS earned_xp
          FROM team_challenges c JOIN quests q ON q.id=c.quest_id
          JOIN quest_moderation mod ON mod.quest_id=q.id AND mod.state='APPROVED'
          LEFT JOIN team_members tm ON tm.team_id=c.team_id
          LEFT JOIN participations p ON p.user_id=tm.user_id AND p.quest_id=c.quest_id
             AND p.status='COMPLETED' AND p.completed_at BETWEEN c.starts_at AND c.ends_at
          LEFT JOIN xp_ledger x ON x.participation_id=p.id
          WHERE c.id=? AND c.team_id=? AND q.status='PUBLISHED'
            AND NOT EXISTS(SELECT 1 FROM quest_reports rep WHERE rep.quest_id=q.id AND rep.resolution='OPEN')
          GROUP BY c.id,q.title
          """,rs->rs.next()?new Challenge(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
           rs.getString(3),rs.getTimestamp(4).toInstant(),rs.getTimestamp(5).toInstant(),
           rs.getInt(6),rs.getLong(7)):null,id,team);
        if(c==null)throw hidden();return c;
    }
    @Transactional(readOnly=true)
    public Page<Challenge> challenges(UUID actor,UUID team,int index,int size){
        validPage(index,size);member(actor,team);
        String eligible="""
          FROM team_challenges c JOIN quests q ON q.id=c.quest_id
          JOIN quest_moderation mo ON mo.quest_id=q.id AND mo.state='APPROVED'
          WHERE c.team_id=? AND q.status='PUBLISHED'
          AND NOT EXISTS(SELECT 1 FROM quest_reports rep WHERE rep.quest_id=q.id AND rep.resolution='OPEN')
          """;
        long n=jdbc.queryForObject("SELECT count(*) "+eligible,Long.class,team);
        List<UUID> ids=jdbc.query("SELECT c.id "+eligible+
          " ORDER BY c.starts_at DESC,c.id LIMIT ? OFFSET ?",
          (rs,i)->rs.getObject(1,UUID.class),team,size,(long)index*size);
        return page(ids.stream().map(id->challenge(id,team)).toList(),index,size,n);
    }
    @Transactional(readOnly=true)
    public Page<Score> scores(UUID actor,UUID team,int index,int size){
        validPage(index,size);member(actor,team);
        String ranked="""
          WITH scores AS (
            SELECT m.user_id,u.username,COALESCE(sum(x.amount),0)::bigint xp
            FROM team_members m JOIN users u ON u.id=m.user_id
            LEFT JOIN team_challenges c ON c.team_id=m.team_id
            LEFT JOIN quests q ON q.id=c.quest_id AND q.status='PUBLISHED'
            LEFT JOIN quest_moderation mo ON mo.quest_id=q.id AND mo.state='APPROVED'
            LEFT JOIN participations p ON p.quest_id=q.id AND p.user_id=m.user_id
                 AND p.status='COMPLETED' AND p.completed_at BETWEEN c.starts_at AND c.ends_at
                 AND mo.quest_id IS NOT NULL
                 AND NOT EXISTS(SELECT 1 FROM quest_reports rep WHERE rep.quest_id=q.id AND rep.resolution='OPEN')
            LEFT JOIN xp_ledger x ON x.participation_id=p.id
            WHERE m.team_id=? AND u.status='ACTIVE'
            GROUP BY m.user_id,u.username
          ), ranks AS (
            SELECT username,xp,RANK() OVER(ORDER BY xp DESC) rank FROM scores
          )
          """;
        long count=jdbc.queryForObject(ranked+" SELECT count(*) FROM ranks",Long.class,team);
        List<Score> rows=jdbc.query(ranked+"""
           SELECT username,xp,rank FROM ranks ORDER BY xp DESC,username COLLATE "C" LIMIT ? OFFSET ?
           """,(rs,i)->new Score(rs.getString(1),rs.getLong(2),rs.getLong(3)),
           team,size,(long)index*size);
        return page(rows,index,size,count);
    }
}
