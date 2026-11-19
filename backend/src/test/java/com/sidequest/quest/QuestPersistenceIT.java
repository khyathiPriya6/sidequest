package com.sidequest.quest;

import static org.assertj.core.api.Assertions.*;

import com.sidequest.identity.User;
import com.sidequest.identity.UserRepository;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;

@DataJpaTest(properties = {"spring.config.import=", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class QuestPersistenceIT {
    private static final String HASH =
            "{argon2}$argon2id$v=19$m=19456,t=2,p=1$c29tZXNhbHRzb21lc2FsdA$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaGhhc2hoYXM";

    @Autowired QuestRepository quests;
    @Autowired UserRepository users;
    @Autowired jakarta.persistence.EntityManager entityManager;
    @Autowired DataSource dataSource;

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
    void savesLoadsAndUpdatesQuestWithUtcTimestampsAndVersion() {
        User creator = users.saveAndFlush(newCreator());
        Quest quest = quests.saveAndFlush(Quest.create(
                creator,
                "  Explore a new walking route  ",
                null,
                QuestCategory.ADVENTURE,
                QuestDifficulty.EASY,
                100));
        UUID id = quest.getId();
        Instant createdAt = quest.getCreatedAt();

        assertThat(id).isNotNull();
        assertThat(quest.getCreatorId()).isEqualTo(creator.getId());
        assertThat(createdAt).isNotNull();
        assertThat(quest.getUpdatedAt()).isEqualTo(createdAt);
        assertThat(quest.getVersion()).isZero();
        assertThat(quest.getStatus()).isEqualTo(QuestStatus.DRAFT);

        entityManager.clear();
        Quest loaded = quests.findById(id).orElseThrow();
        assertThat(loaded.getTitle()).isEqualTo("Explore a new walking route");
        assertThat(loaded.getCategory()).isEqualTo(QuestCategory.ADVENTURE);
        assertThat(loaded.getDifficulty()).isEqualTo(QuestDifficulty.EASY);
        assertThat(loaded.getRewardXp()).isEqualTo(100);

        loaded.updateDraft(
                "Revised walking route",
                "A description long enough to be saved.",
                QuestCategory.FITNESS,
                QuestDifficulty.MEDIUM,
                250);
        quests.flush();
        entityManager.clear();

        Quest updated = quests.findById(id).orElseThrow();
        assertThat(updated.getCreatedAt()).isEqualTo(createdAt);
        assertThat(updated.getUpdatedAt()).isAfterOrEqualTo(createdAt);
        assertThat(updated.getVersion()).isEqualTo(1);
        assertThat(updated.getTitle()).isEqualTo("Revised walking route");
    }

    @Test
    void rejectsAWriteFromAnOlderOptimisticVersion() throws Exception {
        User creator = users.saveAndFlush(newCreator());
        Quest quest = quests.saveAndFlush(Quest.create(
                creator,
                "Quest before concurrent update",
                null,
                null,
                null,
                null));
        UUID id = quest.getId();
        entityManager.clear();
        Quest stale = quests.findById(id).orElseThrow();

        TestTransaction.flagForCommit();
        TestTransaction.end();
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(
                        "UPDATE quests SET title = ?, updated_at = ?, version = version + 1 WHERE id = ?")) {
            statement.setString(1, "Quest after concurrent update");
            statement.setTimestamp(2, Timestamp.from(Instant.now()));
            statement.setObject(3, id);
            assertThat(statement.executeUpdate()).isEqualTo(1);
        }

        TestTransaction.start();
        stale.updateDraft("Stale quest edit", null, null, null, null);
        assertThatThrownBy(() -> quests.saveAndFlush(stale))
                .isInstanceOf(OptimisticLockingFailureException.class);
    }

    @Test
    void databaseEnforcesCreatorAndQuestFieldConstraints() throws Exception {
        UUID creatorId = insertCreator();
        Instant publishedAt = Instant.now();
        assertThat(databaseEncoding()).isEqualTo("UTF8");
        assertThat(databaseCharacterLength("🧭".repeat(120))).isEqualTo(120);

        assertThatThrownBy(() -> insertQuest(
                UUID.randomUUID(), "Valid quest title", null, null, null, null, "DRAFT", null, null, 0))
                .isInstanceOf(SQLException.class);
        assertRejected(creatorId, "ab", null, null, null, null, "DRAFT", null, null, 0);
        assertRejected(creatorId, "   ", null, null, null, null, "DRAFT", null, null, 0);
        assertRejected(creatorId, "t".repeat(121), null, null, null, null, "DRAFT", null, null, 0);
        assertRejected(creatorId, "Valid quest title", "too short", null, null, null, "DRAFT", null, null, 0);
        assertRejected(creatorId, "Valid quest title", "x".repeat(3001), null, null, null, "DRAFT", null, null, 0);
        assertRejected(creatorId, "Valid quest title", null, "UNKNOWN", null, null, "DRAFT", null, null, 0);
        assertRejected(creatorId, "Valid quest title", null, null, "UNKNOWN", null, "DRAFT", null, null, 0);
        assertRejected(creatorId, "Valid quest title", null, null, null, 0, "DRAFT", null, null, 0);
        assertRejected(creatorId, "Valid quest title", null, null, null, 1001, "DRAFT", null, null, 0);
        assertRejected(creatorId, "Valid quest title", null, null, null, null, "UNKNOWN", null, null, 0);
        assertRejected(creatorId, "Valid quest title", null, null, null, null, "PUBLISHED", null, null, 0);
        assertRejected(creatorId, "Valid quest title", null, null, null, null, "DRAFT", null, null, -1);

        assertThatCode(() -> insertQuest(
                creatorId,
                "A valid published quest",
                "A description long enough to publish.",
                "LEARNING",
                "MEDIUM",
                250,
                "PUBLISHED",
                publishedAt,
                null,
                0)).doesNotThrowAnyException();
        assertThatCode(() -> insertQuest(
                creatorId,
                "🧭".repeat(120),
                "🧭".repeat(3000),
                "SOCIAL",
                "HARD",
                1000,
                "DRAFT",
                null,
                null,
                0)).doesNotThrowAnyException();
        assertThatCode(() -> insertQuest(
                creatorId,
                "🧭".repeat(3),
                "🧭".repeat(10),
                "FOOD",
                "EASY",
                1,
                "DRAFT",
                null,
                null,
                0)).doesNotThrowAnyException();
    }

    @Test
    void creatorCannotBeDeletedWhileQuestHistoryReferencesTheAccount() throws Exception {
        UUID creatorId = insertCreator();
        insertQuest(creatorId, "Quest with a persistent owner", null, null, null, null, "DRAFT", null, null, 0);

        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement("DELETE FROM users WHERE id = ?")) {
            statement.setObject(1, creatorId);
            assertThatThrownBy(statement::executeUpdate).isInstanceOf(SQLException.class);
            try (var query = connection.prepareStatement("SELECT count(*) FROM users WHERE id = ?")) {
                query.setObject(1, creatorId);
                try (var rows = query.executeQuery()) {
                    rows.next();
                    assertThat(rows.getInt(1)).isEqualTo(1);
                }
            }
        }
    }

    @Test
    void questMigrationIsRepeatable() throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();

        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM databasechangelog")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(7);
        }
    }

    private void assertRejected(
            UUID creatorId,
            String title,
            String description,
            String category,
            String difficulty,
            Integer rewardXp,
            String status,
            Instant publishedAt,
            Instant archivedAt,
            long version) throws Exception {
        assertThatThrownBy(() -> insertQuest(
                creatorId, title, description, category, difficulty, rewardXp,
                status, publishedAt, archivedAt, version)).isInstanceOf(SQLException.class);
    }

    private UUID insertCreator() throws Exception {
        UUID id = UUID.randomUUID();
        String identity = id.toString().replace("-", "").substring(0, 20);
        String sql = """
                INSERT INTO users (id, email, username, password_hash, status, created_at, updated_at)
                VALUES (?, ?, ?, ?, 'ACTIVE', ?, ?)
                """;
        Instant now = Instant.now();
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, id);
            statement.setString(2, "creator-" + id + "@example.test");
            statement.setString(3, "creator_" + identity);
            statement.setString(4, HASH);
            statement.setTimestamp(5, Timestamp.from(now));
            statement.setTimestamp(6, Timestamp.from(now));
            statement.executeUpdate();
        }
        return id;
    }

    private int databaseCharacterLength(String value) throws Exception {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement("SELECT char_length(?)")) {
            statement.setString(1, value);
            try (var rows = statement.executeQuery()) {
                rows.next();
                return rows.getInt(1);
            }
        }
    }

    private String databaseEncoding() throws Exception {
        try (Connection connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT current_setting('server_encoding')")) {
            rows.next();
            return rows.getString(1);
        }
    }

    private void insertQuest(
            UUID creatorId,
            String title,
            String description,
            String category,
            String difficulty,
            Integer rewardXp,
            String status,
            Instant publishedAt,
            Instant archivedAt,
            long version) throws Exception {
        String sql = """
                INSERT INTO quests (
                    id, creator_id, title, description, category, difficulty, reward_xp,
                    status, created_at, updated_at, published_at, archived_at, version
                ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                """;
        Instant now = Instant.now().minusSeconds(1);
        try (Connection connection = dataSource.getConnection();
                var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, creatorId);
            statement.setString(3, title);
            statement.setString(4, description);
            statement.setString(5, category);
            statement.setString(6, difficulty);
            statement.setObject(7, rewardXp);
            statement.setString(8, status);
            statement.setTimestamp(9, Timestamp.from(now));
            statement.setTimestamp(10, Timestamp.from(now));
            statement.setTimestamp(11, timestamp(publishedAt));
            statement.setTimestamp(12, timestamp(archivedAt));
            statement.setLong(13, version);
            statement.executeUpdate();
        }
    }

    private static Timestamp timestamp(Instant value) {
        return value == null ? null : Timestamp.from(value);
    }

    private static User newCreator() {
        String identity = UUID.randomUUID().toString();
        return User.create(
                "creator-" + identity + "@example.test",
                "creator_" + identity.replace("-", "").substring(0, 20),
                HASH);
    }
}
