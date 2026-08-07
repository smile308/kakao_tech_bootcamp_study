package kr.adapterz.springdatajpa.config;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.assertj.core.api.Assertions.assertThat;

class FlywayBaselineMigrationTest {

    @Test
    void emptyDatabaseIsCreatedFromCurrentBaseline() throws Exception {
        String jdbcUrl = "jdbc:h2:mem:flyway-baseline;MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1";
        Flyway flyway = Flyway.configure()
                .dataSource(jdbcUrl, "sa", "")
                .locations("classpath:db/migration")
                .load();

        flyway.migrate();

        assertThat(flyway.info().current().getVersion().getVersion()).isEqualTo("6");

        try (
                Connection connection = DriverManager.getConnection(jdbcUrl, "sa", "");
                Statement statement = connection.createStatement()
        ) {
            assertThat(tableExists(statement, "users")).isTrue();
            assertThat(tableExists(statement, "posts")).isTrue();
            assertThat(tableExists(statement, "comments")).isTrue();
            assertThat(tableExists(statement, "post_counters")).isTrue();
            assertThat(tableExists(statement, "post_images")).isTrue();
            assertThat(tableExists(statement, "post_likes")).isTrue();
            assertThat(tableExists(statement, "post_likes_seq")).isFalse();
            assertThat(tableExists(statement, "post_reports")).isTrue();
            assertThat(tableExists(statement, "post_view_counts")).isTrue();
            assertThat(tableExists(statement, "auth_sessions")).isTrue();
            assertThat(columnExists(statement, "post_counters", "view_count")).isFalse();
            assertThat(columnExists(statement, "posts", "is_fixed")).isFalse();

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE script = 'B3__current_schema.sql'
                      AND success = TRUE
                    """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE script = 'V4__remove_legacy_post_counter_view_count.sql'
                    """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE script = 'V5__remove_post_is_fixed.sql'
                    """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }

            try (ResultSet resultSet = statement.executeQuery("""
                    SELECT COUNT(*)
                    FROM flyway_schema_history
                    WHERE script = 'V6__use_identity_for_post_likes.sql'
                    """)) {
                resultSet.next();
                assertThat(resultSet.getInt(1)).isEqualTo(1);
            }
        }
    }

    private boolean tableExists(Statement statement, String tableName) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT COUNT(*)
                FROM information_schema.tables
                WHERE table_schema = CURRENT_SCHEMA()
                  AND table_name = '%s'
                """.formatted(tableName))) {
            resultSet.next();
            return resultSet.getInt(1) == 1;
        }
    }

    private boolean columnExists(
            Statement statement,
            String tableName,
            String columnName
    ) throws Exception {
        try (ResultSet resultSet = statement.executeQuery("""
                SELECT COUNT(*)
                FROM information_schema.columns
                WHERE table_schema = CURRENT_SCHEMA()
                  AND table_name = '%s'
                  AND column_name = '%s'
                """.formatted(tableName, columnName))) {
            resultSet.next();
            return resultSet.getInt(1) == 1;
        }
    }
}
