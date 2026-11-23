package com.sidequest.moderation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.sidequest.identity.User;
import com.sidequest.identity.UserRepository;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

@SpringBootTest(properties = "spring.config.import=")
class AccountAbuseQuotaIT {
    private static final String HASH =
            "{argon2}$argon2id$v=19$m=19456,t=2,p=1$c29tZXNhbHRzb21lc2FsdA$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXM";

    @Autowired UserRepository users;
    @Autowired JdbcTemplate jdbc;
    @Autowired AccountAbuseQuota quota;
    @Autowired PlatformTransactionManager transactions;

    @DynamicPropertySource
    static void isolatedDatabase(DynamicPropertyRegistry registry) {
        String url = System.getProperty("sidequest.test.jdbc-url", "");
        if (!url.matches("jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/sidequest_isolated_test")) {
            throw new IllegalStateException("Run backend/scripts/test-postgres.sh with isolated PostgreSQL");
        }
        registry.add("spring.datasource.url", () -> url);
        registry.add("spring.datasource.username", () -> "sidequest_test");
        registry.add("spring.datasource.password", () -> "");
    }

    @Test
    void questCreationBudgetBlocksFortyFirstWithinRollingDayWithoutAffectingOtherUsers() {
        UUID creator = account();
        UUID another = account();
        Instant now = Instant.now();
        for (int n = 0; n < 40; n++) {
            jdbc.update("""
                    INSERT INTO quests (id, creator_id, title, status, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'DRAFT', ?, ?, 0)
                    """, UUID.randomUUID(), creator, "Limited draft " + n, Timestamp.from(now), Timestamp.from(now));
        }
        var tx = new TransactionTemplate(transactions);
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> quota.requireQuestCreation(creator)))
                .isInstanceOf(AccountAbuseQuota.QuotaExceededException.class);
        org.assertj.core.api.Assertions.assertThatCode(() -> tx.executeWithoutResult(status -> quota.requireQuestCreation(another)))
                .doesNotThrowAnyException();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM quests WHERE creator_id = ?",
                Long.class, creator)).isEqualTo(40);
    }

    @Test
    void duplicateReportIsFreeOfQuotaEvenAfterFifteenReportsInRollingDay() {
        UUID reporter = account();
        UUID creator = account();
        Instant now = Instant.now();
        UUID duplicateQuest = null;
        for (int n = 0; n < 15; n++) {
            UUID questId = UUID.randomUUID();
            if (n == 0) duplicateQuest = questId;
            jdbc.update("""
                    INSERT INTO quests (id, creator_id, title, status, created_at, updated_at, version)
                    VALUES (?, ?, ?, 'DRAFT', ?, ?, 0)
                    """, questId, creator, "Reported draft " + n, Timestamp.from(now), Timestamp.from(now));
            jdbc.update("""
                    INSERT INTO quest_reports (id, quest_id, reporter_id, reason, created_at)
                    VALUES (?, ?, ?, 'OTHER', ?)
                    """, UUID.randomUUID(), questId, reporter, Timestamp.from(now));
        }
        UUID alreadyReported = duplicateQuest;
        var tx = new TransactionTemplate(transactions);
        tx.executeWithoutResult(status -> quota.requireNewReport(reporter, alreadyReported));
        UUID nextQuest = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO quests (id, creator_id, title, status, created_at, updated_at, version)
                VALUES (?, ?, 'New draft to report', 'DRAFT', ?, ?, 0)
                """, nextQuest, creator, Timestamp.from(now), Timestamp.from(now));
        assertThatThrownBy(() -> tx.executeWithoutResult(status -> quota.requireNewReport(reporter, nextQuest)))
                .isInstanceOf(AccountAbuseQuota.QuotaExceededException.class);
    }

    private UUID account() {
        String id = UUID.randomUUID().toString().replace("-", "");
        return users.saveAndFlush(User.create("quota-" + id + "@example.test",
                "quota_" + id.substring(0, 18), HASH)).getId();
    }
}
