package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;

import com.sidequest.identity.User;
import com.sidequest.quest.Quest;
import com.sidequest.quest.QuestCategory;
import com.sidequest.quest.QuestDifficulty;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class ParticipationTest {
    private static final String HASH = "{argon2}$argon2id$test";

    @Test
    void joinsWithServerOwnedImmutableSnapshot() {
        User user = User.create("player@example.test", "player_one", HASH);
        Quest quest = Quest.create(
                User.create("creator@example.test", "quest_creator", HASH),
                "A valid quest title", "A valid quest description.",
                QuestCategory.ADVENTURE, QuestDifficulty.EASY, 250);

        com.sidequest.quest.QuestFixtures.published(quest);
        Participation participation = Participation.join(user, quest);

        assertThat(participation.getId()).isNotNull();
        assertThat(participation.getUserId()).isEqualTo(user.getId());
        assertThat(participation.getQuestId()).isEqualTo(quest.getId());
        assertThat(participation.getRewardXp()).isEqualTo(250);
        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.IN_PROGRESS);
        assertThat(participation.getJoinedAt()).isNotNull();
        assertThat(participation.getCompletedAt()).isNull();
    }

    @Test
    void rejectsInvalidRewardSnapshotsAndCompletionOrdering() {
        User user = User.create("player2@example.test", "player_two", HASH);
        Quest quest = Quest.create(
                User.create("creator2@example.test", "quest_creator2", HASH),
                "A valid quest title", "A valid quest description.",
                QuestCategory.LEARNING, QuestDifficulty.MEDIUM, 100);
        com.sidequest.quest.QuestFixtures.published(quest);
        Participation participation = Participation.join(user, quest);

        assertThatThrownBy(() -> participation.complete(participation.getJoinedAt().minusSeconds(1)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.IN_PROGRESS);

        Instant completion = participation.getJoinedAt().plusSeconds(10);
        participation.complete(completion);
        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.COMPLETED);
        assertThat(participation.getCompletedAt()).isEqualTo(completion);

        participation.complete(completion.plusSeconds(100));
        assertThat(participation.getCompletedAt()).isEqualTo(completion);
    }

    @Test
    void requiresAQuestRewardSnapshotInRange() {
        User user = User.create("player3@example.test", "player_three", HASH);
        assertThatThrownBy(() -> Participation.join(user, Quest.create(
                User.create("creator3@example.test", "quest_creator3", HASH),
                "A valid quest title", null, null, null, null)))
                .isInstanceOf(IllegalArgumentException.class);
    }
    @Test
    void rejectsDraftArchivedAndCreatorEnrollment() {
        User creator = User.create("creator@example.test", "creator", HASH);
        User player = User.create("player@example.test", "player", HASH);
        Quest quest = Quest.create(creator, "Eligible quest", "A valid description.",
                QuestCategory.LEARNING, QuestDifficulty.EASY, 100);
        assertThatThrownBy(() -> Participation.join(player, quest))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Only published quests can be joined");
        com.sidequest.quest.QuestFixtures.published(quest);
        assertThatThrownBy(() -> Participation.join(creator, quest))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Creators cannot join their own quests");
        com.sidequest.quest.QuestFixtures.archived(quest);
        assertThatThrownBy(() -> Participation.join(player, quest))
                .isInstanceOf(IllegalArgumentException.class).hasMessage("Only published quests can be joined");
    }

    @org.junit.jupiter.params.ParameterizedTest
    @org.junit.jupiter.params.provider.ValueSource(ints = {1, 1000})
    void acceptsRewardBoundariesAndCompletionAtJoinTime(int reward) {
        User creator = User.create("creator@example.test", "creator", HASH);
        User player = User.create("player@example.test", "player", HASH);
        Quest quest = com.sidequest.quest.QuestFixtures.published(Quest.create(creator,
                "Boundary quest", "A valid description.", QuestCategory.LEARNING, QuestDifficulty.EASY, reward));
        Participation participation = Participation.join(player, quest);
        participation.complete(participation.getJoinedAt());
        assertThat(participation.getRewardXp()).isEqualTo(reward);
        assertThat(participation.getCompletedAt()).isEqualTo(participation.getJoinedAt());
    }
}
