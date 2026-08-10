package com.aidevos.orchestrator.persistence.postgresql;

import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Migration gate validation. Verifies that a fresh database is fully
 * initialized by V1..V12 and that a Phase 7-era database (V1..V4 only)
 * upgrades in place without losing data.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresMigrationValidationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@Test
	void freshDatabaseAppliesAllMigrationsV1ThroughV23() throws Exception {
		PGSimpleDataSource dataSource = dataSource(POSTGRES.getDatabaseName());
		new PostgresDocumentStore(dataSource, new ObjectMapper());

		assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
				18, 19, 20, 21, 22, 23),
			appliedVersions(dataSource));
		for (String table : List.of("repository_documents", "audit_events",
				"plan_version_freezes", "audit_outbox", "jobs", "execution_attempts",
				"memory_records", "projects", "skills", "agent_packages", "mcp_plugins",
				"workspaces", "tasks", "execution_records", "change_sets", "commits",
				"ci_runs", "repair_tasks", "pr_feedback", "traces", "usage_records",
				"schema_migrations")) {
			assertTrue(tableExists(dataSource, table), "missing table: " + table);
		}
		for (String column : List.of("repository_url", "default_branch")) {
			assertTrue(columnExists(dataSource, "projects", column),
				"projects column missing: " + column);
		}
		for (String column : List.of("skill_id", "name", "version", "enabled",
				"created_at", "updated_at")) {
			assertTrue(columnExists(dataSource, "skills", column),
				"skills column missing: " + column);
		}
		for (String column : List.of("agent_id", "version", "installed", "enabled")) {
			assertTrue(columnExists(dataSource, "agent_packages", column),
				"agent_packages column missing: " + column);
		}
		for (String column : List.of("plugin_id", "enabled", "permission_level")) {
			assertTrue(columnExists(dataSource, "mcp_plugins", column),
				"mcp_plugins column missing: " + column);
		}
		assertTrue(columnExists(dataSource, "repository_documents", "version"),
			"V6 coordinator version column missing");
		for (String column : List.of("attempt_no", "max_attempts", "available_at", "priority",
				"lease_owner", "lease_token", "lease_expires_at", "heartbeat_at", "version",
				"recovery_count", "last_failure_code", "recovery_policy")) {
			assertTrue(columnExists(dataSource, "jobs", column),
				"jobs column missing: " + column);
		}
		for (String column : List.of("topic", "next_attempt_at", "dead_lettered_at")) {
			assertTrue(columnExists(dataSource, "audit_outbox", column),
				"audit_outbox column missing: " + column);
		}
		for (String index : List.of("idx_audit_outbox_claim", "idx_audit_outbox_pending",
				"uq_plan_run_approval")) {
			assertTrue(indexExists(dataSource, index), "missing index: " + index);
		}
	}

	@Test
	void oldDatabaseUpgradesInPlaceWithoutLosingData() throws Exception {
		String oldDatabase = "ai_dev_os_old";
		try (Connection connection = POSTGRES.createConnection("");
				Statement statement = connection.createStatement()) {
			statement.execute("DROP DATABASE IF EXISTS " + oldDatabase);
			statement.execute("CREATE DATABASE " + oldDatabase);
		}
		PGSimpleDataSource dataSource = dataSource(oldDatabase);

		// Simulate a Phase 7-era deployment: only V1..V4 are applied and the
		// repository already contains real rows.
		applyMigrations(dataSource, 4);
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO repository_documents(repository_type,entity_id,payload,secondary_key) "
				+ "VALUES ('plan-run','run-old','{\"id\":\"run-old\"}','approval-old')");
			statement.execute("INSERT INTO audit_events(id,event_type,occurred_at,aggregate_type,"
				+ "aggregate_id,idempotency_key,payload) VALUES ('event-old','JOB_SUBMITTED',"
				+ "CURRENT_TIMESTAMP,'job','job-old','key-old','{}')");
			statement.execute("INSERT INTO audit_outbox(idempotency_key,event_payload) "
				+ "VALUES ('outbox-old','{}')");
			statement.execute("INSERT INTO plan_version_freezes(version_key,snapshot_hash) "
				+ "VALUES ('plan:1','hash-old')");
		}

		// The full migration set upgrades V5..V23 in place.
		new PostgresDocumentStore(dataSource, new ObjectMapper());

		assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
				18, 19, 20, 21, 22, 23),
			appliedVersions(dataSource));
		assertEquals(1, count(dataSource,
			"SELECT COUNT(*) FROM repository_documents WHERE entity_id='run-old'"));
		assertEquals(1, count(dataSource,
			"SELECT COUNT(*) FROM audit_events WHERE id='event-old'"));
		assertEquals(1, count(dataSource,
			"SELECT COUNT(*) FROM plan_version_freezes WHERE version_key='plan:1'"));
		// V7 backfills the relay control columns on pre-existing rows.
		assertEquals(1, count(dataSource,
			"SELECT COUNT(*) FROM audit_outbox WHERE idempotency_key='outbox-old' "
				+ "AND topic='audit' AND next_attempt_at IS NOT NULL"));
		// V5's jobs table is usable with its control defaults after the upgrade.
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO jobs(id,task_snapshot,created_at,status) "
				+ "VALUES ('job-upgraded','{}',CURRENT_TIMESTAMP,'QUEUED')");
		}
		assertEquals(1, count(dataSource,
			"SELECT COUNT(*) FROM jobs WHERE id='job-upgraded' AND version=0 "
				+ "AND recovery_policy='MANUAL'"));
		// V8's memory_records table is usable after the upgrade.
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO memory_records(id,project_id,type,key,content,"
				+ "created_at,updated_at) VALUES ('mem-upgraded','default','PROJECT_RULE',"
				+ "'rule-1','use V8',CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
		}
		assertEquals(1, count(dataSource,
			"SELECT COUNT(*) FROM memory_records WHERE id='mem-upgraded' AND type='PROJECT_RULE'"));
		assertTrue(columnExists(dataSource, "repository_documents", "version"),
			"V6 version column missing after upgrade");
		// V10-V12 registry tables are usable after the upgrade.
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("INSERT INTO skills(skill_id,name,enabled,created_at,updated_at) "
				+ "VALUES ('skill-upgraded','Upgraded',TRUE,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)");
			statement.execute("INSERT INTO agent_packages(agent_id,installed,enabled) "
				+ "VALUES ('pkg-upgraded',FALSE,TRUE)");
			statement.execute("INSERT INTO mcp_plugins(plugin_id,enabled,permission_level) "
				+ "VALUES ('plugin-upgraded',TRUE,'read-only')");
		}
		assertEquals(1, count(dataSource,
			"SELECT COUNT(*) FROM skills WHERE skill_id='skill-upgraded' AND enabled"));
		assertEquals(1, count(dataSource,
			"SELECT COUNT(*) FROM agent_packages WHERE agent_id='pkg-upgraded'"));
		assertEquals(1, count(dataSource,
			"SELECT COUNT(*) FROM mcp_plugins WHERE plugin_id='plugin-upgraded'"));

		// Re-running the migration remains idempotent.
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		assertEquals(Set.of(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11, 12, 13, 14, 15, 16, 17,
				18, 19, 20, 21, 22, 23),
			appliedVersions(dataSource));
	}

	private void applyMigrations(DataSource dataSource, int upToVersion) throws Exception {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("CREATE TABLE IF NOT EXISTS schema_migrations "
				+ "(version INTEGER PRIMARY KEY, name VARCHAR(255) NOT NULL, "
				+ "applied_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP)");
			Resource[] migrations = new PathMatchingResourcePatternResolver()
				.getResources("classpath*:/db/migration/V*.sql");
			Arrays.sort(migrations, Comparator.comparingInt(this::migrationVersion));
			for (Resource migration : migrations) {
				int version = migrationVersion(migration);
				if (version > upToVersion) {
					continue;
				}
				try (var input = migration.getInputStream()) {
					String sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
					connection.setAutoCommit(false);
					statement.execute(sql);
					try (PreparedStatement record = connection.prepareStatement(
							"INSERT INTO schema_migrations(version,name) VALUES (?,?)")) {
						record.setInt(1, version);
						record.setString(2, migration.getFilename());
						record.executeUpdate();
					}
					connection.commit();
					connection.setAutoCommit(true);
				}
			}
		}
	}

	private int migrationVersion(Resource migration) {
		String name = migration.getFilename();
		return Integer.parseInt(name.substring(1, name.indexOf("__")));
	}

	private PGSimpleDataSource dataSource(String database) {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl("jdbc:postgresql://" + POSTGRES.getHost() + ":"
			+ POSTGRES.getMappedPort(PostgreSQLContainer.POSTGRESQL_PORT) + "/" + database);
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		return dataSource;
	}

	private Set<Integer> appliedVersions(DataSource dataSource) throws Exception {
		Set<Integer> versions = new TreeSet<>();
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(
					"SELECT version FROM schema_migrations")) {
			while (result.next()) {
				versions.add(result.getInt(1));
			}
		}
		return versions;
	}

	private boolean tableExists(DataSource dataSource, String table) throws Exception {
		return count(dataSource, "SELECT COUNT(*) FROM information_schema.tables "
			+ "WHERE table_schema='public' AND table_name='" + table + "'") == 1;
	}

	private boolean columnExists(DataSource dataSource, String table, String column)
			throws Exception {
		return count(dataSource, "SELECT COUNT(*) FROM information_schema.columns "
			+ "WHERE table_schema='public' AND table_name='" + table
			+ "' AND column_name='" + column + "'") == 1;
	}

	private boolean indexExists(DataSource dataSource, String index) throws Exception {
		return count(dataSource, "SELECT COUNT(*) FROM pg_indexes WHERE indexname='" + index + "'")
			== 1;
	}

	private int count(DataSource dataSource, String sql) throws SQLException {
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement();
				ResultSet result = statement.executeQuery(sql)) {
			result.next();
			return result.getInt(1);
		}
	}
}
