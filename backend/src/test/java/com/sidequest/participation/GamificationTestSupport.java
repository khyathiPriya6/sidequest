package com.sidequest.participation;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

abstract class GamificationTestSupport extends ParticipationApiSupport {
    UUID award(Session creator, Session player, Instant at, int xp) {
        UUID q=UUID.randomUUID(), p=UUID.randomUUID();
        jdbc.update("""
            INSERT INTO quests(id,creator_id,title,description,category,difficulty,reward_xp,status,created_at,updated_at,published_at,version)
            VALUES(?,?,'Ranking fixture','A safe historical activity.','LEARNING','EASY',?,'PUBLISHED',?,?,?,0)
            """,q,creator.id(),xp,Timestamp.from(at.minusSeconds(60)),Timestamp.from(at.minusSeconds(60)),Timestamp.from(at.minusSeconds(60)));
        jdbc.update("INSERT INTO quest_moderation(quest_id,state,policy_version,version) VALUES(?,'APPROVED','v1',0)",q);
        jdbc.update("INSERT INTO participations(id,user_id,quest_id,status,reward_xp,joined_at,completed_at) VALUES(?,?,?,'COMPLETED',?,?,?)",
                p,player.id(),q,xp,Timestamp.from(at.minusSeconds(30)),Timestamp.from(at));
        jdbc.update("INSERT INTO xp_ledger(id,participation_id,amount,awarded_at) VALUES(?,?,?,?)",UUID.randomUUID(),p,xp,Timestamp.from(at));
        return q;
    }

}
