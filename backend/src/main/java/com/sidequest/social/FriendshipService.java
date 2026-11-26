package com.sidequest.social;

import java.time.Clock;
import java.sql.Timestamp;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class FriendshipService {
    public record Connection(UUID id, UUID profileId, String username, String state, boolean incoming, long version) { }
    public record Connections(List<Connection> content,int page,int size,long totalElements,
            int totalPages,boolean hasNext,boolean hasPrevious) { }
    private static void pagination(int page,int size) {
        if(page<0 || size<1 || size>100 || (long)page*size>Integer.MAX_VALUE)
            throw SocialProfileFailure.invalid();
    }
    private static int pages(long count,int size) {
        return (int)Math.min(Integer.MAX_VALUE,(count+size-1)/size);
    }
    private static Connections wrap(List<Connection> content,int page,int size,long count) {
        int totalPages=pages(count,size);
        return new Connections(content,page,size,count,totalPages,
                (long)page+1<totalPages,page>0);
    }
    public record BlockedUser(String username) { }
    public record BlockedPage(List<BlockedUser> content,int page,int size,long totalElements,
            int totalPages,boolean hasNext,boolean hasPrevious) { }
    public record FriendRank(String username, long rank, long earnedXp) { }
    public record FriendBoard(String period, List<FriendRank> content,int page,int size,long totalElements,
                              int totalPages,boolean hasNext,boolean hasPrevious) { }
    private final JdbcTemplate jdbc;
    private final Clock clock;
    public FriendshipService(JdbcTemplate jdbc, Clock clock) { this.jdbc=jdbc; this.clock=clock; }

    private UUID user(String username) {
        if (username==null || !username.matches("[a-z0-9_]{3,30}")) throw SocialProfileFailure.invalid();
        List<UUID> ids=jdbc.query("SELECT id FROM users WHERE username=? AND status='ACTIVE'",
                (rs,i)->rs.getObject(1,UUID.class),username);
        if (ids.isEmpty()) throw SocialProfileFailure.notFound();
        return ids.getFirst();
    }
    private void active(UUID who) {
        if (jdbc.queryForObject("SELECT count(*) FROM users WHERE id=? AND status='ACTIVE'",Integer.class,who)!=1)
            throw SocialProfileFailure.notFound();
    }
    private void notBlocked(UUID a, UUID b) {
        Long count=jdbc.queryForObject("""
            SELECT count(*) FROM social_blocks WHERE (blocker_id=? AND blocked_id=?) OR (blocker_id=? AND blocked_id=?)
            """,Long.class,a,b,b,a);
        if (count!=null && count>0) throw SocialProfileFailure.notFound();
    }
    private void ensureProfile(UUID user) {
        jdbc.update("""
            INSERT INTO social_profile_preferences(user_id,public_id,visibility)
            VALUES(?,?,'PRIVATE') ON CONFLICT(user_id) DO NOTHING
            """,user,UUID.randomUUID());
    }
    private void event(UUID actor, UUID peer, String action) {
        jdbc.update("INSERT INTO friend_request_events(id,actor_id,peer_id,action,created_at) VALUES(?,?,?,?,?)",
                UUID.randomUUID(),actor,peer,action,Timestamp.from(clock.instant()));
    }
    private void lock(String key,int namespace) {
        jdbc.query("SELECT pg_advisory_xact_lock(hashtextextended(CAST(? AS text), ?))",
                rs -> null, key, namespace);
    }
    private static String pairKey(UUID a,UUID b) {
        return (a.toString().compareTo(b.toString())<0?a:b).toString()+"|"+(a.toString().compareTo(b.toString())<0?b:a);
    }
    private void rate(UUID user) {
        Long recent=jdbc.queryForObject("""
            SELECT count(*) FROM friend_request_events
             WHERE actor_id=? AND action='SEND' AND created_at >= ?
            """,Long.class,user,Timestamp.from(clock.instant().minusSeconds(86400)));
        if(recent!=null && recent>=10) throw SocialProfileFailure.limited();
    }
    private Connection fromRow(java.sql.ResultSet rs, UUID owner) throws java.sql.SQLException {
        UUID requester=rs.getObject("requester_id",UUID.class);
        return new Connection(rs.getObject("id",UUID.class),rs.getObject("public_id",UUID.class),
            rs.getString("username"),rs.getString("state"),!owner.equals(requester),rs.getLong("version"));
    }
    @Transactional
    public Connection send(UUID actor, String peerUsername) {
        active(actor);
        UUID peer=user(peerUsername);
        if(actor.equals(peer)) throw SocialProfileFailure.invalid();
        lock(actor.toString(),437); // serialize send-count budget across distinct peers
        lock(pairKey(actor,peer),438); // serialize opposite requests and block
        notBlocked(actor,peer);
        UUID low=actor.toString().compareTo(peer.toString())<0?actor:peer,high=actor.toString().compareTo(peer.toString())<0?peer:actor;
        // The unordered primary key stops concurrent A->B and B->A duplicate rows.
        // Never automatically accept an opposite request.
        List<Map<String,Object>> existing=jdbc.queryForList("""
            SELECT id,state,requester_id,requested_at FROM friend_connections
             WHERE user_low_id=? AND user_high_id=? FOR UPDATE
            """,low,high);
        if(existing.isEmpty()) {
            rate(actor);
            ensureProfile(actor);ensureProfile(peer);
            int created=jdbc.update("""
                INSERT INTO friend_connections(id,user_low_id,user_high_id,requester_id,state,updated_at,requested_at)
                VALUES(?,?,?,?,'PENDING',?,?) ON CONFLICT(user_low_id,user_high_id) DO NOTHING
                """,UUID.randomUUID(),low,high,actor,Timestamp.from(clock.instant()),Timestamp.from(clock.instant()));
            if(created==0) throw SocialProfileFailure.conflict();
            event(actor,peer,"SEND");
        } else {
            var row=existing.getFirst();
            String state=(String)row.get("state");
            if("PENDING".equals(state) && actor.equals(row.get("requester_id"))) return connection(actor,low,high);
            if("PENDING".equals(state) || "ACCEPTED".equals(state)) throw SocialProfileFailure.conflict();
            Timestamp previous=(Timestamp)row.get("requested_at");
            if(previous!=null && previous.toInstant().isAfter(clock.instant().minusSeconds(3600)))
                throw SocialProfileFailure.limited();
            rate(actor);
            ensureProfile(actor);ensureProfile(peer);
            jdbc.update("""
                UPDATE friend_connections SET requester_id=?,state='PENDING',version=version+1,
                    requested_at=?,updated_at=? WHERE user_low_id=? AND user_high_id=?
                """,actor,Timestamp.from(clock.instant()),Timestamp.from(clock.instant()),low,high);
            event(actor,peer,"SEND");
        }
        return connection(actor,low,high);
    }
    private Connection connection(UUID actor,UUID low,UUID high) {
        List<Connection> rows=jdbc.query("""
            SELECT c.*,u.username,p.public_id FROM friend_connections c
            JOIN users u ON u.id=CASE WHEN c.user_low_id=? THEN c.user_high_id ELSE c.user_low_id END
            JOIN social_profile_preferences p ON p.user_id=u.id
            WHERE c.user_low_id=? AND c.user_high_id=?
            """,(rs,i)->fromRow(rs,actor),actor,low,high);
        if(rows.isEmpty()) throw SocialProfileFailure.notFound();
        return rows.getFirst();
    }
    @Transactional
    public Connection decide(UUID actor,UUID requestId,String action,Long expectedVersion) {
        if(expectedVersion==null || expectedVersion<0) throw SocialProfileFailure.invalid();
        if(!List.of("accept","reject","cancel","remove").contains(action)) throw SocialProfileFailure.invalid();
        List<Map<String,Object>> rows=jdbc.queryForList("""
            SELECT * FROM friend_connections WHERE id=? AND (user_low_id=? OR user_high_id=?) FOR UPDATE
            """,requestId,actor,actor);
        if(rows.isEmpty()) throw SocialProfileFailure.notFound();
        var row=rows.getFirst(); UUID requester=(UUID)row.get("requester_id");
        UUID low=(UUID)row.get("user_low_id"),high=(UUID)row.get("user_high_id");
        UUID peer=actor.equals(low)?high:low;
        notBlocked(actor,peer);
        String state=(String)row.get("state");
        boolean incoming=!actor.equals(requester);
        String next=switch(action) {
            case "accept" -> "PENDING".equals(state) && incoming ? "ACCEPTED" : null;
            case "reject" -> "PENDING".equals(state) && incoming ? "REJECTED" : null;
            case "cancel" -> "PENDING".equals(state) && !incoming ? "CANCELLED" : null;
            case "remove" -> "ACCEPTED".equals(state) ? "REMOVED" : null;
            default -> null;
        };
        if(next==null) throw SocialProfileFailure.notFound();
        int changed=jdbc.update("""
            UPDATE friend_connections SET state=?,version=version+1,updated_at=?
             WHERE id=? AND version=?
            """,next,Timestamp.from(clock.instant()),requestId,expectedVersion);
        if(changed!=1) throw SocialProfileFailure.conflict();
        event(actor,peer,action.toUpperCase(Locale.ROOT));
        return connection(actor,low,high);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Connections requests(UUID owner,String direction,int page,int size) {
        if(!List.of("incoming","outgoing").contains(direction)) throw SocialProfileFailure.invalid();
        pagination(page,size);
        active(owner);
        boolean incoming="incoming".equals(direction);
        String where="""
            FROM friend_connections c
            JOIN users u ON u.id=CASE WHEN c.user_low_id=? THEN c.user_high_id ELSE c.user_low_id END
            JOIN social_profile_preferences p ON p.user_id=u.id
            WHERE c.state='PENDING' AND (c.user_low_id=? OR c.user_high_id=?)
              AND (c.requester_id <> ?) = ?
              AND u.status='ACTIVE'
              AND NOT EXISTS(SELECT 1 FROM social_blocks b WHERE
                (b.blocker_id=c.user_low_id AND b.blocked_id=c.user_high_id)
                OR (b.blocker_id=c.user_high_id AND b.blocked_id=c.user_low_id))
            """;
        long total=jdbc.queryForObject("SELECT count(*) "+where,Long.class,owner,owner,owner,owner,incoming);
        var result=jdbc.query("SELECT c.*,u.username,p.public_id "+where+
                " ORDER BY c.requested_at DESC,c.id LIMIT ? OFFSET ?",
                (rs,i)->fromRow(rs,owner),owner,owner,owner,owner,incoming,size,(long)page*size);
        return wrap(result,page,size,total);
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public Connections friends(UUID owner,int page,int size) {
        pagination(page,size);
        active(owner);
        String where="""
            FROM friend_connections c
            JOIN users u ON u.id=CASE WHEN c.user_low_id=? THEN c.user_high_id ELSE c.user_low_id END
            JOIN social_profile_preferences p ON p.user_id=u.id
            WHERE c.state='ACCEPTED' AND (c.user_low_id=? OR c.user_high_id=?) AND u.status='ACTIVE'
              AND NOT EXISTS(SELECT 1 FROM social_blocks b WHERE
                (b.blocker_id=c.user_low_id AND b.blocked_id=c.user_high_id)
                OR (b.blocker_id=c.user_high_id AND b.blocked_id=c.user_low_id))
            """;
        long total=jdbc.queryForObject("SELECT count(*) "+where,Long.class,owner,owner,owner);
        var result=jdbc.query("SELECT c.*,u.username,p.public_id "+where+
                " ORDER BY u.username COLLATE \"C\",c.id LIMIT ? OFFSET ?",
                (rs,i)->fromRow(rs,owner),owner,owner,owner,size,(long)page*size);
        return wrap(result,page,size,total);
    }

    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public BlockedPage blocked(UUID owner,int page,int size) {
        pagination(page,size);
        active(owner);
        long total=jdbc.queryForObject("SELECT count(*) FROM social_blocks WHERE blocker_id=?",Long.class,owner);
        var rows=jdbc.query("""
            SELECT u.username FROM social_blocks b JOIN users u ON u.id=b.blocked_id
            WHERE b.blocker_id=? ORDER BY u.username COLLATE "C" LIMIT ? OFFSET ?
            """,(rs,i)->new BlockedUser(rs.getString(1)),owner,size,(long)page*size);
        int totalPages=pages(total,size);
        return new BlockedPage(rows,page,size,total,totalPages,(long)page+1<totalPages,page>0);
    }

    @Transactional
    public void block(UUID actor,String peerUsername,boolean blocking) {
        active(actor);UUID peer=user(peerUsername);
        if(actor.equals(peer)) throw SocialProfileFailure.invalid();
        lock(pairKey(actor,peer),438);
        if(blocking) {
            jdbc.update("INSERT INTO social_blocks(blocker_id,blocked_id) VALUES(?,?) ON CONFLICT DO NOTHING",actor,peer);
            jdbc.update("""
                UPDATE friend_connections SET state='REMOVED',version=version+1,updated_at=?
                WHERE state IN ('PENDING','ACCEPTED') AND
                  ((user_low_id=? AND user_high_id=?) OR (user_low_id=? AND user_high_id=?))
                """,Timestamp.from(clock.instant()),actor,peer,peer,actor);
            event(actor,peer,"BLOCK");
        } else {
            jdbc.update("DELETE FROM social_blocks WHERE blocker_id=? AND blocked_id=?",actor,peer);
            event(actor,peer,"UNBLOCK");
        }
    }
    @Transactional(readOnly=true,isolation=org.springframework.transaction.annotation.Isolation.REPEATABLE_READ)
    public FriendBoard leaderboard(UUID owner,String period,int page,int size) {
        pagination(page,size);
        active(owner);
        java.time.Instant now=clock.instant();
        java.time.LocalDate day=now.atZone(java.time.ZoneOffset.UTC).toLocalDate();
        java.time.Instant start=switch(period) {
            case "all" -> java.time.Instant.EPOCH;
            case "weekly" -> day.with(java.time.temporal.TemporalAdjusters.previousOrSame(java.time.DayOfWeek.MONDAY)).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            case "monthly" -> day.withDayOfMonth(1).atStartOfDay(java.time.ZoneOffset.UTC).toInstant();
            default -> throw SocialProfileFailure.invalid();
        };
        String ranked="""
            WITH peers AS (
                SELECT CAST(? AS uuid) AS id
                UNION
                SELECT CASE WHEN c.user_low_id=? THEN c.user_high_id ELSE c.user_low_id END
                  FROM friend_connections c
                 WHERE c.state='ACCEPTED' AND (c.user_low_id=? OR c.user_high_id=?)
                   AND NOT EXISTS (SELECT 1 FROM social_blocks b
                     WHERE (b.blocker_id=c.user_low_id AND b.blocked_id=c.user_high_id)
                        OR (b.blocker_id=c.user_high_id AND b.blocked_id=c.user_low_id))
            ), scores AS (
                SELECT p.user_id,SUM(x.amount::bigint)::bigint earned_xp
                  FROM xp_ledger x JOIN participations p ON p.id=x.participation_id
                  JOIN quest_moderation m ON m.quest_id=p.quest_id AND m.state='APPROVED'
                 WHERE x.awarded_at >= ? AND x.awarded_at <= ?
                   AND p.user_id IN (SELECT id FROM peers)
                   AND NOT EXISTS(SELECT 1 FROM quest_reports q WHERE q.quest_id=p.quest_id AND q.resolution='OPEN')
                 GROUP BY p.user_id
            ), ranked AS (
                SELECT u.username,s.earned_xp,RANK() OVER(ORDER BY s.earned_xp DESC) position
                  FROM scores s JOIN users u ON u.id=s.user_id WHERE u.status='ACTIVE'
            )
            """;
        Object[] args={owner,owner,owner,owner,Timestamp.from(start),Timestamp.from(now)};
        long total=jdbc.queryForObject(ranked+"SELECT count(*) FROM ranked",Long.class,args);
        var rows=jdbc.query(ranked+"""
            SELECT username,earned_xp,position FROM ranked
            ORDER BY earned_xp DESC,username COLLATE "C" LIMIT ? OFFSET ?
            """,(rs,i)->new FriendRank(rs.getString(1),rs.getLong(3),rs.getLong(2)),
            args[0],args[1],args[2],args[3],args[4],args[5],size,(long)page*size);
        int totalPages=pages(total,size);
        return new FriendBoard(period,rows,page,size,total,totalPages,
                (long)page+1<totalPages,page>0);
    }
}
