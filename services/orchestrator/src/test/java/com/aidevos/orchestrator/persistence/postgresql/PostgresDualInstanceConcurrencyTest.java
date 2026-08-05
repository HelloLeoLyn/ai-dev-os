package com.aidevos.orchestrator.persistence.postgresql;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobLease;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.JobWorker;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.RetryPolicy;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.StepAttempt;
import com.aidevos.orchestrator.plan.run.StepRun;
import com.aidevos.orchestrator.plan.schedule.PlanScheduler;
import com.aidevos.orchestrator.plan.schedule.StepTaskFactory;
import com.aidevos.orchestrator.planner.replan.FailureClassifier;
import com.aidevos.orchestrator.planner.replan.ReplanRequestService;
import com.aidevos.orchestrator.planner.replan.ReplanRequestStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Phase 8-F dual-instance validation: two worker instances racing for the same
 * job and two scheduler instances racing for the same plan run must never
 * double-claim or double-advance. Every claim is executed on its own repository
 * and connection, like two application instances sharing one PostgreSQL.
 */
@Testcontainers(disabledWithoutDocker = true)
class PostgresDualInstanceConcurrencyTest {

	private static final Instant NOW = Instant.parse("2026-08-05T00:00:00Z");
	private static final Duration LEASE = Duration.ofSeconds(60);

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@BeforeEach
	void setUp() throws Exception {
		new PostgresDocumentStore(dataSource(), new ObjectMapper());
		try (java.sql.Connection connection = dataSource().getConnection();
				java.sql.Statement statement = connection.createStatement()) {
			statement.execute("TRUNCATE jobs, execution_attempts, repository_documents, "
				+ "audit_outbox, audit_events, plan_version_freezes RESTART IDENTITY");
		}
	}

	@Test
	void concurrentJobClaimGrantsExactlyOneLease() throws Exception {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1");
		new PostgresLeaseableJobRepository(dataSource(), new ObjectMapper())
			.save(new ExecutionJob("job-1", task));

		List<Optional<JobLease>> leases = race(2, index -> new PostgresLeaseableJobRepository(
			dataSource(), new ObjectMapper()).claimNext(NOW, "worker-" + index, LEASE));

		assertEquals(1, leases.stream().filter(Optional::isPresent).count());
		ExecutionJob stored = new PostgresLeaseableJobRepository(dataSource(),
			new ObjectMapper()).get("job-1");
		assertEquals(JobStatus.RUNNING, stored.getStatus());
		assertEquals(1, stored.getAttemptNo());
		assertEquals(1, stored.getLeaseToken());
		assertNotNull(stored.getLeaseOwner());
		assertTrue(stored.getLeaseExpiresAt().equals(NOW.plus(LEASE)));
	}

	@Test
	void concurrentCoordinatorClaimGrantsExactlyOneOwner() throws Exception {
		PostgresDocumentStore store = new PostgresDocumentStore(dataSource(),
			new ObjectMapper());
		PostgresPlanRunRepository runs = new PostgresPlanRunRepository(store, dataSource(),
			new ObjectMapper());
		PlanRun run = new PlanRun("run-1", "approval-1", plan(), List.of(), NOW);
		runs.createIfAbsent("approval-1", run);

		List<Optional<PlanRun>> claims = race(2, index -> new PostgresPlanRunRepository(store,
			dataSource(), new ObjectMapper()).claimCoordinator("run-1", "scheduler-" + index,
			NOW, Duration.ofSeconds(30)));

		assertEquals(1, claims.stream().filter(Optional::isPresent).count());
		PlanRun stored = runs.get("run-1");
		assertNotNull(stored.getCoordinatorOwner());
		assertEquals(1, stored.getCoordinatorToken());
	}

	@Test
	void concurrentSchedulersAdvanceStepOnlyOnce() throws Exception {
		PostgresDocumentStore store = new PostgresDocumentStore(dataSource(),
			new ObjectMapper());
		PostgresLeaseableJobRepository jobs = new PostgresLeaseableJobRepository(dataSource(),
			new ObjectMapper());
		PostgresPlanRunRepository runs = new PostgresPlanRunRepository(store, dataSource(),
			new ObjectMapper());
		PostgresPlanApprovalRepository approvals = new PostgresPlanApprovalRepository(store,
			dataSource());

		JobWorker worker = mock(JobWorker.class);
		when(worker.submit(any())).thenReturn(true);
		JobService jobService = new JobService(jobs, worker);
		PlanApprovalService approvalService = new PlanApprovalService(approvals,
			new com.aidevos.orchestrator.plan.PlanValidator(), new ObjectMapper(),
			AuditService.noop());
		ReplanRequestService replans = new ReplanRequestService(new ReplanRequestStore(),
			new FailureClassifier(), Clock.fixed(NOW, ZoneOffset.UTC));

		Plan plan = plan();
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "request-1", plan,
			"hash", NOW);
		approval.approve("reviewer", NOW);
		approvals.save(approval);
		StepRun stepRun = new StepRun("run-approval-1:step:one", "one");
		PlanRun run = new PlanRun("run-approval-1", "approval-1", plan, List.of(stepRun), NOW);
		runs.createIfAbsent("approval-1", run);

		PlanScheduler first = new PlanScheduler(jobService, new StepTaskFactory(),
			approvalService, replans, runs, AuditService.noop());
		PlanScheduler second = new PlanScheduler(jobService, new StepTaskFactory(),
			approvalService, replans, runs, AuditService.noop());

		race(2, index -> {
			(index == 0 ? first : second).reconcile();
			return Optional.empty();
		});

		PlanRun after = runs.get(run.getId());
		StepAttempt attempt = after.getSteps().getFirst().getCurrentAttempt();
		assertNotNull(attempt, "step attempt must be started exactly once");
		String jobId = attempt.getJobId();
		assertNotNull(jobId, "attempt must be bound to a job");
		assertEquals("job-run-approval-1:step:one:attempt:1", jobId);
		assertEquals(1, count("SELECT COUNT(*) FROM jobs WHERE id='" + jobId + "'"));
		assertEquals(1, count("SELECT COUNT(*) FROM jobs"));
	}

	private <T> List<T> race(int participants, CheckedSupplier<T> task) throws Exception {
		ExecutorService pool = Executors.newFixedThreadPool(participants);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<T>> futures = IntStream.range(0, participants).mapToObj(index ->
			pool.submit(() -> {
				start.await();
				T result = task.get(index);
				return result;
			})).toList();
		start.countDown();
		List<T> results = new ArrayList<>();
		for (Future<T> future : futures) {
			results.add(future.get(15, TimeUnit.SECONDS));
		}
		pool.shutdownNow();
		return results;
	}

	private interface CheckedSupplier<T> {
		T get(int index) throws Exception;
	}

	private int count(String sql) throws Exception {
		try (java.sql.Connection connection = dataSource().getConnection();
				java.sql.Statement statement = connection.createStatement();
				java.sql.ResultSet result = statement.executeQuery(sql)) {
			result.next();
			return result.getInt(1);
		}
	}

	private PGSimpleDataSource dataSource() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		return dataSource;
	}

	private Plan plan() {
		PlanStep step = new PlanStep("one", "one", "Execute one", StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding"), List.of()), Map.of("input", "one"),
			List.of(), null, null, Map.of(), List.of(), RetryPolicy.noRetry(),
			FailurePolicy.STOP_PLAN, false);
		return new Plan("plan-1", 1, "Execute plan", PlanStatus.DRAFT, List.of(step), List.of(),
			null, NOW);
	}
}
