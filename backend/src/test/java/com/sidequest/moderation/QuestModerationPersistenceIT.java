package com.sidequest.moderation;

import static org.assertj.core.api.Assertions.*;

import com.sidequest.identity.User;
import com.sidequest.identity.UserRepository;
import com.sidequest.quest.Quest;
import com.sidequest.quest.QuestRepository;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {"spring.config.import=", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuestModerationPersistenceIT {
    private static final String HASH =
            "{argon2}$argon2id$v=19$m=19456,t=2,p=1$c29tZXNhbHRzb21lc2FsdA$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaGhhc2hoYXM";

    @Autowired QuestModerationRepository moderation;
    @Autowired QuestRepository quests;
    @Autowired UserRepository users;
    @Autowired jakarta.persistence.EntityManager entityManager;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getProperty("sidequest.test.jdbc-url", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/sidequest_isolated_test")) {
            throw new IllegalStateException("Run scripts/test-postgres.sh to provision an isolated database");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "sidequest_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Test
    void reviewRowStartsUnreviewedAndNeverApprovesExistingQuestAutomatically() {
        var id = UUID.randomUUID().toString().replace("-", "");
        User creator = users.saveAndFlush(User.create(
                "moderator-test-" + id + "@example.test", "mod_" + id.substring(0, 18), HASH));
        Quest quest = quests.saveAndFlush(Quest.create(creator, "Learn to draw flowers",
                "Sketch flowers from a safe public viewpoint.", null, null, null));
        assertThat(moderation.findById(quest.getId())).isEmpty();

        moderation.saveAndFlush(QuestModeration.unreviewed(quest));
        entityManager.clear();

        var record = moderation.findById(quest.getId()).orElseThrow();
        assertThat(record.getState()).isEqualTo(ModerationState.UNREVIEWED);
        assertThat(record.getPolicyVersion()).isEqualTo("m3-v1");
        assertThat(record.getVersion()).isZero();
        assertThat(record.getSubmittedAt()).isNull();
        assertThat(record.getReviewedAt()).isNull();
    }
}
