package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.run.PlanRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresPlanRunReliabilityIntegrationTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PGSimpleDataSource dataSource;
	private PostgresDocumentStore store;
	private PostgresPlanRunRepository runs;

	@BeforeEach
	void setUp() {
		dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		store = new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM repository_documents WHERE repository_type='plan-run' "
				+ "OR repository_type='plan-approval'");
		}
		catch (Exception exception) {
			throw new IllegalStateException(exception);
		}
		runs = new PostgresPlanRunRepository(store, dataSource, new ObjectMapper());
	}

	@Test
	void planRunCreateIsIdempotentAndSaveIsVersionGuarded() {
		Plan plan = plan();
		PlanRun run = new PlanRun("run-approval-1", "approval-1", plan, List.of(), Instant.now());

		PlanRun stored = runs.createIfAbsent("approval-1", run);
		assertEquals("run-approval-1", stored.getId());
		assertEquals(0, stored.getVersion());
		assertThrows(IllegalStateException.class, () -> runs.create("approval-1", run));

		PlanRun existing = runs.createIfAbsent("approval-1",
			new PlanRun("run-approval-1", "approval-1", plan, List.of(), Instant.now()));
		assertEquals("run-approval-1", existing.getId());

		assertFalse(runs.saveIfUnchanged(existing, 5));
		assertTrue(runs.saveIfUnchanged(existing, existing.getVersion()));
		assertEquals(1, runs.get("run-approval-1").getVersion());

		PlanRun reloaded = runs.get("run-approval-1");
		assertEquals(1, reloaded.getVersion());
		assertEquals("approval-1", reloaded.getApprovalId());
	}

	@Test
	void coordinatorLeaseIsExclusiveReleasedAndExpiryTakeover() {
		Plan plan = plan();
		PlanRun run = new PlanRun("run-approval-2", "approval-2", plan, List.of(), Instant.now());
		runs.createIfAbsent("approval-2", run);
		Instant now = Instant.parse("2026-08-04T00:00:00Z");

		Optional<PlanRun> claimed = runs.claimCoordinator("run-approval-2", "scheduler-1", now,
			Duration.ofSeconds(30));
		assertTrue(claimed.isPresent());
		assertEquals("scheduler-1", claimed.get().getCoordinatorOwner());
		assertEquals(1, claimed.get().getCoordinatorToken());

		assertTrue(runs.claimCoordinator("run-approval-2", "scheduler-2", now.plusSeconds(10),
			Duration.ofSeconds(30)).isEmpty());

		assertTrue(runs.releaseCoordinator("run-approval-2", "scheduler-1", 1));
		assertTrue(runs.claimCoordinator("run-approval-2", "scheduler-2", now.plusSeconds(11),
			Duration.ofSeconds(30)).isPresent());

		// Held by scheduler-2 until +41s; after expiry scheduler-3 can take over.
		assertTrue(runs.claimCoordinator("run-approval-2", "scheduler-3", now.plusSeconds(60),
			Duration.ofSeconds(30)).isPresent());
	}

	@Test
	void approvalConsumeIsAtomicAcrossInstances() {
		PostgresPlanApprovalRepository approvals = new PostgresPlanApprovalRepository(store,
			dataSource);
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "request-1", plan(),
			"hash", Instant.now());
		approval.approve("reviewer", Instant.now());
		approvals.save(approval);

		assertTrue(approvals.consumeIfApproved("approval-1"));
		assertFalse(approvals.consumeIfApproved("approval-1"));
		assertEquals(ApprovalStatus.CONSUMED,
			new PostgresPlanApprovalRepository(store, dataSource).get("approval-1")
				.getStatus());
	}

	@Test
	void persistedRunRoundTripsCoordinatorLeaseAndSteps() {
		Plan plan = plan();
		PlanRun run = new PlanRun("run-approval-3", "approval-3", plan, List.of(), Instant.now());
		runs.createIfAbsent("approval-3", run);
		Instant now = Instant.parse("2026-08-04T00:00:00Z");
		Optional<PlanRun> claimed = runs.claimCoordinator("run-approval-3", "scheduler-1", now,
			Duration.ofSeconds(30));
		assertTrue(claimed.isPresent());

		PlanRun reloaded = new PostgresPlanRunRepository(store, dataSource, new ObjectMapper())
			.get("run-approval-3");
		assertNotNull(reloaded);
		assertEquals("scheduler-1", reloaded.getCoordinatorOwner());
		assertEquals(1, reloaded.getCoordinatorToken());
		assertEquals(now.plusSeconds(30), reloaded.getCoordinatorExpiresAt());
	}

	private Plan plan() {
		return new Plan("plan-1", 1, "Execute plan", PlanStatus.DRAFT, List.of(), List.of(),
			null, Instant.now());
	}
}
