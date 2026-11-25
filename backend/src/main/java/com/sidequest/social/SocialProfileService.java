package com.sidequest.social;

import com.sidequest.identity.AccountStatus;
import java.time.Clock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Social visibility is independent of M4 leaderboard visibility and private XP history. */
@Service
public class SocialProfileService {
    public record Preferences(UUID profileId, String visibility, long version) { }
    public record Profile(UUID profileId, String displayName) { }
    private final JdbcTemplate jdbc;
    private final Clock clock;

    public SocialProfileService(JdbcTemplate jdbc, Clock clock) {
        this.jdbc = jdbc;
        this.clock = clock;
    }

    @Transactional
    public Preferences settings(UUID owner) {
        requireActive(owner);
        // The first owner read assigns a random, stable social-only identifier.
        // Concurrency is safe because the user_id PK selects one persisted identifier.
        jdbc.update("""
            INSERT INTO social_profile_preferences (user_id, public_id, visibility)
            VALUES (?, ?, 'PRIVATE') ON CONFLICT (user_id) DO NOTHING
            """, owner, UUID.randomUUID());
        return findSettings(owner);
    }

    @Transactional
    public Preferences update(UUID owner, String visibility, Long expectedVersion) {
        if (visibility == null || !visibility.matches("PRIVATE|FRIENDS|PUBLIC") || expectedVersion == null
                || expectedVersion < 0) {
            throw SocialProfileFailure.invalid();
        }
        requireActive(owner);
        // Serialize first-row allocation with concurrent PATCH/GET, then do CAS.
        jdbc.update("""
            INSERT INTO social_profile_preferences (user_id, public_id, visibility)
            VALUES (?, ?, 'PRIVATE') ON CONFLICT (user_id) DO NOTHING
            """, owner, UUID.randomUUID());
        int changed = jdbc.update("""
            UPDATE social_profile_preferences
               SET visibility = ?, version = version + 1, updated_at = ?
             WHERE user_id = ? AND version = ?
            """, visibility, java.sql.Timestamp.from(clock.instant()), owner, expectedVersion);
        if (changed != 1) throw SocialProfileFailure.conflict();
        return findSettings(owner);
    }

    @Transactional(readOnly = true)
    public Profile profile(UUID viewer, UUID publicId) {
        Profile visible = jdbc.query("""
            SELECT p.public_id, u.username
              FROM social_profile_preferences p
              JOIN users u ON u.id = p.user_id
             WHERE p.public_id = ? AND u.status = 'ACTIVE'
               AND (p.user_id = ? OR (
                   NOT EXISTS (SELECT 1 FROM social_blocks blocked
                     WHERE (blocked.blocker_id=p.user_id AND blocked.blocked_id=?)
                        OR (blocked.blocker_id=? AND blocked.blocked_id=p.user_id))
                   AND (p.visibility = 'PUBLIC' OR
                    (p.visibility='FRIENDS' AND EXISTS (
                       SELECT 1 FROM friend_connections c
                       WHERE c.state='ACCEPTED'
                         AND ((c.user_low_id=p.user_id AND c.user_high_id=?)
                          OR (c.user_high_id=p.user_id AND c.user_low_id=?))
                         AND NOT EXISTS (SELECT 1 FROM social_blocks b
                           WHERE (b.blocker_id=p.user_id AND b.blocked_id=?)
                              OR (b.blocker_id=? AND b.blocked_id=p.user_id))
                    ))))
            """, rs -> rs.next() ? new Profile(rs.getObject(1, UUID.class), rs.getString(2)) : null,
                publicId, viewer, viewer, viewer, viewer, viewer, viewer, viewer);
        if (visible == null) throw SocialProfileFailure.notFound();
        return visible;
        // FRIENDS remains inaccessible to others until M5-03 introduces accepted friendships.
    }

    private Preferences findSettings(UUID owner) {
        Preferences preferences = jdbc.query("""
            SELECT public_id, visibility, version
              FROM social_profile_preferences WHERE user_id = ?
            """, rs -> rs.next() ? new Preferences(rs.getObject(1, UUID.class),
                rs.getString(2), rs.getLong(3)) : null, owner);
        if (preferences == null) throw SocialProfileFailure.notFound();
        return preferences;
    }

    private void requireActive(UUID owner) {
        Boolean active = jdbc.query("""
            SELECT status FROM users WHERE id = ?
            """, rs -> rs.next() && AccountStatus.ACTIVE.name().equals(rs.getString(1)), owner);
        if (!Boolean.TRUE.equals(active)) throw SocialProfileFailure.notFound();
    }
}
