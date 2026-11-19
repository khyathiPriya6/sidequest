package com.sidequest.participation;

import static org.assertj.core.api.Assertions.*;

import com.sidequest.identity.User;
import com.sidequest.identity.UserRepository;
import com.sidequest.quest.Quest;
import com.sidequest.quest.QuestCategory;
import com.sidequest.quest.QuestDifficulty;
import com.sidequest.quest.QuestRepository;
import com.sidequest.reward.XpLedger;
import com.sidequest.reward.XpLedgerRepository;
import com.sidequest.reward.XpLedgerRepositoryImpl;
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
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.transaction.TestTransaction;

@DataJpaTest(properties = {"spring.config.import=", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(XpLedgerRepositoryImpl.class)
class ParticipationPersistenceIT {
    private static final String HASH = "{argon2}$argon2id$v=19$m=19456,t=2,p=1$c29tZXNhbHRzb21lc2FsdA$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaGhhc2hoYXM";

    @Autowired ParticipationRepository participations;
    @Autowired XpLedgerRepository ledger;
    @Autowired UserRepository users;
    @Autowired QuestRepository quests;
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
    void persistsValidParticipationAndOwnerQueriesAndRewardLedger() {
        Fixture fixture = fixture();
        Participation participation = participations.saveAndFlush(Participation.join(fixture.user, fixture.quest));

        assertThat(participation.getId()).isNotNull();
        assertThat(participation.getUserId()).isEqualTo(fixture.user.getId());
        assertThat(participation.getQuestId()).isEqualTo(fixture.quest.getId());
        assertThat(participation.getStatus()).isEqualTo(ParticipationStatus.IN_PROGRESS);
        assertThat(participation.getRewardXp()).isEqualTo(175);
        assertThat(participation.getJoinedAt()).isNotNull();
        assertThat(participation.getCompletedAt()).isNull();

        assertThat(participations.findByUser_IdAndQuest_Id(fixture.user.getId(), fixture.quest.getId()))
                .isPresent();
        assertThat(participations.findByIdAndUser_Id(participation.getId(), fixture.user.getId()))
                .isPresent();
        assertThat(participations.findAllByUser_Id(fixture.user.getId(), PageRequest.of(0, 20)).getContent())
                .extracting(Participation::getId).contains(participation.getId());

        participation.complete(participation.getJoinedAt().plusSeconds(30));
        participations.saveAndFlush(participation);
        XpLedger entry = XpLedger.award(participation, participation.getCompletedAt());
        ledger.save(entry);

        assertThat(ledger.findByParticipationId(participation.getId())).isPresent();
        assertThat(ledger.existsByParticipationId(participation.getId())).isTrue();
        assertThat(ledger.findByParticipationId(participation.getId()).orElseThrow().getAmount()).isEqualTo(175);
    }

    @Test
    void rejectsDuplicateEnrollmentAndDuplicateLedgerEntry() throws Exception {
        Fixture fixture = fixture();
        Participation first = participations.saveAndFlush(Participation.join(fixture.user, fixture.quest));
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), fixture.quest.getId(),
                "IN_PROGRESS", 175, first.getJoinedAt(), null))
                .isInstanceOf(SQLException.class);

        first.complete(first.getJoinedAt().plusSeconds(30));
        participations.saveAndFlush(first);
        XpLedger entry = ledger.save(XpLedger.award(first, first.getCompletedAt()));

        assertThatThrownBy(() -> insertLedger(entry.getParticipationId(), 175, entry.getAwardedAt()))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void rejectsInvalidReferencesStatusesRewardsAndCompletionTimestamps() throws Exception {
        Fixture fixture = fixture();
        UUID otherUser = UUID.randomUUID();
        UUID otherQuest = UUID.randomUUID();
        Instant now = Instant.now();

        assertThatThrownBy(() -> insertParticipation(otherUser, fixture.quest.getId(), "IN_PROGRESS", 100, now, null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), otherQuest, "IN_PROGRESS", 100, now, null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), fixture.quest.getId(), "UNKNOWN", 100, now, null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), fixture.quest.getId(), "IN_PROGRESS", 0, now, null))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), fixture.quest.getId(), "COMPLETED", 100, now, now.minusSeconds(1)))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), fixture.quest.getId(), "COMPLETED", 100, now, null))
                .isInstanceOf(SQLException.class);

        Participation participation = participations.saveAndFlush(Participation.join(fixture.user, fixture.quest));
        assertThatThrownBy(() -> insertLedger(participation.getId(), 0, now))
                .isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> insertLedger(participation.getId(), 1001, now))
                .isInstanceOf(SQLException.class);
    }

    @Test
    void restrictsUserQuestAndParticipationDeletionAndLedgerMutation() throws Exception {
        Fixture fixture = fixture();
        Participation participation = participations.saveAndFlush(Participation.join(fixture.user, fixture.quest));
        participation.complete(participation.getJoinedAt().plusSeconds(30));
        participations.saveAndFlush(participation);
        XpLedger entry = ledger.save(XpLedger.award(participation, participation.getCompletedAt()));

        TestTransaction.flagForCommit();
        TestTransaction.end();
        assertThatThrownBy(() -> deleteUser(fixture.user.getId())).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> deleteQuest(fixture.quest.getId())).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> deleteParticipation(participation.getId())).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> updateLedger(entry.getId(), 999)).isInstanceOf(SQLException.class);
        assertThatThrownBy(() -> deleteLedger(entry.getId())).isInstanceOf(SQLException.class);

        TestTransaction.start();
        assertThat(participations.findById(participation.getId())).isPresent();
        assertThat(ledger.findByParticipationId(participation.getId())).isPresent();
    }

    @Test
    void rollsBackATransactionAndRepeatsMigrationsWithoutChanges() throws Exception {
        Fixture fixture = fixture();
        UUID[] id = new UUID[1];
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(false);
            try (var statement = connection.prepareStatement(
                    "INSERT INTO participations (id,user_id,quest_id,status,reward_xp,joined_at,completed_at) VALUES (?,?,?,?,?,?,?)")) {
                id[0] = UUID.randomUUID();
                statement.setObject(1, id[0]);
                statement.setObject(2, fixture.user.getId());
                statement.setObject(3, fixture.quest.getId());
                statement.setString(4, "IN_PROGRESS");
                statement.setInt(5, 175);
                statement.setTimestamp(6, java.sql.Timestamp.from(Instant.now()));
                statement.setTimestamp(7, null);
                statement.executeUpdate();
            }
            connection.rollback();
            try (var statement = connection.prepareStatement("SELECT count(*) FROM participations WHERE id = ?")) {
                statement.setObject(1, id[0]);
                try (var rows = statement.executeQuery()) {
                    rows.next();
                    assertThat(rows.getInt(1)).isZero();
                }
            }
        }

        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM databasechangelog")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(6);
        }
    }

    private Fixture fixture() {
        User user = users.saveAndFlush(User.create("participant-" + UUID.randomUUID() + "@example.test", "participant_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18), HASH));
        User creator = users.saveAndFlush(User.create("creator-" + UUID.randomUUID() + "@example.test", "creator_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18), HASH));
        Quest quest = quests.saveAndFlush(Quest.create(creator,
                "A participation quest " + UUID.randomUUID(),
                "A valid description for a participation quest.",
                QuestCategory.LEARNING, QuestDifficulty.MEDIUM, 175));
        return new Fixture(user, quest);
    }

    private void insertParticipation(UUID userId, UUID questId, String status,
                                     int reward, Instant joinedAt, Instant completedAt) throws Exception {
        String sql = "INSERT INTO participations (id,user_id,quest_id,status,reward_xp,joined_at,completed_at) VALUES (?,?,?,?,?,?,?)";
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, questId);
            statement.setString(4, status);
            statement.setInt(5, reward);
            statement.setTimestamp(6, Timestamp.from(joinedAt));
            statement.setTimestamp(7, completedAt == null ? null : Timestamp.from(completedAt));
            statement.executeUpdate();
        }
    }

    private void insertLedger(UUID participationId, int amount, Instant awardedAt) throws Exception {
        String sql = "INSERT INTO xp_ledger (id,participation_id,amount,awarded_at) VALUES (?,?,?,?)";
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, participationId);
            statement.setInt(3, amount);
            statement.setTimestamp(4, Timestamp.from(awardedAt));
            statement.executeUpdate();
        }
    }

    private void deleteUser(UUID id) throws Exception { deleteById("users", id); }
    private void deleteQuest(UUID id) throws Exception { deleteById("quests", id); }
    private void deleteParticipation(UUID id) throws Exception { deleteById("participations", id); }

    private void deleteById(String table, UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("DELETE FROM " + table + " WHERE id = ?")) {
            statement.setObject(1, id);
            statement.executeUpdate();
        }
    }

    private void updateLedger(UUID id, int amount) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("UPDATE xp_ledger SET amount = ? WHERE id = ?")) {
            statement.setInt(1, amount);
            statement.setObject(2, id);
            statement.executeUpdate();
        }
    }

    private void deleteLedger(UUID id) throws Exception { deleteById("xp_ledger", id); }

    private void insertParticipation(UUID userId, UUID questId, String status,
                                     int reward, Instant joinedAt, Instant completedAt) throws Exception {
        String sql = "INSERT INTO participations (id,user_id,quest_id,status,reward_xp,joined_at,completed_at) VALUES (?,?,?,?,?,?,?)";
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, userId);
            statement.setObject(3, questId);
            statement.setString(4, status);
            statement.setInt(5, reward);
            statement.setTimestamp(6, java.sql.Timestamp.from(joinedAt));
            statement.setTimestamp(7, completedAt == null ? null : java.sql.Timestamp.from(completedAt));
            statement.executeUpdate();
        }
    }

    private void insertLedger(UUID participationId, int amount, Instant awardedAt) throws Exception {
        String sql = "INSERT INTO xp_ledger (id,participation_id,amount,awarded_at) VALUES (?,?,?,?)";
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement(sql)) {
            statement.setObject(1, UUID.randomUUID());
            statement.setObject(2, participationId);
            statement.setInt(3, amount);
            statement.setTimestamp(4, java.sql.Timestamp.from(awardedAt));
            statement.executeUpdate();
        }
    }

    private record Fixture(User user, Quest quest) { }
}
