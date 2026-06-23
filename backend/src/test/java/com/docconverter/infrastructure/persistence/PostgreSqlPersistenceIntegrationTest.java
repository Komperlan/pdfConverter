package com.docconverter.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import com.docconverter.application.port.out.ConversionJobRepository;
import com.docconverter.domain.model.ConversionJob;
import com.docconverter.domain.model.ConversionStatus;
import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.postgresql.PostgreSQLContainer;

@SpringBootTest(properties = {
        "doc-converter.worker.enabled=false",
        "doc-converter.recovery.enabled=false",
        "doc-converter.retention.enabled=false"
})
@Testcontainers(disabledWithoutDocker = true)
class PostgreSqlPersistenceIntegrationTest {

    @Container
    @ServiceConnection
    static final PostgreSQLContainer POSTGRESQL = new PostgreSQLContainer("postgres:18")
            .withDatabaseName("docconverter_test")
            .withUsername("docconverter")
            .withPassword("docconverter");

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ConversionJobRepository conversionJobRepository;

    @Autowired
    private Flyway flyway;

    private ExecutorService executorService;

    @BeforeEach
    void cleanDatabase() {
        jdbcTemplate.update("delete from conversion_attempts");
        jdbcTemplate.update("delete from conversion_jobs");
    }

    @AfterEach
    void stopExecutor() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test
    void appliesBaselineMigrationAndValidatesCurrentSchema() {
        List<MigrationHistoryEntry> migrations = jdbcTemplate.query(
                """
                select version, description, checksum
                from flyway_schema_history
                where success = true
                  and version is not null
                order by installed_rank
                """,
                (resultSet, rowNumber) -> new MigrationHistoryEntry(
                        resultSet.getString("version"),
                        resultSet.getString("description"),
                        resultSet.getObject("checksum", Integer.class)
                )
        );

        assertThat(migrations)
                .extracting(MigrationHistoryEntry::version)
                .containsExactly("1");
        assertThat(migrations)
                .extracting(MigrationHistoryEntry::description)
                .containsExactly("create conversion schema");
        assertThat(migrations)
                .extracting(MigrationHistoryEntry::checksum)
                .doesNotContainNull();
        assertThat(jdbcTemplate.queryForObject(
                "select current_setting('server_version')",
                String.class
        )).startsWith("18.");

        assertThat(tableNames()).contains("conversion_jobs", "conversion_attempts");
        assertThat(columnNames("conversion_jobs")).contains(
                "id",
                "checksum_sha256",
                "version",
                "expired_at",
                "cleanup_completed_at",
                "next_attempt_at"
        );
        assertThat(columnNames("conversion_attempts")).contains(
                "id",
                "job_id",
                "status",
                "external_request_id",
                "error_code"
        );
        assertThat(checksumColumnDefinition())
                .containsEntry("data_type", "character")
                .containsEntry("character_maximum_length", "64");

        assertThat(indexNames()).contains(
                "idx_conversion_jobs_cleanup_pending",
                "idx_conversion_jobs_ready_for_processing",
                "idx_conversion_jobs_stalled_processing",
                "idx_conversion_attempts_started_recovery"
        );

        Map<String, String> constraints = constraintDefinitions();
        assertThat(constraints).containsKeys(
                "chk_conversion_jobs_status",
                "chk_conversion_jobs_sha256_format",
                "chk_conversion_jobs_expired_at",
                "chk_conversion_jobs_cleanup_completed_at",
                "chk_conversion_jobs_next_attempt_at",
                "chk_conversion_jobs_last_error_code",
                "chk_conversion_attempts_error_code"
        );
        assertThat(constraints.get("chk_conversion_jobs_last_error_code"))
                .contains("PROCESSING_INTERRUPTED");
        assertThat(constraints.get("chk_conversion_attempts_error_code"))
                .contains("PROCESSING_INTERRUPTED");

        assertThat(flyway.validateWithResult().validationSuccessful).isTrue();
        assertThat(flyway.migrate().migrationsExecuted).isZero();
        assertThat(successfulMigrationCount()).isEqualTo(1);
    }

    @Test
    void concurrentClaimsReturnDifferentJobs() throws Exception {
        ConversionJob firstJob = conversionJobRepository.save(newJob("first.docx"));
        ConversionJob secondJob = conversionJobRepository.save(newJob("second.docx"));
        Instant claimAt = Instant.now().plusSeconds(1);
        CountDownLatch ready = new CountDownLatch(2);
        CountDownLatch start = new CountDownLatch(1);
        executorService = Executors.newFixedThreadPool(2);

        Future<List<ConversionJob>> firstClaim = executorService.submit(
                () -> claimOneJob(ready, start, claimAt)
        );
        Future<List<ConversionJob>> secondClaim = executorService.submit(
                () -> claimOneJob(ready, start, claimAt)
        );
        assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue();
        start.countDown();

        List<ConversionJob> firstResult = firstClaim.get(10, TimeUnit.SECONDS);
        List<ConversionJob> secondResult = secondClaim.get(10, TimeUnit.SECONDS);
        assertThat(firstResult).hasSize(1);
        assertThat(secondResult).hasSize(1);
        assertThat(List.of(
                firstResult.getFirst().getId(),
                secondResult.getFirst().getId()
        )).containsExactlyInAnyOrder(firstJob.getId(), secondJob.getId());
        assertThat(firstResult.getFirst().getStatus()).isEqualTo(ConversionStatus.PROCESSING);
        assertThat(secondResult.getFirst().getStatus()).isEqualTo(ConversionStatus.PROCESSING);
        assertThat(conversionJobRepository.claimNextForProcessing(1, claimAt)).isEmpty();
    }

    private List<ConversionJob> claimOneJob(
            CountDownLatch ready,
            CountDownLatch start,
            Instant claimAt
    ) throws InterruptedException {
        ready.countDown();
        if (!start.await(5, TimeUnit.SECONDS)) {
            throw new IllegalStateException("Concurrent claim did not start in time");
        }
        return conversionJobRepository.claimNextForProcessing(1, claimAt);
    }

    private ConversionJob newJob(String filename) {
        Instant createdAt = Instant.now();
        return ConversionJob.create(
                filename,
                filename,
                "source/2026/06/22/" + filename,
                "docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/x-tika-ooxml",
                128,
                "a".repeat(64),
                createdAt,
                createdAt.plusSeconds(3_600),
                3
        );
    }

    private List<String> columnNames(String tableName) {
        return jdbcTemplate.queryForList(
                """
                select column_name
                from information_schema.columns
                where table_schema = 'public'
                  and table_name = ?
                """,
                String.class,
                tableName
        );
    }

    private List<String> tableNames() {
        return jdbcTemplate.queryForList(
                """
                select table_name
                from information_schema.tables
                where table_schema = 'public'
                  and table_type = 'BASE TABLE'
                """,
                String.class
        );
    }

    private Map<String, String> checksumColumnDefinition() {
        return jdbcTemplate.queryForMap(
                        """
                        select data_type, character_maximum_length::text
                        from information_schema.columns
                        where table_schema = 'public'
                          and table_name = 'conversion_jobs'
                          and column_name = 'checksum_sha256'
                        """
                ).entrySet().stream()
                .collect(Collectors.toMap(
                        entry -> entry.getKey().toLowerCase(),
                        entry -> String.valueOf(entry.getValue())
                ));
    }

    private Map<String, String> constraintDefinitions() {
        return jdbcTemplate.query(
                """
                select constraint_record.conname as constraint_name,
                       pg_get_constraintdef(constraint_record.oid) as definition
                from pg_constraint constraint_record
                join pg_class table_record
                  on table_record.oid = constraint_record.conrelid
                join pg_namespace schema_record
                  on schema_record.oid = table_record.relnamespace
                where schema_record.nspname = 'public'
                  and table_record.relname in ('conversion_jobs', 'conversion_attempts')
                """,
                resultSet -> {
                    Map<String, String> result = new HashMap<>();
                    while (resultSet.next()) {
                        result.put(
                                resultSet.getString("constraint_name"),
                                resultSet.getString("definition")
                        );
                    }
                    return result;
                }
        );
    }

    private int successfulMigrationCount() {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from flyway_schema_history
                where success = true
                  and version is not null
                """,
                Integer.class
        );
        return count == null ? 0 : count;
    }

    private List<String> indexNames() {
        return jdbcTemplate.queryForList(
                """
                select indexname
                from pg_indexes
                where schemaname = 'public'
                """,
                String.class
        );
    }

    private record MigrationHistoryEntry(String version, String description, Integer checksum) {
    }
}
