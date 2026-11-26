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
import org.postgresql.util.PSQLException;
import org.springframework.jdbc.datasource.DataSourceUtils;
import jakarta.persistence.EntityManager;
import com.sidequest.quest.QuestFixtures;
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
    @Autowired EntityManager entityManager;

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

        UUID persistedId = participation.getId();
        entityManager.clear();
        participation = participations.findById(persistedId).orElseThrow();
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

        entityManager.flush();
        entityManager.clear();
        Participation reloaded = participations.findById(participation.getId()).orElseThrow();
        assertThat(reloaded.getStatus()).isEqualTo(ParticipationStatus.COMPLETED);
        assertThat(reloaded.getCompletedAt()).isEqualTo(participation.getCompletedAt());
        assertThat(ledger.findByParticipationId(participation.getId()).orElseThrow().getAwardedAt())
                .isEqualTo(reloaded.getCompletedAt());
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
                .satisfies(error -> assertViolation(error, "23505", "participations_unique_user_quest"));

        first.complete(first.getJoinedAt().plusSeconds(30));
        participations.saveAndFlush(first);
        XpLedger entry = ledger.save(XpLedger.award(first, first.getCompletedAt()));

        assertThatThrownBy(() -> insertLedger(entry.getParticipationId(), 175, entry.getAwardedAt()))
                .satisfies(error -> assertViolation(error, "23505", "xp_ledger_unique_participation"));
    }

    @Test
    void rejectsInvalidReferencesStatusesRewardsAndCompletionTimestamps() throws Exception {
        Fixture fixture = fixture();
        UUID otherUser = UUID.randomUUID();
        UUID otherQuest = UUID.randomUUID();
        Instant now = Instant.now();

        assertThatThrownBy(() -> insertParticipation(otherUser, fixture.quest.getId(), "IN_PROGRESS", 100, now, null))
                .satisfies(error -> assertViolation(error, "23503", "participations_user_fk"));
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), otherQuest, "IN_PROGRESS", 100, now, null))
                .satisfies(error -> assertViolation(error, "23503", "participations_quest_fk"));
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), fixture.quest.getId(), "UNKNOWN", 100, now, null))
                .satisfies(error -> assertViolation(error, "23514", "participations_completed_state_valid"));
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), fixture.quest.getId(), "IN_PROGRESS", 0, now, null))
                .satisfies(error -> assertViolation(error, "23514", "participations_reward_valid"));
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), fixture.quest.getId(), "COMPLETED", 100, now, now.minusSeconds(1)))
                .satisfies(error -> assertViolation(error, "23514", "participations_completed_state_valid"));
        assertThatThrownBy(() -> insertParticipation(fixture.user.getId(), fixture.quest.getId(), "COMPLETED", 100, now, null))
                .satisfies(error -> assertViolation(error, "23514", "participations_completed_state_valid"));

        Participation participation = participations.saveAndFlush(Participation.join(fixture.user, fixture.quest));
        assertThatThrownBy(() -> insertLedger(participation.getId(), 0, now))
                .satisfies(error -> assertViolation(error, "23514", "xp_ledger_amount_valid"));
        assertThatThrownBy(() -> insertLedger(participation.getId(), 1001, now))
                .satisfies(error -> assertViolation(error, "23514", "xp_ledger_amount_valid"));
    }

    @Test
    void restrictsUserQuestAndParticipationDeletionAndLedgerMutation() throws Exception {
        Fixture fixture = fixture();
        Participation participation = participations.saveAndFlush(Participation.join(fixture.user, fixture.quest));
        participation.complete(participation.getJoinedAt().plusSeconds(30));
        participations.saveAndFlush(participation);
        XpLedger entry = ledger.save(XpLedger.award(participation, participation.getCompletedAt()));

        assertThatThrownBy(() -> deleteUser(fixture.user.getId())).satisfies(error -> assertViolation(error, "23001", "participations_user_fk"));
        assertThatThrownBy(() -> deleteQuest(fixture.quest.getId())).satisfies(error -> assertViolation(error, "23001", "participations_quest_fk"));
        assertThatThrownBy(() -> deleteParticipation(participation.getId())).satisfies(error -> assertViolation(error, "23001", "xp_ledger_participation_fk"));
        assertThatThrownBy(() -> updateLedger(entry.getId(), 999)).satisfies(error -> assertViolation(error, "23514", "xp_ledger_append_only"));
        assertThatThrownBy(() -> deleteLedger(entry.getId())).satisfies(error -> assertViolation(error, "23514", "xp_ledger_append_only"));

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

        String migrationsBefore = migrationHistory();
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        assertThat(migrationHistory()).isEqualTo(migrationsBefore);
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT count(*) FROM databasechangelog")) {
            rows.next();
            assertThat(rows.getInt(1)).isEqualTo(14);
        }
    }

    @Test
    void filtersOwnersAndStatuses() {
        Fixture f = fixture();
        Participation active = participations.saveAndFlush(Participation.join(f.user, f.quest));
        Fixture other = fixture();
        Participation completed = Participation.join(f.user, other.quest);
        completed.complete(completed.getJoinedAt());
        participations.saveAndFlush(completed);
        entityManager.clear();
        assertThat(participations.findByIdAndUser_Id(active.getId(), other.user.getId())).isEmpty();
        assertThat(participations.findByIdAndUserForUpdate(active.getId(), other.user.getId())).isEmpty();
        assertThat(quests.findByIdAndCreatorForUpdate(f.quest.getId(), f.user.getId())).isEmpty();
        assertThat(participations.findAllByUser_Id(other.user.getId(), PageRequest.of(0, 20))).isEmpty();
        assertThat(participations.findAllByUser_IdAndStatus(f.user.getId(), ParticipationStatus.IN_PROGRESS,
                PageRequest.of(0, 20))).extracting(Participation::getId).containsExactly(active.getId());
        assertThat(participations.findAllByUser_IdAndStatus(f.user.getId(), ParticipationStatus.COMPLETED,
                PageRequest.of(0, 20))).extracting(Participation::getId).containsExactly(completed.getId());
    }

    @Test
    void lockQueriesAcquireDatabaseRowLocks() throws Exception {
        Fixture f = fixture();
        Participation p = participations.saveAndFlush(Participation.join(f.user, f.quest));
        TestTransaction.flagForCommit();
        TestTransaction.end();
        TestTransaction.start();
        assertThat(participations.findByIdAndUserForUpdate(p.getId(), f.user.getId())).isPresent();
        assertRowLocked("participations", p.getId());
        TestTransaction.end();
        TestTransaction.start();
        assertThat(participations.findByUserAndQuestForUpdate(f.user.getId(), f.quest.getId())).isPresent();
        assertRowLocked("participations", p.getId());
        TestTransaction.end();
        TestTransaction.start();
        assertThat(quests.findByIdForUpdate(f.quest.getId())).isPresent();
        assertRowLocked("quests", f.quest.getId());
        TestTransaction.end();
        TestTransaction.start();
        assertThat(quests.findByIdAndCreatorForUpdate(f.quest.getId(), f.quest.getCreatorId())).isPresent();
        assertRowLocked("quests", f.quest.getId());
    }

    private void assertRowLocked(String table, UUID id) throws Exception {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.prepareStatement("SELECT id FROM " + table + " WHERE id = ? FOR UPDATE NOWAIT")) {
            statement.setObject(1, id);
            assertThatThrownBy(statement::executeQuery).isInstanceOf(PSQLException.class)
                    .satisfies(error -> assertThat(((SQLException) error).getSQLState()).isEqualTo("55P03"));
        }
    }

    @Test
    void rejectsSnapshotMutationsAndCompletionReversal() throws Exception {
        Fixture f = fixture();
        Participation p = participations.saveAndFlush(Participation.join(f.user, f.quest));
        for (String assignment : new String[] {"id = '" + UUID.randomUUID() + "'",
                "user_id = '" + f.quest.getCreatorId() + "'", "quest_id = '" + UUID.randomUUID() + "'",
                "reward_xp = 176", "joined_at = joined_at - interval '1 second'"}) {
            assertThatThrownBy(() -> execute("UPDATE participations SET " + assignment + " WHERE id = ?", p.getId()))
                    .satisfies(error -> assertViolation(error, "23514", "participations_immutable_snapshot"));
        }
        p.complete(p.getJoinedAt().plusSeconds(1));
        participations.saveAndFlush(p);
        assertThatThrownBy(() -> execute("UPDATE participations SET status = 'IN_PROGRESS', completed_at = NULL WHERE id = ?", p.getId()))
                .satisfies(error -> assertViolation(error, "23514", "participations_completion_immutable"));
        assertThatThrownBy(() -> execute("UPDATE participations SET completed_at = completed_at + interval '1 second' WHERE id = ?", p.getId()))
                .satisfies(error -> assertViolation(error, "23514", "participations_completion_immutable"));
        execute("UPDATE participations SET completed_at = completed_at WHERE id = ?", p.getId());
    }

    @Test
    void enforcesRewardBoundariesAndTimestampConsistencyInSql() throws Exception {
        Fixture f = fixture();
        Instant now = Instant.now();
        assertThatThrownBy(() -> insertParticipation(f.user.getId(), f.quest.getId(), "IN_PROGRESS", 1001, now, null))
                .satisfies(error -> assertViolation(error, "23514", "participations_reward_valid"));
        assertThatThrownBy(() -> insertParticipation(f.user.getId(), f.quest.getId(), "IN_PROGRESS", 1, now, now))
                .satisfies(error -> assertViolation(error, "23514", "participations_completed_state_valid"));
        assertThatThrownBy(() -> insertLedger(UUID.randomUUID(), 1, now))
                .satisfies(error -> assertViolation(error, "23503", "xp_ledger_participation_fk"));
        for (int reward : new int[] {1, 1000}) {
            Fixture boundary = fixture();
            insertParticipation(boundary.user.getId(), boundary.quest.getId(), "COMPLETED", reward, now, now);
            entityManager.clear();
            Participation p = participations.findByUser_IdAndQuest_Id(boundary.user.getId(), boundary.quest.getId()).orElseThrow();
            assertThat(p.getRewardXp()).isEqualTo(reward);
            assertThat(p.getCompletedAt()).isEqualTo(p.getJoinedAt());
            insertLedger(p.getId(), reward, p.getCompletedAt());
            assertThat(ledger.findByParticipationId(p.getId()).orElseThrow().getAmount()).isEqualTo(reward);
        }
    }

    private String migrationHistory() throws SQLException {
        try (Connection connection = dataSource.getConnection();
             var statement = connection.createStatement();
             var rows = statement.executeQuery("SELECT string_agg(id || ':' || md5sum || ':' || dateexecuted::text, ',' ORDER BY orderexecuted) FROM databasechangelog")) {
            rows.next();
            return rows.getString(1);
        }
    }

    private Fixture fixture() {
        User user = users.saveAndFlush(User.create("participant-" + UUID.randomUUID() + "@example.test", "participant_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18), HASH));
        User creator = users.saveAndFlush(User.create("creator-" + UUID.randomUUID() + "@example.test", "creator_" + UUID.randomUUID().toString().replace("-", "").substring(0, 18), HASH));
        Quest quest = quests.saveAndFlush(Quest.create(creator,
                "A participation quest " + UUID.randomUUID(),
                "A valid description for a participation quest.",
                QuestCategory.LEARNING, QuestDifficulty.MEDIUM, 175));
        QuestFixtures.published(quest);
        quests.flush();
        return new Fixture(user, quest);
    }

    private void insertParticipation(UUID userId, UUID questId, String status,
                                     int reward, Instant joinedAt, Instant completedAt) throws Exception {
        execute("INSERT INTO participations (id,user_id,quest_id,status,reward_xp,joined_at,completed_at) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), userId, questId, status, reward, Timestamp.from(joinedAt),
                completedAt == null ? null : Timestamp.from(completedAt));
    }

    private void insertLedger(UUID participationId, int amount, Instant awardedAt) throws Exception {
        execute("INSERT INTO xp_ledger (id,participation_id,amount,awarded_at) VALUES (?,?,?,?)",
                UUID.randomUUID(), participationId, amount, Timestamp.from(awardedAt));
    }

    private void deleteUser(UUID id) throws Exception { deleteById("users", id); }
    private void deleteQuest(UUID id) throws Exception { deleteById("quests", id); }
    private void deleteParticipation(UUID id) throws Exception { deleteById("participations", id); }
    private void deleteLedger(UUID id) throws Exception { deleteById("xp_ledger", id); }
    private void deleteById(String table, UUID id) throws Exception {
        execute("DELETE FROM " + table + " WHERE id = ?", id);
    }
    private void updateLedger(UUID id, int amount) throws Exception {
        execute("UPDATE xp_ledger SET amount = ? WHERE id = ?", amount, id);
    }

    private void execute(String sql, Object... values) throws SQLException {
        Connection connection = DataSourceUtils.getConnection(dataSource);
        var savepoint = connection.setSavepoint();
        try (var statement = connection.prepareStatement(sql)) {
            for (int i = 0; i < values.length; i++) statement.setObject(i + 1, values[i]);
            statement.executeUpdate();
        } catch (SQLException error) {
            connection.rollback(savepoint);
            throw error;
        } finally {
            connection.releaseSavepoint(savepoint);
            DataSourceUtils.releaseConnection(connection, dataSource);
        }
    }

    private static void assertViolation(Throwable error, String state, String constraint) {
        assertThat(error).isInstanceOf(PSQLException.class);
        PSQLException postgres = (PSQLException) error;
        assertThat(postgres.getSQLState()).isEqualTo(state);
        assertThat(postgres.getServerErrorMessage()).isNotNull();
        assertThat(postgres.getServerErrorMessage().getConstraint()).isEqualTo(constraint);
    }

    private record Fixture(User user, Quest quest) { }
}
