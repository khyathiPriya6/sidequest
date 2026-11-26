package com.sidequest.group;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class GroupService {
    public record Page<T>(List<T> content,int page,int size,long totalElements,int totalPages,
            boolean hasNext,boolean hasPrevious) { }
    public record Team(UUID id,String name,String state,String role,long version,int members) { }
    public record Invite(UUID id,UUID teamId,String teamName,String sender,String recipient,
            String status,long version) { }
    public record Member(UUID id,String username,String role) { }
    public record Challenge(UUID id,UUID questId,String title,int targetCompletions,
            long completed,int earnedXp,Instant createdAt,Instant endsAt,String status) { }
    public record Score(String username,long earnedXp,long completed,long rank) { }
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public GroupService(JdbcTemplate jdbc,Clock clock) { this.jdbc=jdbc;this.clock=clock; }

    private static void page(int page,int size) {
        if(page<0||size<1||size>100||(long)page*size>Integer.MAX_VALUE)throw GroupFailure.bad();
    }
    private static <T> Page<T> paged(List<T> rows,int page,int size,long total) {
        int pages=(int)Math.min(Integer.MAX_VALUE,(total+size-1)/size);
        return new Page<>(rows,page,size,total,pages,(long)page+1<pages,page>0);
    }
    private void active(UUID user) {
        Integer count=jdbc.queryForObject("SELECT count(*) FROM users WHERE id=? AND status='ACTIVE'",Integer.class,user);
        if(count==null||count!=1)throw GroupFailure.missing();
    }
    private String teamLock(UUID id) {
        List<String> states=jdbc.query("SELECT state FROM teams WHERE id=? FOR UPDATE",
                (rs,n)->rs.getString(1),id);
        if(states.isEmpty())throw GroupFailure.missing();
        return states.getFirst();
    }
    private void member(UUID team,UUID actor) {
        active(actor);
        Integer count=jdbc.queryForObject("""
            SELECT count(*) FROM team_members m JOIN users u ON u.id=m.user_id
            WHERE m.team_id=? AND m.user_id=? AND m.state='ACTIVE' AND u.status='ACTIVE'
            """,Integer.class,team,actor);
        if(count==null||count!=1)throw GroupFailure.missing();
    }
    private void owner(UUID team,UUID actor,boolean needsActive) {
        active(actor);
        List<String> states=jdbc.query("""
            SELECT state FROM teams WHERE id=? AND owner_id=?
            """,(rs,n)->rs.getString(1),team,actor);
        if(states.isEmpty()||(needsActive&&!"ACTIVE".equals(states.getFirst())))throw GroupFailure.missing();
    }
    private void friends(UUID a,UUID b) {
        if(a.equals(b))throw GroupFailure.bad();
        Integer accepted=jdbc.queryForObject("""
            SELECT count(*) FROM friend_connections c WHERE c.state='ACCEPTED'
              AND ((c.user_low_id=? AND c.user_high_id=?) OR(c.user_low_id=? AND c.user_high_id=?))
              AND NOT EXISTS(SELECT 1 FROM social_blocks bl WHERE
                 (bl.blocker_id=? AND bl.blocked_id=?) OR (bl.blocker_id=? AND bl.blocked_id=?))
            """,Integer.class,a,b,b,a,a,b,b,a);
        if(accepted==null||accepted!=1)throw GroupFailure.missing();
    }
    private UUID user(String username) {
        if(username==null||!username.matches("[a-z0-9_]{3,30}"))throw GroupFailure.bad();
        List<UUID> ids=jdbc.query("SELECT id FROM users WHERE username=? AND status='ACTIVE'",
                (rs,n)->rs.getObject(1,UUID.class),username);
        if(ids.isEmpty())throw GroupFailure.missing();
        return ids.getFirst();
    }
    private Team teamRow(UUID id,UUID user) {
        List<Team> found=jdbc.query("""
            SELECT t.id,t.name,t.state,m.role,t.version,
              (SELECT count(*) FROM team_members mm JOIN users uu ON uu.id=mm.user_id
               WHERE mm.team_id=t.id AND mm.state='ACTIVE' AND uu.status='ACTIVE') members
            FROM teams t JOIN team_members m ON m.team_id=t.id
            WHERE t.id=? AND m.user_id=? AND m.state='ACTIVE'
            """,(rs,n)->new Team(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),
                rs.getString(4),rs.getLong(5),rs.getInt(6)),id,user);
        if(found.isEmpty())throw GroupFailure.missing();
        return found.getFirst();
    }
    @Transactional
    public Team create(UUID actor,String name) {
        active(actor);
        if(name==null||name.isBlank()||name.trim().length()<3||name.trim().length()>80)
            throw GroupFailure.bad();
        // Serialize per-owner team creation, even across concurrent sessions.
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text),611))",
                rs->null,actor.toString());
        Integer owned=jdbc.queryForObject("SELECT count(*) FROM teams WHERE owner_id=? AND state='ACTIVE'",
                Integer.class,actor);
        if(owned!=null&&owned>=10)throw GroupFailure.limited();
        UUID id=UUID.randomUUID();
        jdbc.update("INSERT INTO teams(id,owner_id,name) VALUES(?,?,?)",id,actor,name.trim());
        jdbc.update("""
            INSERT INTO team_members(team_id,user_id,role,state)
            VALUES(?,?,'OWNER','ACTIVE')
            """,id,actor);
        return teamRow(id,actor);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Team> myTeams(UUID user,int number,int size) {
        page(number,size);active(user);
        String where="FROM teams t JOIN team_members m ON m.team_id=t.id WHERE m.user_id=? AND m.state='ACTIVE'";
        long total=jdbc.queryForObject("SELECT count(*) "+where,Long.class,user);
        List<Team> rows=jdbc.query("""
            SELECT t.id,t.name,t.state,m.role,t.version,
               (SELECT count(*) FROM team_members mm JOIN users u ON u.id=mm.user_id
                WHERE mm.team_id=t.id AND mm.state='ACTIVE' AND u.status='ACTIVE') members
            """+where+" ORDER BY t.created_at DESC,t.id LIMIT ? OFFSET ?",
                (rs,n)->new Team(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3),
                    rs.getString(4),rs.getLong(5),rs.getInt(6)),user,size,(long)number*size);
        return paged(rows,number,size,total);
    }
    @Transactional(readOnly=true)
    public Team detail(UUID team,UUID user) {member(team,user);return teamRow(team,user);}
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Member> roster(UUID team,UUID user,int number,int size) {
        page(number,size);member(team,user);
        String where="""
            FROM team_members m JOIN users u ON u.id=m.user_id
            WHERE m.team_id=? AND m.state='ACTIVE' AND u.status='ACTIVE'
            """;
        long total=jdbc.queryForObject("SELECT count(*) "+where,Long.class,team);
        List<Member> rows=jdbc.query("SELECT u.id,u.username,m.role "+where+
                    " ORDER BY m.role DESC,u.username COLLATE \"C\",u.id LIMIT ? OFFSET ?",
                (rs,n)->new Member(rs.getObject(1,UUID.class),rs.getString(2),rs.getString(3)),
                team,size,(long)number*size);
        return paged(rows,number,size,total);
    }
    private Invite inviteRow(UUID id) {
        List<Invite> result=jdbc.query("""
            SELECT i.id,i.team_id,t.name,s.username,u.username,i.status,i.version
              FROM team_invitations i JOIN teams t ON t.id=i.team_id
              JOIN users u ON u.id=i.invited_user_id JOIN users s ON s.id=i.sender_id
             WHERE i.id=?
            """,(rs,n)->new Invite(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
                rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getLong(7)),id);
        if(result.isEmpty())throw GroupFailure.missing();
        return result.getFirst();
    }
    @Transactional
    public Invite invite(UUID team,UUID owner,String username) {
        if(!"ACTIVE".equals(teamLock(team)))throw GroupFailure.missing();
        owner(team,owner,true);
        UUID target=user(username);
        friends(owner,target);
        Integer already=jdbc.queryForObject("""
            SELECT count(*) FROM team_members WHERE team_id=? AND user_id=? AND state='ACTIVE'
            """,Integer.class,team,target);
        if(already!=null&&already>0)throw GroupFailure.conflict();
        Integer recent=jdbc.queryForObject("""
            SELECT count(*) FROM team_invitations WHERE sender_id=? AND created_at>=?
            """,Integer.class,owner,Timestamp.from(clock.instant().minus(1,ChronoUnit.DAYS)));
        if(recent!=null&&recent>=10)throw GroupFailure.limited();
        List<Map<String,Object>> prior=jdbc.queryForList("""
            SELECT id,status,updated_at FROM team_invitations WHERE team_id=? AND invited_user_id=? FOR UPDATE
            """,team,target);
        if(!prior.isEmpty()){
            Map<String,Object> row=prior.getFirst();
            if("PENDING".equals(row.get("status")))return inviteRow((UUID)row.get("id"));
            Timestamp previous=(Timestamp)row.get("updated_at");
            if(previous.toInstant().isAfter(clock.instant().minus(1,ChronoUnit.HOURS)))throw GroupFailure.limited();
            jdbc.update("""
                UPDATE team_invitations SET sender_id=?,status='PENDING',version=version+1,
                    created_at=?,updated_at=? WHERE team_id=? AND invited_user_id=?
                """,owner,Timestamp.from(clock.instant()),Timestamp.from(clock.instant()),team,target);
            return inviteRow((UUID)row.get("id"));
        }
        UUID id=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO team_invitations(id,team_id,invited_user_id,sender_id)
            VALUES(?,?,?,?)
            """,id,team,target,owner);
        return inviteRow(id);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Invite> invitations(UUID user,int number,int size) {
        page(number,size);active(user);
        String where="""
            FROM team_invitations i JOIN teams t ON t.id=i.team_id
            JOIN users u ON u.id=i.invited_user_id JOIN users s ON s.id=i.sender_id
            WHERE i.invited_user_id=? AND i.status='PENDING' AND t.state='ACTIVE'
            """;
        long total=jdbc.queryForObject("SELECT count(*) "+where,Long.class,user);
        List<Invite> rows=jdbc.query("""
            SELECT i.id,i.team_id,t.name,s.username,u.username,i.status,i.version
            """+where+" ORDER BY i.created_at DESC,i.id LIMIT ? OFFSET ?",
            (rs,n)->new Invite(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
                rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getLong(7)),
            user,size,(long)number*size);
        return paged(rows,number,size,total);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Invite> teamInvites(UUID team,UUID actor,int number,int size) {
        page(number,size);owner(team,actor,false);
        String where="""
            FROM team_invitations i JOIN teams t ON t.id=i.team_id
            JOIN users u ON u.id=i.invited_user_id JOIN users s ON s.id=i.sender_id
            WHERE i.team_id=? AND i.status='PENDING'
            """;
        long total=jdbc.queryForObject("SELECT count(*) "+where,Long.class,team);
        List<Invite> rows=jdbc.query("""
            SELECT i.id,i.team_id,t.name,s.username,u.username,i.status,i.version
            """+where+" ORDER BY i.created_at DESC,i.id LIMIT ? OFFSET ?",
            (rs,n)->new Invite(rs.getObject(1,UUID.class),rs.getObject(2,UUID.class),
                rs.getString(3),rs.getString(4),rs.getString(5),rs.getString(6),rs.getLong(7)),
            team,size,(long)number*size);
        return paged(rows,number,size,total);
    }

    @Transactional
    public Invite decide(UUID actor,UUID invitation,String action,long expected) {
        if(expected<0||!List.of("accept","reject","cancel").contains(action))throw GroupFailure.bad();
        // Lock team before invitation; all membership-changing operations use this order.
        List<UUID> ids=jdbc.query("SELECT team_id FROM team_invitations WHERE id=?",
                (rs,n)->rs.getObject(1,UUID.class),invitation);
        if(ids.isEmpty())throw GroupFailure.missing();
        UUID team=ids.getFirst();
        String state=teamLock(team);
        List<Map<String,Object>> rows=jdbc.queryForList("SELECT * FROM team_invitations WHERE id=? FOR UPDATE",
                invitation);
        if(rows.isEmpty())throw GroupFailure.missing();
        Map<String,Object> row=rows.getFirst();
        UUID recipient=(UUID)row.get("invited_user_id");
        UUID sender=(UUID)row.get("sender_id");
        if(!"PENDING".equals(row.get("status")))throw GroupFailure.missing();
        if("cancel".equals(action))owner(team,actor,false);
        else if(!actor.equals(recipient))throw GroupFailure.missing();
        active(actor);
        if("accept".equals(action)) {
            if(!"ACTIVE".equals(state))throw GroupFailure.missing();
            active(sender);friends(sender,actor);
            Integer count=jdbc.queryForObject("""
                SELECT count(*) FROM team_members m JOIN users u ON u.id=m.user_id
                WHERE m.team_id=? AND m.state='ACTIVE' AND u.status='ACTIVE'
                """,Integer.class,team);
            if(count!=null&&count>=20)throw GroupFailure.conflict();
            Integer exists=jdbc.queryForObject("""
                SELECT count(*) FROM team_members WHERE team_id=? AND user_id=? AND state='ACTIVE'
                """,Integer.class,team,actor);
            if(exists!=null&&exists>0)throw GroupFailure.conflict();
        }
        int changed=jdbc.update("""
            UPDATE team_invitations SET status=?,updated_at=?,version=version+1 WHERE id=? AND version=? AND status='PENDING'
            """,action.equals("accept")?"ACCEPTED":action.equals("reject")?"REJECTED":"CANCELLED",
            Timestamp.from(clock.instant()),invitation,expected);
        if(changed!=1)throw GroupFailure.conflict();
        if("accept".equals(action))jdbc.update("""
            INSERT INTO team_members(team_id,user_id,role,state)
            VALUES(?,?,'MEMBER','ACTIVE') ON CONFLICT(team_id,user_id) DO UPDATE
              SET state='ACTIVE',role='MEMBER',joined_at=CURRENT_TIMESTAMP,version=team_members.version+1
            """,team,actor);
        return inviteRow(invitation);
    }
    @Transactional
    public Team leave(UUID team,UUID actor) {
        teamLock(team);member(team,actor);
        ownerCheckNotSelf(team,actor);
        int changed=jdbc.update("""
            UPDATE team_members SET state='LEFT',version=version+1
            WHERE team_id=? AND user_id=? AND state='ACTIVE' AND role='MEMBER'
            """,team,actor);
        if(changed!=1)throw GroupFailure.missing();
        return new Team(team,"","LEFT","MEMBER",0,0);
    }
    private void ownerCheckNotSelf(UUID team,UUID actor) {
        Integer count=jdbc.queryForObject("SELECT count(*) FROM teams WHERE id=? AND owner_id=?",
                Integer.class,team,actor);
        if(count!=null&&count>0)throw GroupFailure.conflict();
    }
    @Transactional
    public void remove(UUID team,UUID actor,UUID target) {
        teamLock(team);owner(team,actor,false);
        ownerCheckNotSelf(team,target);
        int changed=jdbc.update("""
            UPDATE team_members SET state='REMOVED',version=version+1
            WHERE team_id=? AND user_id=? AND role='MEMBER' AND state='ACTIVE'
            """,team,target);
        if(changed!=1)throw GroupFailure.missing();
    }
    @Transactional
    public Team archive(UUID team,UUID actor) {
        teamLock(team);owner(team,actor,true);
        jdbc.update("UPDATE teams SET state='ARCHIVED',version=version+1 WHERE id=?",team);
        jdbc.update("UPDATE team_invitations SET status='CANCELLED',version=version+1,updated_at=? WHERE team_id=? AND status='PENDING'",
                Timestamp.from(clock.instant()),team);
        return teamRow(team,actor);
    }
    private void questEligible(UUID quest) {
        Integer allowed=jdbc.queryForObject("""
            SELECT count(*) FROM quests q JOIN quest_moderation m ON m.quest_id=q.id
            WHERE q.id=? AND q.status='PUBLISHED' AND m.state='APPROVED'
              AND NOT EXISTS(SELECT 1 FROM quest_reports r WHERE r.quest_id=q.id AND r.resolution='OPEN')
            """,Integer.class,quest);
        if(allowed==null||allowed!=1)throw GroupFailure.missing();
    }
    @Transactional
    public Challenge challenge(UUID team,UUID actor,UUID quest,int days,int target) {
        if(days<1||days>30||target<1||target>20||quest==null)throw GroupFailure.bad();
        if(!"ACTIVE".equals(teamLock(team)))throw GroupFailure.missing();
        owner(team,actor,true);questEligible(quest);
        Integer count=jdbc.queryForObject("""
            SELECT count(*) FROM team_challenges WHERE team_id=? AND ends_at>?
            """,Integer.class,team,Timestamp.from(clock.instant()));
        if(count!=null&&count>=20)throw GroupFailure.limited();
        UUID id=UUID.randomUUID();
        Instant now=clock.instant();
        // Exact team/Quest pair is unique across history: avoids duplicate XP representation.
        Integer present=jdbc.queryForObject("""
            SELECT count(*) FROM team_challenges WHERE team_id=? AND quest_id=?
            """,Integer.class,team,quest);
        if(present!=null&&present>0)throw GroupFailure.conflict();
        jdbc.update("""
            INSERT INTO team_challenges(id,team_id,quest_id,target_completions,created_at,ends_at)
            VALUES(?,?,?,?,?,?)
            """,id,team,quest,target,Timestamp.from(now),Timestamp.from(now.plus(days,ChronoUnit.DAYS)));
        return challengeRow(id);
    }
    private static final String CHALLENGE_PROGRESS = """
        SELECT c.id,c.quest_id,q.title,c.target_completions,c.created_at,c.ends_at,
          CASE WHEN q.status='PUBLISHED' AND mod.state='APPROVED'
             AND NOT EXISTS(SELECT 1 FROM quest_reports rep WHERE rep.quest_id=q.id AND rep.resolution='OPEN')
          THEN (SELECT count(*) FROM team_members m JOIN participations p ON p.user_id=m.user_id
                 JOIN xp_ledger x ON x.participation_id=p.id
                 JOIN users u ON u.id=m.user_id
                WHERE m.team_id=c.team_id AND m.state='ACTIVE' AND u.status='ACTIVE'
                  AND p.quest_id=c.quest_id AND p.status='COMPLETED'
                  AND x.awarded_at>=c.created_at AND x.awarded_at<=c.ends_at)
          ELSE 0 END AS completed,
          CASE WHEN q.status='PUBLISHED' AND mod.state='APPROVED'
             AND NOT EXISTS(SELECT 1 FROM quest_reports rep WHERE rep.quest_id=q.id AND rep.resolution='OPEN')
          THEN COALESCE((SELECT sum(x.amount) FROM team_members m JOIN participations p ON p.user_id=m.user_id
                 JOIN xp_ledger x ON x.participation_id=p.id JOIN users u ON u.id=m.user_id
                WHERE m.team_id=c.team_id AND m.state='ACTIVE' AND u.status='ACTIVE'
                  AND p.quest_id=c.quest_id AND p.status='COMPLETED'
                  AND x.awarded_at>=c.created_at AND x.awarded_at<=c.ends_at),0)
          ELSE 0 END AS earned_xp
        FROM team_challenges c JOIN quests q ON q.id=c.quest_id
        LEFT JOIN quest_moderation mod ON mod.quest_id=q.id
        """;
    private Challenge readChallenge(java.sql.ResultSet rs) throws java.sql.SQLException {
        Instant end=rs.getTimestamp("ends_at").toInstant();
        return new Challenge(rs.getObject("id",UUID.class),rs.getObject("quest_id",UUID.class),
            rs.getString("title"),rs.getInt("target_completions"),rs.getLong("completed"),
            rs.getInt("earned_xp"),rs.getTimestamp("created_at").toInstant(),end,
            clock.instant().isAfter(end)?"ENDED":"ACTIVE");
    }
    private Challenge challengeRow(UUID id) {
        List<Challenge> found=jdbc.query(CHALLENGE_PROGRESS+" WHERE c.id=?",
                (rs,n)->readChallenge(rs),id);
        if(found.isEmpty())throw GroupFailure.missing();
        return found.getFirst();
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Challenge> challenges(UUID team,UUID actor,int number,int size) {
        page(number,size);member(team,actor);
        long total=jdbc.queryForObject("SELECT count(*) FROM team_challenges WHERE team_id=?",Long.class,team);
        var rows=jdbc.query(CHALLENGE_PROGRESS+
            " WHERE c.team_id=? ORDER BY c.created_at DESC,c.id LIMIT ? OFFSET ?",
            (rs,n)->readChallenge(rs),team,size,(long)number*size);
        return paged(rows,number,size,total);
    }
    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Page<Score> leaderboard(UUID team,UUID actor,int number,int size) {
        page(number,size);member(team,actor);
        String ranked="""
            WITH eligible AS (
              SELECT m.user_id,u.username,
                COALESCE(SUM(x.amount) FILTER (WHERE p.status='COMPLETED'
                    AND x.awarded_at>=c.created_at AND x.awarded_at<=c.ends_at
                    AND q.status='PUBLISHED' AND mod.state='APPROVED'
                    AND NOT EXISTS(SELECT 1 FROM quest_reports r WHERE r.quest_id=q.id AND r.resolution='OPEN')),0) AS xp,
                COUNT(DISTINCT p.id) FILTER (WHERE p.status='COMPLETED'
                    AND x.awarded_at>=c.created_at AND x.awarded_at<=c.ends_at
                    AND q.status='PUBLISHED' AND mod.state='APPROVED'
                    AND NOT EXISTS(SELECT 1 FROM quest_reports r WHERE r.quest_id=q.id AND r.resolution='OPEN')) AS completed
              FROM team_members m JOIN users u ON u.id=m.user_id
              LEFT JOIN team_challenges c ON c.team_id=m.team_id
              LEFT JOIN quests q ON q.id=c.quest_id
              LEFT JOIN quest_moderation mod ON mod.quest_id=q.id
              LEFT JOIN participations p ON p.user_id=m.user_id AND p.quest_id=c.quest_id
              LEFT JOIN xp_ledger x ON x.participation_id=p.id
              WHERE m.team_id=? AND m.state='ACTIVE' AND u.status='ACTIVE'
              GROUP BY m.user_id,u.username
            ), ranked AS (
              SELECT username,xp,completed,RANK() OVER(ORDER BY xp DESC) position FROM eligible
            )
            """;
        long total=jdbc.queryForObject(ranked+" SELECT count(*) FROM ranked",Long.class,team);
        var rows=jdbc.query(ranked+"""
            SELECT username,xp,completed,position FROM ranked
            ORDER BY xp DESC,username COLLATE "C" LIMIT ? OFFSET ?
            """,(rs,n)->new Score(rs.getString(1),rs.getLong(2),rs.getLong(3),rs.getLong(4)),
                team,size,(long)number*size);
        return paged(rows,number,size,total);
    }
}
