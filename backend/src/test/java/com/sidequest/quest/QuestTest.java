package com.sidequest.quest;

import static org.assertj.core.api.Assertions.*;

import com.sidequest.identity.User;
import org.junit.jupiter.api.Test;

class QuestTest {
    private static final String HASH = "{argon2}$argon2id$test";

    @Test
    void createsDraftWithTrimmedTitleAndOptionalDetails() {
        Quest quest = Quest.create(
                creator(),
                "  Explore a new walking route  ",
                null,
                null,
                null,
                null);

        assertThat(quest.getTitle()).isEqualTo("Explore a new walking route");
        assertThat(quest.getDescription()).isNull();
        assertThat(quest.getCategory()).isNull();
        assertThat(quest.getDifficulty()).isNull();
        assertThat(quest.getRewardXp()).isNull();
        assertThat(quest.getStatus()).isEqualTo(QuestStatus.DRAFT);
        assertThat(quest.getPublishedAt()).isNull();
        assertThat(quest.getArchivedAt()).isNull();
    }

    @Test
    void rejectsInvalidTitleAndOptionalDraftFields() {
        assertThatIllegalArgumentException().isThrownBy(() -> Quest.create(
                creator(), "  ab  ", null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> Quest.create(
                creator(), "   ", null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> Quest.create(
                creator(), "a".repeat(121), null, null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> Quest.create(
                creator(), "A valid title", "too short", null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> Quest.create(
                creator(), "A valid title", "x".repeat(3001), null, null, null));
        assertThatIllegalArgumentException().isThrownBy(() -> Quest.create(
                creator(), "A valid title", null, null, null, 0));
        assertThatIllegalArgumentException().isThrownBy(() -> Quest.create(
                creator(), "A valid title", null, null, null, 1001));
    }

    @Test
    void acceptsContractLimitsAsUnicodeCodePoints() {
        String title = "🧭".repeat(120);
        String description = "🧭".repeat(3000);

        Quest quest = Quest.create(
                creator(),
                title,
                description,
                QuestCategory.SOCIAL,
                QuestDifficulty.HARD,
                1000);

        assertThat(quest.getTitle().codePointCount(0, quest.getTitle().length())).isEqualTo(120);
        assertThat(quest.getDescription().codePointCount(0, quest.getDescription().length())).isEqualTo(3000);
    }

    @Test
    void draftUpdatesRefreshOnlyEditableContent() {
        Quest quest = Quest.create(
                creator(),
                "Original quest title",
                null,
                null,
                null,
                null);

        quest.updateDraft(
                "  Revised quest title  ",
                "A description long enough to be saved.",
                QuestCategory.LEARNING,
                QuestDifficulty.MEDIUM,
                250);

        assertThat(quest.getTitle()).isEqualTo("Revised quest title");
        assertThat(quest.getDescription()).isEqualTo("A description long enough to be saved.");
        assertThat(quest.getCategory()).isEqualTo(QuestCategory.LEARNING);
        assertThat(quest.getDifficulty()).isEqualTo(QuestDifficulty.MEDIUM);
        assertThat(quest.getRewardXp()).isEqualTo(250);
        assertThat(quest.getStatus()).isEqualTo(QuestStatus.DRAFT);
    }

    private static User creator() {
        return User.create("creator@example.test", "quest_creator", HASH);
    }
}
