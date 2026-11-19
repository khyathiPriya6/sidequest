package com.sidequest.reward;

import static org.assertj.core.api.Assertions.*;

import com.sidequest.identity.User;
import com.sidequest.participation.Participation;
import com.sidequest.participation.ParticipationStatus;
import com.sidequest.quest.Quest;
import com.sidequest.quest.QuestCategory;
import com.sidequest.quest.QuestDifficulty;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class XpLedgerTest {
    private static final String HASH = "{argon2}$argon2id$test";

    @Test
    void awardsTheImmutableParticipationSnapshotOnce() {
        User participant = User.create("participant@example.test", "participant", HASH);
        Quest quest = Quest.create(
                User.create("creator@example.test", "reward_creator", HASH),
                "A valid quest title", "A valid quest description.",
                QuestCategory.FITNESS, QuestDifficulty.MEDIUM, 175);
        com.sidequest.quest.QuestFixtures.published(quest);
        Participation participation = Participation.join(participant, quest);
        participation.complete(participation.getJoinedAt().plusSeconds(30));

        XpLedger first = XpLedger.award(participation, participation.getCompletedAt().plusSeconds(1));

        assertThat(first.getId()).isNotNull();
        assertThat(first.getParticipationId()).isEqualTo(participation.getId());
        assertThat(first.getAmount()).isEqualTo(175);
        assertThat(first.getAwardedAt()).isEqualTo(participation.getCompletedAt().plusSeconds(1));
    }

    @Test
    void rejectsRewardsBeforeCompletionAndInvalidAmounts() {
        User participant = User.create("participant2@example.test", "participant2", HASH);
        Quest quest = Quest.create(
                User.create("creator2@example.test", "reward_creator2", HASH),
                "A valid quest title", "A valid quest description.",
                QuestCategory.SOCIAL, QuestDifficulty.EASY, 50);
        com.sidequest.quest.QuestFixtures.published(quest);
        Participation participation = Participation.join(participant, quest);

        assertThatThrownBy(() -> XpLedger.award(participation, Instant.now()))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.IN_PROGRESS);

        participation.complete(participation.getJoinedAt().plusSeconds(10));
        assertThatThrownBy(() -> XpLedger.award(participation,
                participation.getJoinedAt().minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
