package com.sidequest.identity;

import static org.assertj.core.api.Assertions.*;

import java.sql.Connection;
import java.sql.DriverManager;
import java.util.UUID;
import javax.sql.DataSource;
import liquibase.integration.spring.SpringLiquibase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@DataJpaTest(properties = {"spring.config.import=", "spring.jpa.hibernate.ddl-auto=validate"})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class UserPersistenceIT {
    // Synthetic encoded-format fixture; credential verification belongs to the security tests.
    private static final String HASH = "{argon2}$argon2id$v=19$m=19456,t=2,p=1$c29tZXNhbHRzb21lc2FsdA$aGFzaGhhc2hoYXNoaGFzaGhhc2hoYXNoaGFzaGhhc2hoYXM";
    @Autowired UserRepository users;
    @Autowired TestEntityManager entityManager;
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
    void createsRetrievesAndUpdatesAccountWithStableCreationTime() {
        User user = users.saveAndFlush(User.create("  Maker@Example.test ", "Quest_Maker", HASH));
        var id = user.getId();
        entityManager.clear();
        User stored = users.findByEmail("maker@example.test").orElseThrow();
        assertThat(stored.getId()).isEqualTo(id);
        assertThat(stored.getUsername()).isEqualTo("quest_maker");
        assertThat(stored.getStatus()).isEqualTo(AccountStatus.ACTIVE);
        var created = stored.getCreatedAt();
        assertThat(stored.getUpdatedAt()).isEqualTo(created);
        stored.disable();
        users.flush();
        entityManager.clear();
        stored = users.findByUsername("quest_maker").orElseThrow();
        assertThat(stored.getCreatedAt()).isEqualTo(created);
        assertThat(stored.getUpdatedAt()).isAfterOrEqualTo(created);
        assertThat(stored.getStatus()).isEqualTo(AccountStatus.DISABLED);
        assertThat(stored.getPasswordHash()).isEqualTo(HASH);
    }

    @Test
    void databaseRejectsInvalidAndDuplicateAccounts() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            connection.setAutoCommit(true);
            String email = UUID.randomUUID() + "@example.test";
            insert(connection, email, "maker_1", HASH, "ACTIVE");
            assertThatThrownBy(() -> insert(connection, email, "maker_2", HASH, "ACTIVE")).hasMessageContaining("users_email_key");
            assertThatThrownBy(() -> insert(connection, "other@example.test", "maker_1", HASH, "ACTIVE")).hasMessageContaining("users_username_key");
            for (String username : new String[]{"ab", "UPPER", "has space", "ééé", "a".repeat(31)}) {
                assertThatThrownBy(() -> insert(connection, "other@example.test", username, HASH, "ACTIVE"));
            }
            assertThatThrownBy(() -> insert(connection, "UPPER@example.test", "valid_name", HASH, "ACTIVE"));
            assertThatThrownBy(() -> insert(connection, "other@example.test", "valid_name", "plaintext", "ACTIVE"));
            assertThatThrownBy(() -> insert(connection, "other@example.test", "valid_name", HASH, "UNKNOWN"));
            assertThatThrownBy(() -> insert(connection, null, "valid_name", HASH, "ACTIVE"));
        }
    }

    private void insert(Connection connection, String email, String username, String hash, String status) throws Exception {
        try (var statement = connection.prepareStatement("INSERT INTO users VALUES (?, ?, ?, ?, ?, now(), now())")) {
            statement.setObject(1, UUID.randomUUID()); statement.setString(2, email);
            statement.setString(3, username); statement.setString(4, hash); statement.setString(5, status);
            statement.executeUpdate();
        }
    }

    @Test
    void migrationsRepeatWithoutReapplyingAndExpiredSessionsCascade() throws Exception {
        SpringLiquibase liquibase = new SpringLiquibase();
        liquibase.setDataSource(dataSource);
        liquibase.setChangeLog("classpath:db/changelog/db.changelog-master.yaml");
        liquibase.afterPropertiesSet();
        try (var connection = dataSource.getConnection(); var sql = connection.createStatement()) {
            var rows = sql.executeQuery("SELECT count(*) FROM databasechangelog");
            rows.next(); assertThat(rows.getInt(1)).isEqualTo(6);
            String id = UUID.randomUUID().toString();
            sql.executeUpdate("INSERT INTO spring_session VALUES ('" + id + "','" + id + "',0,0,1800,1,'test-user')");
            sql.executeUpdate("INSERT INTO spring_session_attributes VALUES ('" + id + "','test',decode('00','hex'))");
            assertThat(sql.executeUpdate("DELETE FROM spring_session WHERE expiry_time < 2")).isEqualTo(1);
            rows = sql.executeQuery("SELECT count(*) FROM spring_session_attributes WHERE session_primary_id='" + id + "'");
            rows.next(); assertThat(rows.getInt(1)).isZero();
        }
    }
}
