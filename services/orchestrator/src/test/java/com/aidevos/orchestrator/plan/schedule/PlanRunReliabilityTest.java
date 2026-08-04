package com.aidevos.orchestrator.plan.schedule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStore;
import com.aidevos.orchestrator.job.JobWorker;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.ExpectedArtifact;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.PlanValidator;
import com.aidevos.orchestrator.plan.RetryPolicy;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.approval.PlanApprovalStore;
import com.aidevos.orchestrator.plan.run.InMemoryPlanRunRepository;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunStatus;
import com.aidevos.orchestrator.plan.run.StepAttempt;
import com.aidevos.orchestrator.plan.run.StepRun;
import com.aidevos.orchestrator.planner.replan.FailureClassifier;
import com.aidevos.orchestrator.planner.replan.ReplanRequestService;
import com.aidevos.orchestrator.planner.replan.ReplanRequestStore;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanRunReliabilityTest {

	private static final Instant NOW = Instant.parse("2026-08-04T06:00:00Z");

	private final InMemoryPlanRunRepository repository = new InMemoryPlanRunRepository();
	private final JobStore jobStore = new JobStore();
	private final ReplanRequestService replanRequestService = new ReplanRequestService(
		new ReplanRequestStore(), new FailureClassifier(), Clock.fixed(NOW, ZoneOffset.UTC));
	private final JobService jobService;
	private PlanApprovalService approvalService;

	{
		JobWorker worker = mock(JobWorker.class);
		when(worker.submit(any())).thenReturn(true);
		jobService = new JobService(jobStore, worker);
	}

	@Test
	void casSaveRejectsStaleVersion() {
		PlanRun run = run(plan(singleStep("one", false)));

		assertTrue(repository.saveIfUnchanged(run, run.getVersion()));
		assertFalse(repository.saveIfUnchanged(run, run.getVersion() - 1));
		assertTrue(repository.saveIfUnchanged(run, run.getVersion()));
	}

	@Test
	void createIfAbsentIsIdempotentWhileCreateRejectsDuplicates() {
		Plan plan = plan(singleStep("one", false));
		PlanRun first = new PlanRun("run-approval-1", "approval-1", plan, List.of(), NOW);
		assertSame(first, repository.createIfAbsent("approval-1", first));

		PlanRun second = new PlanRun("run-approval-1", "approval-1", plan, List.of(), NOW);
		assertSame(first, repository.createIfAbsent("approval-1", second));
		assertThrows(IllegalStateException.class, () -> repository.create("approval-1", second));
	}

	@Test
	void coordinatorLeaseIsExclusiveUntilReleasedOrExpired() {
		PlanRun run = run(plan(singleStep("one", false)));

		Optional<PlanRun> claimed = repository.claimCoordinator(run.getId(), "scheduler-1", NOW,
			Duration.ofSeconds(30));
		assertTrue(claimed.isPresent());
		assertEquals("scheduler-1", claimed.get().getCoordinatorOwner());
		assertEquals(1, claimed.get().getCoordinatorToken());

		assertTrue(repository.claimCoordinator(run.getId(), "scheduler-2", NOW.plusSeconds(10),
			Duration.ofSeconds(30)).isEmpty());

		assertTrue(repository.releaseCoordinator(run.getId(), "scheduler-1", 1));
		Optional<PlanRun> retaken = repository.claimCoordinator(run.getId(), "scheduler-2",
			NOW.plusSeconds(11), Duration.ofSeconds(30));
		assertTrue(retaken.isPresent());
		assertEquals("scheduler-2", retaken.get().getCoordinatorOwner());
		assertEquals(2, retaken.get().getCoordinatorToken());
	}

	@Test
	void coordinatorLeaseCanBeTakenOverAfterExpiry() {
		PlanRun run = run(plan(singleStep("one", false)));
		repository.claimCoordinator(run.getId(), "scheduler-1", NOW, Duration.ofSeconds(30));

		Optional<PlanRun> takeover = repository.claimCoordinator(run.getId(), "scheduler-2",
			NOW.plusSeconds(31), Duration.ofSeconds(30));

		assertTrue(takeover.isPresent());
		assertEquals("scheduler-2", takeover.get().getCoordinatorOwner());
	}

	@Test
	void reconcileSkipsRunHeldByAnotherCoordinator() {
		PlanScheduler scheduler = schedulerWithMockedApproval();
		PlanRun run = scheduler.start("approval-1");
		int jobsBefore = jobStore.getAll().size();

		repository.claimCoordinator(run.getId(), "other-scheduler", NOW, Duration.ofSeconds(30));
		int versionBefore = run.getVersion();
		scheduler.reconcile();

		assertEquals(jobsBefore, jobStore.getAll().size());
		assertEquals(versionBefore, run.getVersion());
	}

	@Test
	void crashWindowRunWithApprovalStillApprovedIsConvergedByReconcile() {
		PlanApprovalStore approvalStore = new PlanApprovalStore();
		PlanApprovalService realApprovalService = new PlanApprovalService(approvalStore,
			new PlanValidator(), new ObjectMapper(), AuditService.noop());
		Plan plan = plan(singleStep("one", false));
		PlanApprovalRequest approval = realApprovalService.create("request-1", plan);
		realApprovalService.approve(approval.getId(), "reviewer");
		List<StepRun> stepRuns = plan.steps().stream()
			.map(step -> new StepRun("run-" + approval.getId() + ":step:" + step.id(),
				step.id()))
			.toList();
		PlanRun run = new PlanRun("run-" + approval.getId(), approval.getId(), plan, stepRuns,
			NOW);
		repository.createIfAbsent(approval.getId(), run);
		PlanScheduler scheduler = new PlanScheduler(jobService, new StepTaskFactory(),
			realApprovalService, replanRequestService, repository, Clock.fixed(NOW, ZoneOffset.UTC));

		scheduler.reconcile();
		scheduler.reconcile();

		assertEquals(ApprovalStatus.CONSUMED,
			realApprovalService.get(approval.getId()).getStatus());
		assertEquals(1, jobStore.getAll().size());
		assertEquals(PlanRunStatus.RUNNING, run.getStatus());
	}

	@Test
	void missingJobBindingIsRepairedWithoutDuplicateJob() {
		Plan plan = plan(singleStep("one", false));
		StepRun step = new StepRun("run-approval-1:step:one", "one");
		StepAttempt attempt = step.startAttempt("run-approval-1:step:one:attempt:1", NOW);
		PlanRun run = new PlanRun("run-approval-1", "approval-1", plan, List.of(step), NOW);
		run.markRunning(NOW);
		repository.createIfAbsent("approval-1", run);
		String expectedJobId = "job-" + attempt.getId();
		TaskDefinition task = new TaskDefinition();
		task.setId("run-approval-1:one:1");
		jobStore.createIfAbsent(new ExecutionJob(expectedJobId, task));
		PlanScheduler scheduler = schedulerWithMockedApproval(plan);

		scheduler.reconcile();

		assertEquals(1, jobStore.getAll().size());
		assertEquals(expectedJobId, step.getCurrentAttempt().getJobId());
	}

	@Test
	void concurrentReconcileFromTwoSchedulersKeepsSingleJob() {
		PlanScheduler first = schedulerWithMockedApproval();
		first.start("approval-1");
		PlanScheduler second = new PlanScheduler(jobService, new StepTaskFactory(),
			approvalService, replanRequestService, repository, Clock.fixed(NOW, ZoneOffset.UTC));

		for (int index = 0; index < 20; index++) {
			first.reconcile();
			second.reconcile();
		}

		assertEquals(1, jobStore.getAll().size());
	}

	@Test
	void concurrentStartCreatesExactlyOneRunAndConsumesOnce() {
		PlanScheduler first = schedulerWithMockedApproval();
		PlanScheduler second = new PlanScheduler(jobService, new StepTaskFactory(),
			approvalService, replanRequestService, repository, Clock.fixed(NOW, ZoneOffset.UTC));

		PlanRun run = first.start("approval-1");
		assertThrows(IllegalStateException.class, () -> second.start("approval-1"));

		assertEquals(1, repository.getAll().size());
		assertEquals(run.getId(), repository.findRunIdByApproval("approval-1"));
		assertEquals(1, jobStore.getAll().size());
		verify(approvalService, times(1)).consume("approval-1");
	}

	private PlanScheduler schedulerWithMockedApproval() {
		return schedulerWithMockedApproval(plan(singleStep("one", false)));
	}

	private PlanScheduler schedulerWithMockedApproval(Plan plan) {
		approvalService = mock(PlanApprovalService.class);
		when(approvalService.get("approval-1")).thenReturn(approvedApproval(plan));
		when(approvalService.consume("approval-1")).thenReturn(consumedApproval(plan));
		return new PlanScheduler(jobService, new StepTaskFactory(), approvalService,
			replanRequestService, repository, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private PlanApprovalRequest approvedApproval(Plan plan) {
		PlanApprovalRequest approval = new PlanApprovalRequest("approval-1", "request-1", plan,
			"hash", NOW);
		approval.approve("reviewer", NOW);
		return approval;
	}

	private PlanApprovalRequest consumedApproval(Plan plan) {
		PlanApprovalRequest approval = approvedApproval(plan);
		approval.consume();
		return approval;
	}

	private PlanRun run(Plan plan) {
		PlanRun run = new PlanRun("run-approval-1", "approval-1", plan, List.of(), NOW);
		repository.createIfAbsent("approval-1", run);
		return run;
	}

	private Plan plan(List<PlanStep> steps) {
		return new Plan("plan-1", 1, "Execute plan", PlanStatus.DRAFT, steps, List.of(),
			snapshot(), NOW);
	}

	private List<PlanStep> singleStep(String id, boolean expectedArtifact) {
		List<ExpectedArtifact> artifacts = expectedArtifact
			? List.of(new ExpectedArtifact("text", "result", "text/plain", true, 1))
			: List.of();
		return List.of(new PlanStep(id, id, "Execute " + id, StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding"), List.of()), Map.of("input", id),
			List.of(), null, null, Map.of(), artifacts, RetryPolicy.noRetry(),
			FailurePolicy.STOP_PLAN, false));
	}

	private PlanSnapshot snapshot() {
		return new PlanSnapshot(List.of(new PlanSnapshot.AgentSnapshot("coder", "codex",
			List.of("coding"), "workspace-write", true)), Set.of("coding"), List.of(),
			Set.of("codex"), "policy-v1", Map.of());
	}
}
