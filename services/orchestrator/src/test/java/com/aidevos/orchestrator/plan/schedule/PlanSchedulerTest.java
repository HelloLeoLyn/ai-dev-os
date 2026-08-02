package com.aidevos.orchestrator.plan.schedule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.JobSubmissionResponse;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.Dependency;
import com.aidevos.orchestrator.plan.ExpectedArtifact;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.RetryPolicy;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunStatus;
import com.aidevos.orchestrator.plan.run.StepRunStatus;
import com.aidevos.orchestrator.planner.replan.ReplanRequestService;
import com.aidevos.orchestrator.planner.replan.ReplanRequest;
import com.aidevos.orchestrator.planner.replan.ReplanRequestStore;
import com.aidevos.orchestrator.planner.replan.FailureClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PlanSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-08-02T06:00:00Z");

	private final Map<String, ExecutionJob> jobs = new LinkedHashMap<>();
	private final List<TaskDefinition> submissions = new ArrayList<>();
	private JobService jobService;
	private PlanApprovalService approvalService;
	private ReplanRequestService replanRequestService;
	private ReplanRequestStore replanRequestStore;
	private PlanScheduler scheduler;

	@BeforeEach
	void setUp() {
		jobService = mock(JobService.class);
		approvalService = mock(PlanApprovalService.class);
		replanRequestStore = new ReplanRequestStore();
		replanRequestService = new ReplanRequestService(replanRequestStore,
			new FailureClassifier(), Clock.fixed(NOW, ZoneOffset.UTC));
		when(jobService.submit(any())).thenAnswer(invocation -> submit(invocation.getArgument(0)));
		when(jobService.get(any())).thenAnswer(invocation -> jobs.get(invocation.getArgument(0)));
		scheduler = new PlanScheduler(jobService, new StepTaskFactory(), approvalService,
			replanRequestService,
			Clock.fixed(NOW, ZoneOffset.UTC));
	}

	@Test
	void shouldExecuteSingleStepPlan() {
		Plan plan = plan(List.of(step("one", true)), List.of());
		approve(plan);

		PlanRun run = scheduler.start("approval-1");
		succeed(jobs.values().iterator().next(), true);
		scheduler.reconcile();

		assertEquals(PlanRunStatus.SUCCESS, run.getStatus());
		assertEquals(StepRunStatus.SUCCESS, run.getSteps().getFirst().getStatus());
		assertEquals(1, submissions.size());
	}

	@Test
	void shouldExecuteDependencyStepsSequentially() {
		Plan plan = plan(List.of(step("first", false), step("second", false)),
			List.of(new Dependency("first", "second", true)));
		approve(plan);

		PlanRun run = scheduler.start("approval-1");
		assertEquals(List.of("first"), submittedStepIds());
		succeed(job(0), false);
		scheduler.reconcile();
		assertEquals(List.of("first", "second"), submittedStepIds());
		succeed(job(1), false);
		scheduler.reconcile();

		assertEquals(PlanRunStatus.SUCCESS, run.getStatus());
		assertEquals(List.of(StepRunStatus.SUCCESS, StepRunStatus.SUCCESS),
			run.getSteps().stream().map(step -> step.getStatus()).toList());
	}

	@Test
	void jobFailureShouldStopPlanAndLeaveLaterStepPending() {
		Plan plan = plan(List.of(step("first", false), step("second", false)),
			List.of(new Dependency("first", "second", true)));
		approve(plan);
		PlanRun run = scheduler.start("approval-1");

		ExecutionResult failure = result(false, false);
		job(0).markFailed(failure, "executor failed", "record-1");
		scheduler.reconcile();

		assertEquals(PlanRunStatus.FAILED, run.getStatus());
		assertEquals(StepRunStatus.FAILED, run.getSteps().get(0).getStatus());
		assertEquals(StepRunStatus.PENDING, run.getSteps().get(1).getStatus());
		assertEquals(1, submissions.size());
	}

	@Test
	void missingExpectedArtifactShouldFailStep() {
		Plan plan = plan(List.of(step("one", true)), List.of());
		approve(plan);
		PlanRun run = scheduler.start("approval-1");

		succeed(job(0), false);
		scheduler.reconcile();

		assertEquals(PlanRunStatus.FAILED, run.getStatus());
		assertEquals("Expected artifact requirements were not satisfied", run.getError());
	}

	@Test
	void shouldMapAgentCapabilitiesParametersAndCorrelationMetadata() {
		PlanStep toolStep = new PlanStep("tool-step", "Read", "Read file", StepStatus.PLANNED,
			new AgentAssignment("tool-agent", List.of("tool", "read-only"), List.of()),
			"filesystem", "read_text_file", Map.of("path", "README.md"), List.of(),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);
		Plan plan = plan(List.of(toolStep), List.of());
		approve(plan);

		PlanRun run = scheduler.start("approval-1");
		TaskDefinition task = submissions.getFirst();

		assertEquals("tool-agent", task.getAgentName());
		assertEquals(List.of("tool", "read-only"), task.getRequiredCapabilities());
		assertEquals("filesystem", ((Map<?, ?>) task.getParameters().get("tool")).get("provider"));
		assertEquals(run.getId(), task.getMetadata().get("planRunId"));
		assertEquals(run.getSteps().getFirst().getId(), task.getMetadata().get("stepRunId"));
		assertEquals(run.getSteps().getFirst().getCurrentAttempt().getId(),
			task.getMetadata().get("attemptId"));
	}

	@Test
	void approvedPlanCanExecute() {
		Plan plan = plan(List.of(step("one", false)), List.of());
		approve(plan);

		PlanRun run = scheduler.start("approval-1");

		assertEquals(PlanRunStatus.RUNNING, run.getStatus());
		assertEquals(1, submissions.size());
	}

	@Test
	void unapprovedPlanMustBeRejected() {
		PlanApprovalRequest pending = approval(plan(List.of(step("one", false)), List.of()));
		when(approvalService.get("approval-1")).thenReturn(pending);

		assertThrows(IllegalStateException.class, () -> scheduler.start("approval-1"));
		assertEquals(0, submissions.size());
	}

	@Test
	void failedStepShouldCreateReplanRequestWithoutExecutingNewPlan() {
		Plan plan = plan(List.of(step("completed", false), replanStep("failed")),
			List.of(new Dependency("completed", "failed", true)));
		approve(plan);
		PlanRun run = scheduler.start("approval-1");
		succeed(job(0), false);
		scheduler.reconcile();
		ExecutionResult failure = result(false, true);
		failure.setMessage("tool failed");
		failure.getMetadata().put("toolResultCode", "MCP_ERROR");
		job(1).markFailed(failure, "tool failed", "record-2");

		scheduler.reconcile();

		ReplanRequest request = replanRequestStore.findByPlanRun(run.getId());
		assertEquals(PlanRunStatus.REPLAN_REQUIRED, run.getStatus());
		assertEquals("failed", request.failedStepId());
		assertEquals(List.of("completed"), request.completedSteps());
		assertEquals("record-2", request.executionRecord().id());
		assertEquals(1, request.artifactReferences().size());
		assertEquals("TOOL_ERROR", request.failureClassification().name());
		assertEquals(2, submissions.size());
	}

	private void approve(Plan plan) {
		PlanApprovalRequest approval = approval(plan);
		approval.approve("reviewer", NOW);
		when(approvalService.get("approval-1")).thenReturn(approval);
	}

	private PlanApprovalRequest approval(Plan plan) {
		return new PlanApprovalRequest("approval-1", "request-1", plan, "hash", NOW);
	}

	private JobSubmissionResponse submit(TaskDefinition task) {
		submissions.add(task);
		String jobId = "job-" + submissions.size();
		jobs.put(jobId, new ExecutionJob(jobId, task));
		return new JobSubmissionResponse(jobId, task.getId(), JobStatus.QUEUED);
	}

	private ExecutionJob job(int index) {
		return jobs.get("job-" + (index + 1));
	}

	private void succeed(ExecutionJob job, boolean withArtifact) {
		job.markSucceeded(result(true, withArtifact), "record-" + job.getId());
	}

	private ExecutionResult result(boolean success, boolean withArtifact) {
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(success);
		if (withArtifact) {
			ExecutionArtifact artifact = new ExecutionArtifact();
			artifact.setType("text");
			artifact.setName("result");
			artifact.setMediaType("text/plain");
			result.setArtifacts(List.of(artifact));
		}
		return result;
	}

	private List<String> submittedStepIds() {
		return submissions.stream().map(task -> (String) task.getMetadata().get("stepId")).toList();
	}

	private Plan plan(List<PlanStep> steps, List<Dependency> dependencies) {
		return new Plan("plan-1", 1, "Execute plan", PlanStatus.DRAFT, steps, dependencies,
			snapshot(), NOW);
	}

	private PlanStep step(String id, boolean expectedArtifact) {
		List<ExpectedArtifact> artifacts = expectedArtifact
			? List.of(new ExpectedArtifact("text", "result", "text/plain", true, 1))
			: List.of();
		return new PlanStep(id, id, "Execute " + id, StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding"), List.of()), null, null,
			Map.of("input", id), artifacts, new RetryPolicy(1, Duration.ZERO, List.of()),
			FailurePolicy.STOP_PLAN, false);
	}

	private PlanStep replanStep(String id) {
		return new PlanStep(id, id, "Execute " + id, StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding"), List.of()), null, null,
			Map.of("input", id), List.of(), RetryPolicy.noRetry(),
			FailurePolicy.REQUEST_REPLAN, false);
	}

	private PlanSnapshot snapshot() {
		return new PlanSnapshot(List.of(new PlanSnapshot.AgentSnapshot("coder", "codex",
			List.of("coding"), "workspace-write", true)), Set.of("coding"), List.of(),
			Set.of("codex"), "policy-v1", Map.of());
	}
}
