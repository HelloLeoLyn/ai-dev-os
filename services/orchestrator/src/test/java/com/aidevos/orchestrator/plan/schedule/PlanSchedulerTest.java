package com.aidevos.orchestrator.plan.schedule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.net.http.HttpClient;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.execution.ExecutionRecordRepository;
import com.aidevos.orchestrator.execution.FailureClass;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.execution.tool.DeterministicTool;
import com.aidevos.orchestrator.execution.tool.ToolExecutionResult;
import com.aidevos.orchestrator.execution.tool.ToolExecutionService;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.human.HumanApproval;
import com.aidevos.orchestrator.human.HumanApprovalRepository;
import com.aidevos.orchestrator.human.HumanApprovalStatus;
import com.aidevos.orchestrator.human.InMemoryHumanApprovalRepository;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.JobSubmissionResponse;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.ArtifactReference;
import com.aidevos.orchestrator.plan.Dependency;
import com.aidevos.orchestrator.plan.ExpectedArtifact;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.RetryPolicy;
import com.aidevos.orchestrator.plan.StepExecutionType;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunStatus;
import com.aidevos.orchestrator.plan.run.InMemoryPlanRunRepository;
import com.aidevos.orchestrator.plan.run.StepRunStatus;
import com.aidevos.orchestrator.planner.replan.ReplanRequestService;
import com.aidevos.orchestrator.planner.replan.ReplanRequest;
import com.aidevos.orchestrator.planner.replan.ReplanRequestStore;
import com.aidevos.orchestrator.planner.replan.FailureClassifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PlanSchedulerTest {

	private static final Instant NOW = Instant.parse("2026-08-02T06:00:00Z");

	private final Map<String, ExecutionJob> jobs = new LinkedHashMap<>();
	private final List<TaskDefinition> submissions = new ArrayList<>();
	private final List<String> submittedJobIds = new ArrayList<>();
	private JobService jobService;
	private PlanApprovalService approvalService;
	private ReplanRequestService replanRequestService;
	private ReplanRequestStore replanRequestStore;
	private PlanScheduler scheduler;
	private PlanApprovalRequest currentApproval;

	@BeforeEach
	void setUp() {
		jobService = mock(JobService.class);
		approvalService = mock(PlanApprovalService.class);
		replanRequestStore = new ReplanRequestStore();
		replanRequestService = new ReplanRequestService(replanRequestStore,
			new FailureClassifier(), Clock.fixed(NOW, ZoneOffset.UTC));
		when(jobService.submit(any(), any())).thenAnswer(invocation ->
			submit(invocation.getArgument(0), invocation.getArgument(1)));
		when(jobService.get(any())).thenAnswer(invocation -> jobs.get(invocation.getArgument(0)));
		when(approvalService.consume(any())).thenAnswer(invocation -> {
			currentApproval.consume();
			return currentApproval;
		});
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
	void shouldPassOnlyExplicitlyReferencedArtifactsToDependentStep() {
		PlanStep source = new PlanStep("source", "Source", "Produce evidence",
			StepStatus.PLANNED, new AgentAssignment("coder", List.of("coding"), List.of()),
			Map.of(), List.of(), null, null, Map.of(),
			List.of(new ExpectedArtifact("text", "result", "text/plain", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);
		PlanStep consumer = new PlanStep("consumer", "Consumer", "Use evidence",
			StepStatus.PLANNED, new AgentAssignment("coder", List.of("coding"), List.of()),
			Map.of("mode", "focused"),
			List.of(new ArtifactReference("source", "text", "result", "evidence", true)),
			null, null, Map.of(), List.of(), RetryPolicy.noRetry(),
			FailurePolicy.STOP_PLAN, false);
		Plan plan = plan(List.of(source, consumer),
			List.of(new Dependency("source", "consumer", true)));
		approve(plan);

		scheduler.start("approval-1");
		ExecutionResult sourceResult = result(true, true);
		sourceResult.getArtifacts().getFirst().setContent("explicit evidence");
		job(0).markSucceeded(sourceResult, "record-source");
		scheduler.reconcile();

		Map<?, ?> inputs = (Map<?, ?>) submissions.get(1).getParameters().get("inputs");
		Map<?, ?> evidence = (Map<?, ?>) inputs.get("evidence");
		assertEquals("explicit evidence", evidence.get("content"));
		assertEquals("record-source", evidence.get("executionRecordId"));
		assertEquals("focused", submissions.get(1).getParameters().get("mode"));
		assertEquals("request-1", submissions.get(1).getParameters().get("originalTaskId"));
		assertEquals("", submissions.get(1).getParameters().get("projectId"));
		assertEquals("", submissions.get(1).getParameters().get("workspaceId"));
		assertEquals("", submissions.get(1).getParameters().get("workspacePath"));
		assertEquals("", submissions.get(1).getParameters().get("executionMode"));
		assertEquals(Set.of("mode", "inputs", "originalTaskId", "projectId", "workspaceId",
			"workspacePath", "executionMode"), submissions.get(1).getParameters().keySet());
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
	void codexGitDiffShouldSatisfyCoderArtifactContract() {
		PlanStep coder = new PlanStep("code", "Implement", "Write code", StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding", "git"), List.of()), Map.of(),
			List.of(), null, null, Map.of(),
			List.of(new ExpectedArtifact("git-diff", "changes.patch", "text/plain", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);
		approve(plan(List.of(coder), List.of()));
		PlanRun run = scheduler.start("approval-1");
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		ExecutionArtifact patch = new ExecutionArtifact();
		patch.setType("git-diff");
		patch.setName("changes.patch");
		patch.setMediaType("text/plain");
		result.setArtifacts(List.of(patch));
		job(0).markSucceeded(result, "record-codex");

		scheduler.reconcile();

		assertEquals(PlanRunStatus.SUCCESS, run.getStatus());
		assertEquals(StepRunStatus.SUCCESS, run.getSteps().getFirst().getStatus());
	}

	@Test
	void requiredWorkspaceChangeRejectsEmptyPatchEvenWhenArtifactExists() {
		PlanStep coder = new PlanStep("code", "Implement", "Write code", StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding", "git"), List.of()), Map.of(),
			List.of(), null, null, Map.of(),
			List.of(new ExpectedArtifact("git-diff", "changes.patch", "text/plain", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false, true);
		approve(plan(List.of(coder), List.of()));
		PlanRun run = scheduler.start("approval-1");
		ExecutionResult result = new ExecutionResult();
		result.setSuccess(true);
		ExecutionArtifact patch = new ExecutionArtifact();
		patch.setType("git-diff"); patch.setName("changes.patch"); patch.setMediaType("text/plain");
		patch.setContent(""); result.setArtifacts(List.of(patch));
		job(0).markSucceeded(result, "record-empty");

		scheduler.reconcile();

		assertEquals(PlanRunStatus.FAILED, run.getStatus());
		assertEquals("EXPECTED_WORKSPACE_CHANGE_NOT_FOUND: required workspace change was not produced",
			run.getError());
	}

	@Test
	void requiredWorkspaceChangeAcceptsTrackedPatch() {
		PlanStep coder = new PlanStep("code", "Implement", "Write code", StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding", "git"), List.of()), Map.of(),
			List.of(), null, null, Map.of(), List.of(), RetryPolicy.noRetry(),
			FailurePolicy.STOP_PLAN, false, true);
		approve(plan(List.of(coder), List.of()));
		PlanRun run = scheduler.start("approval-1");
		ExecutionResult result = new ExecutionResult(); result.setSuccess(true);
		ExecutionArtifact patch = new ExecutionArtifact(); patch.setType("git-diff");
		patch.setName("changes.patch"); patch.setMediaType("text/plain"); patch.setContent("diff --git a/A b/A\n");
		result.setArtifacts(List.of(patch)); job(0).markSucceeded(result, "record-patch");

		scheduler.reconcile();

		assertEquals(PlanRunStatus.SUCCESS, run.getStatus());
	}

	@Test
	void requiredWorkspaceChangeAcceptsUntrackedFileOnly() {
		PlanStep coder = new PlanStep("code", "Implement", "Write code", StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding", "git"), List.of()), Map.of(),
			List.of(), null, null, Map.of(), List.of(), RetryPolicy.noRetry(),
			FailurePolicy.STOP_PLAN, false, true);
		approve(plan(List.of(coder), List.of()));
		PlanRun run = scheduler.start("approval-1");
		ExecutionResult result = new ExecutionResult(); result.setSuccess(true);
		ExecutionArtifact untracked = new ExecutionArtifact(); untracked.setType("git-untracked-files");
		untracked.setName("untracked-files.txt"); untracked.getMetadata().put("count", 1);
		result.setArtifacts(List.of(untracked)); job(0).markSucceeded(result, "record-untracked");

		scheduler.reconcile();

		assertEquals(PlanRunStatus.SUCCESS, run.getStatus());
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
		assertEquals(ApprovalStatus.CONSUMED, currentApproval.getStatus());
		assertEquals("approval-1", run.getApprovalId());
		assertEquals(1, submissions.size());
	}

	@Test
	void sameApprovalCannotStartASecondPlanRun() {
		approve(plan(List.of(step("one", false)), List.of()));

		PlanRun first = scheduler.start("approval-1");

		assertThrows(IllegalStateException.class, () -> scheduler.start("approval-1"));
		assertEquals(1, scheduler.getAll().size());
		assertEquals(first.getId(), scheduler.getAll().getFirst().getId());
		assertEquals(1, submissions.size());
	}

	@Test
	void unapprovedPlanMustBeRejected() {
		PlanApprovalRequest pending = approval(plan(List.of(step("one", false)), List.of()));
		currentApproval = pending;
		when(approvalService.get("approval-1")).thenReturn(pending);

		assertThrows(IllegalStateException.class, () -> scheduler.start("approval-1"));
		assertEquals(0, submissions.size());
		verify(approvalService, never()).consume("approval-1");
	}

	@Test
	void rejectedPlanMustBeRejected() {
		PlanApprovalRequest rejected = approval(plan(List.of(step("one", false)), List.of()));
		rejected.reject("reviewer", "unsafe", NOW);
		currentApproval = rejected;
		when(approvalService.get("approval-1")).thenReturn(rejected);

		assertThrows(IllegalStateException.class, () -> scheduler.start("approval-1"));
		assertEquals(0, submissions.size());
		verify(approvalService, never()).consume("approval-1");
	}

	@Test
	void consumedPlanMustBeRejected() {
		approve(plan(List.of(step("one", false)), List.of()));
		currentApproval.consume();

		assertThrows(IllegalStateException.class, () -> scheduler.start("approval-1"));
		assertEquals(0, submissions.size());
	}

	@Test
	void planRunCreationFailureMustLeaveApprovalApproved() {
		PlanApprovalRequest broken = mock(PlanApprovalRequest.class);
		when(broken.getStatus()).thenReturn(ApprovalStatus.APPROVED);
		when(broken.getPlan()).thenThrow(new IllegalStateException("plan unavailable"));
		when(approvalService.get("approval-1")).thenReturn(broken);

		assertThrows(IllegalStateException.class, () -> scheduler.start("approval-1"));

		assertEquals(ApprovalStatus.APPROVED, broken.getStatus());
		assertEquals(0, scheduler.getAll().size());
		verify(approvalService, never()).consume("approval-1");
	}

	@Test
	void firstJobSubmissionFailureMustLeaveConsumedApprovalAndFailedRun() {
		approve(plan(List.of(step("one", false)), List.of()));
		doThrow(new IllegalStateException("queue unavailable")).when(jobService)
			.submit(any(), any());

		PlanRun run = scheduler.start("approval-1");

		assertEquals(ApprovalStatus.CONSUMED, currentApproval.getStatus());
		assertEquals(PlanRunStatus.FAILED, run.getStatus());
		assertEquals("queue unavailable", run.getError());
		assertEquals(StepRunStatus.FAILED, run.getSteps().getFirst().getStatus());
		assertEquals(run.getId(), scheduler.getAll().getFirst().getId());
		assertThrows(IllegalStateException.class, () -> scheduler.start("approval-1"));
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

	@Test
	void toolStepRunsDeterministicallyWithoutJob() {
		ToolExecutionService toolService = mock(ToolExecutionService.class);
		ExecutionRecordRepository recordRepository = mock(ExecutionRecordRepository.class);
		scheduler.setToolExecutionService(toolService);
		scheduler.setExecutionRecordRepository(recordRepository);
		when(toolService.execute(any())).thenReturn(new ToolExecutionResult(
			DeterministicTool.GIT, true, 0, "On branch main", null, 5, null));
		PlanStep toolStep = step("tool", false).withExecutionType(StepExecutionType.TOOL_STEP);
		approve(plan(List.of(toolStep), List.of()));

		PlanRun run = scheduler.start("approval-1");
		scheduler.reconcile();

		assertEquals(PlanRunStatus.SUCCESS, run.getStatus());
		assertEquals(StepRunStatus.SUCCESS, run.getSteps().getFirst().getStatus());
		assertEquals(0, submissions.size());
		verify(toolService, times(1)).execute(any());
		org.mockito.ArgumentCaptor<ExecutionRecord> captor =
			org.mockito.ArgumentCaptor.forClass(ExecutionRecord.class);
		verify(recordRepository).save(captor.capture());
		assertEquals("deterministic", captor.getValue().getExecutorName());
		assertEquals("TOOL_STEP", captor.getValue().getExecutionType());
		assertEquals("FAST", captor.getValue().getValidationProfile());
		assertEquals("SUCCESS", captor.getValue().getStatus());
	}

	@Test
	void systemStepRunsDeterministicallyWithoutJob() {
		ToolExecutionService toolService = mock(ToolExecutionService.class);
		scheduler.setToolExecutionService(toolService);
		when(toolService.execute(any())).thenReturn(new ToolExecutionResult(
			DeterministicTool.VALIDATION, true, 0, "clean", null, 2, null));
		PlanStep systemStep = step("system", false).withExecutionType(StepExecutionType.SYSTEM_STEP);
		approve(plan(List.of(systemStep), List.of()));

		PlanRun run = scheduler.start("approval-1");
		scheduler.reconcile();

		assertEquals(PlanRunStatus.SUCCESS, run.getStatus());
		assertEquals(StepRunStatus.SUCCESS, run.getSteps().getFirst().getStatus());
		assertEquals(0, submissions.size());
	}

	@Test
	void toolStepRetriesUpToBudgetBeforeFailing() {
		ToolExecutionService toolService = mock(ToolExecutionService.class);
		scheduler.setToolExecutionService(toolService);
		when(toolService.execute(any())).thenReturn(new ToolExecutionResult(
			DeterministicTool.GIT, false, 1, null, "fatal: something failed", 3,
			FailureClass.EXECUTOR_FAILED));
		PlanStep toolStep = step("tool", false).withExecutionType(StepExecutionType.TOOL_STEP);
		approve(plan(List.of(toolStep), List.of()));

		PlanRun run = scheduler.start("approval-1");

		assertEquals(PlanRunStatus.FAILED, run.getStatus());
		assertEquals(StepRunStatus.FAILED, run.getSteps().getFirst().getStatus());
		assertTrue(run.getError().contains("Tool step failed"));
		verify(toolService, times(2)).execute(any());
		assertEquals(0, submissions.size());
	}

	@Test
	void toolStepPermanentFailureDoesNotRetry() {
		ToolExecutionService toolService = mock(ToolExecutionService.class);
		scheduler.setToolExecutionService(toolService);
		when(toolService.execute(any())).thenReturn(new ToolExecutionResult(
			DeterministicTool.GIT, false, 1, null, "authentication failed", 3,
			FailureClass.CREDENTIAL_MISSING));
		PlanStep toolStep = step("tool", false).withExecutionType(StepExecutionType.TOOL_STEP);
		approve(plan(List.of(toolStep), List.of()));

		PlanRun run = scheduler.start("approval-1");

		assertEquals(PlanRunStatus.FAILED, run.getStatus());
		verify(toolService, times(1)).execute(any());
		assertEquals(0, submissions.size());
	}

	@Test
	void toolStepTransientFailureRetriesUpToBudget() {
		ToolExecutionService toolService = mock(ToolExecutionService.class);
		scheduler.setToolExecutionService(toolService);
		when(toolService.execute(any())).thenReturn(new ToolExecutionResult(
			DeterministicTool.GIT, false, 1, null, "connection refused", 3,
			FailureClass.NETWORK_ERROR));
		PlanStep toolStep = step("tool", false).withExecutionType(StepExecutionType.TOOL_STEP);
		approve(plan(List.of(toolStep), List.of()));

		PlanRun run = scheduler.start("approval-1");

		assertEquals(PlanRunStatus.FAILED, run.getStatus());
		verify(toolService, times(2)).execute(any());
		assertEquals(0, submissions.size());
	}

	@Test
	void efficiencySmokeToolStepShellEchoSucceeds() {
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setExitCode(0);
		commandResult.setOutput("EXECUTION_EFFICIENCY_OK");
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);
		ExecutionRecordRepository recordRepository = mock(ExecutionRecordRepository.class);
		scheduler.setToolExecutionService(new ToolExecutionService(commandExecutor,
			new com.aidevos.orchestrator.execution.FailureClassifier(), mock(HttpClient.class)));
		scheduler.setExecutionRecordRepository(recordRepository);
		PlanStep shellStep = new PlanStep("shell", "Smoke", "Echo marker", StepStatus.PLANNED,
			new AgentAssignment("deterministic", List.of(), List.of()),
			"shell", null, Map.of("command", "echo EXECUTION_EFFICIENCY_OK"), List.of(),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false)
			.withExecutionType(StepExecutionType.TOOL_STEP);
		approve(plan(List.of(shellStep), List.of()));

		PlanRun run = scheduler.start("approval-1");
		scheduler.reconcile();

		assertEquals(StepRunStatus.SUCCESS, run.getSteps().getFirst().getStatus());
		assertEquals(PlanRunStatus.SUCCESS, run.getStatus());
		assertEquals(0, submissions.size());
		verify(jobService, never()).submit(any(), any());
		org.mockito.ArgumentCaptor<CommandOptions> commandCaptor =
			org.mockito.ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor, times(1)).execute(commandCaptor.capture());
		assertEquals(List.of("sh", "-c", "echo EXECUTION_EFFICIENCY_OK"),
			commandCaptor.getValue().getCommand());
		org.mockito.ArgumentCaptor<ExecutionRecord> recordCaptor =
			org.mockito.ArgumentCaptor.forClass(ExecutionRecord.class);
		verify(recordRepository).save(recordCaptor.capture());
		ExecutionRecord record = recordCaptor.getValue();
		assertEquals("deterministic", record.getExecutorName());
		assertEquals("TOOL_STEP", record.getExecutionType());
		assertEquals("FAST", record.getValidationProfile());
		assertEquals("SUCCESS", record.getStatus());
		assertTrue(record.getOutput().contains("EXECUTION_EFFICIENCY_OK"));
	}

	@Test
	void toolStepWithoutExecutorFailsClosed() {
		PlanStep toolStep = step("tool", false).withExecutionType(StepExecutionType.TOOL_STEP);
		approve(plan(List.of(toolStep), List.of()));

		PlanRun run = scheduler.start("approval-1");

		assertEquals(PlanRunStatus.FAILED, run.getStatus());
		assertEquals(StepRunStatus.FAILED, run.getSteps().getFirst().getStatus());
		assertTrue(run.getError().contains("Deterministic tool execution service unavailable"));
		assertEquals(0, submissions.size());
	}

	@Test
	void humanGateStepPausesWithoutJobOrToolCall() {
		PlanStep gate = step("gate", false).withExecutionType(StepExecutionType.HUMAN_GATE);
		approve(plan(List.of(gate), List.of()));

		PlanRun run = scheduler.start("approval-1");

		assertEquals(PlanRunStatus.WAITING_APPROVAL, run.getStatus());
		assertEquals(StepRunStatus.WAITING_APPROVAL, run.getSteps().getFirst().getStatus());
		assertEquals(0, submissions.size());

		scheduler.reconcile();

		assertEquals(PlanRunStatus.WAITING_APPROVAL, run.getStatus());
		assertEquals(0, submissions.size());
	}

	@Test
	void humanGateApproveResumesAndContinuesNextStep() {
		HumanApprovalRepository approvals = new InMemoryHumanApprovalRepository();
		scheduler.setHumanApprovalRepository(approvals);
		PlanStep gate = step("gate", false).withExecutionType(StepExecutionType.HUMAN_GATE);
		Plan plan = plan(List.of(gate, step("after", false)),
			List.of(new Dependency("gate", "after", true)));
		approve(plan);

		PlanRun run = scheduler.start("approval-1");

		assertEquals(PlanRunStatus.WAITING_APPROVAL, run.getStatus());
		assertEquals(StepRunStatus.WAITING_APPROVAL, run.getSteps().getFirst().getStatus());
		assertEquals(0, submissions.size());
		assertEquals(HumanApprovalStatus.PENDING, approvals.list().getFirst().getStatus());

		scheduler.approveHumanGate(run.getId(), "gate", "reviewer", "approved");
		scheduler.reconcile();

		assertEquals(StepRunStatus.SUCCESS, run.getSteps().get(0).getStatus());
		assertEquals(StepRunStatus.RUNNING, run.getSteps().get(1).getStatus());
		assertEquals(1, submissions.size());
		assertEquals(PlanRunStatus.RUNNING, run.getStatus());

		succeed(job(0), false);
		scheduler.reconcile();

		assertEquals(PlanRunStatus.SUCCESS, run.getStatus());
		assertEquals(StepRunStatus.SUCCESS, run.getSteps().get(1).getStatus());
	}

	@Test
	void humanGateRejectTerminatesStepAndRun() {
		HumanApprovalRepository approvals = new InMemoryHumanApprovalRepository();
		scheduler.setHumanApprovalRepository(approvals);
		PlanStep gate = step("gate", false).withExecutionType(StepExecutionType.HUMAN_GATE);
		Plan plan = plan(List.of(gate, step("after", false)),
			List.of(new Dependency("gate", "after", true)));
		approve(plan);

		PlanRun run = scheduler.start("approval-1");
		scheduler.rejectHumanGate(run.getId(), "gate", "reviewer", "not now");
		scheduler.reconcile();

		assertEquals(StepRunStatus.FAILED, run.getSteps().get(0).getStatus());
		assertEquals(PlanRunStatus.FAILED, run.getStatus());
		assertTrue(run.getError().contains("Human gate rejected by reviewer"));
		assertEquals(0, submissions.size());
	}

	@Test
	void humanGateApproveIsIdempotentAndRejectAfterApproveConflicts() {
		HumanApprovalRepository approvals = new InMemoryHumanApprovalRepository();
		scheduler.setHumanApprovalRepository(approvals);
		PlanStep gate = step("gate", false).withExecutionType(StepExecutionType.HUMAN_GATE);
		approve(plan(List.of(gate), List.of()));

		PlanRun run = scheduler.start("approval-1");
		HumanApproval first = scheduler.approveHumanGate(run.getId(), "gate", "reviewer", "ok");
		HumanApproval second = scheduler.approveHumanGate(run.getId(), "gate", "reviewer", "again");

		assertEquals(HumanApprovalStatus.APPROVED, first.getStatus());
		assertEquals(first, second);
		assertEquals(1, approvals.list().size());
		assertThrows(IllegalStateException.class,
			() -> scheduler.rejectHumanGate(run.getId(), "gate", "reviewer", "no"));
	}

	@Test
	void humanGateRejectIsIdempotentAndApproveAfterRejectConflicts() {
		HumanApprovalRepository approvals = new InMemoryHumanApprovalRepository();
		scheduler.setHumanApprovalRepository(approvals);
		PlanStep gate = step("gate", false).withExecutionType(StepExecutionType.HUMAN_GATE);
		approve(plan(List.of(gate), List.of()));

		PlanRun run = scheduler.start("approval-1");
		HumanApproval first = scheduler.rejectHumanGate(run.getId(), "gate", "reviewer", "no");
		HumanApproval second = scheduler.rejectHumanGate(run.getId(), "gate", "reviewer", "no again");

		assertEquals(HumanApprovalStatus.REJECTED, first.getStatus());
		assertEquals(first, second);
		assertEquals(1, approvals.list().size());
		assertThrows(IllegalStateException.class,
			() -> scheduler.approveHumanGate(run.getId(), "gate", "reviewer", "yes"));
	}

	@Test
	void humanGateDecisionRequiresPausedGate() {
		HumanApprovalRepository approvals = new InMemoryHumanApprovalRepository();
		scheduler.setHumanApprovalRepository(approvals);
		PlanStep aiStep = step("ai", false);
		approve(plan(List.of(aiStep), List.of()));

		PlanRun run = scheduler.start("approval-1");

		assertThrows(IllegalStateException.class,
			() -> scheduler.approveHumanGate(run.getId(), "ai", "reviewer", "yes"));
		assertThrows(IllegalArgumentException.class,
			() -> scheduler.approveHumanGate("missing-run", "gate", "reviewer", "yes"));
	}

	@Test
	void humanGateApprovedDecisionSurvivesSchedulerRestart() {
		InMemoryPlanRunRepository runRepository = new InMemoryPlanRunRepository();
		InMemoryHumanApprovalRepository approvals = new InMemoryHumanApprovalRepository();
		scheduler = new PlanScheduler(jobService, new StepTaskFactory(), approvalService,
			replanRequestService, runRepository, Clock.fixed(NOW, ZoneOffset.UTC));
		scheduler.setHumanApprovalRepository(approvals);
		PlanStep gate = step("gate", false).withExecutionType(StepExecutionType.HUMAN_GATE);
		Plan plan = plan(List.of(gate, step("after", false)),
			List.of(new Dependency("gate", "after", true)));
		approve(plan);

		PlanRun run = scheduler.start("approval-1");
		assertEquals(PlanRunStatus.WAITING_APPROVAL, run.getStatus());
		assertEquals(0, submissions.size());
		scheduler.approveHumanGate(run.getId(), "gate", "reviewer", "approved");

		// Simulated restart: a fresh scheduler shares only the persisted
		// repositories (plan runs + human approvals); all scheduler state is new.
		PlanScheduler restarted = new PlanScheduler(jobService, new StepTaskFactory(),
			approvalService, replanRequestService, runRepository,
			Clock.fixed(NOW, ZoneOffset.UTC));
		restarted.setHumanApprovalRepository(approvals);
		restarted.reconcile();

		assertEquals(StepRunStatus.SUCCESS, run.getSteps().get(0).getStatus());
		assertEquals(StepRunStatus.RUNNING, run.getSteps().get(1).getStatus());
		assertEquals(1, submissions.size());
		assertEquals(PlanRunStatus.RUNNING, run.getStatus());
	}

	@Test
	void taskMetadataCarriesExecutionTypeAndValidationProfile() {
		PlanStep aiStep = step("ai", false);
		approve(plan(List.of(aiStep), List.of()));

		scheduler.start("approval-1");

		TaskDefinition task = submissions.getFirst();
		assertEquals("AI_STEP", task.getMetadata().get("executionType"));
		assertEquals("FAST", task.getMetadata().get("validationProfile"));
		assertEquals(20, task.getMetadata().get("maxAiCalls"));
		assertEquals(1, task.getMetadata().get("maxToolRetries"));
	}

	private void approve(Plan plan) {
		PlanApprovalRequest approval = approval(plan);
		approval.approve("reviewer", NOW);
		currentApproval = approval;
		when(approvalService.get("approval-1")).thenReturn(approval);
	}

	private PlanApprovalRequest approval(Plan plan) {
		return new PlanApprovalRequest("approval-1", "request-1", plan, "hash", NOW);
	}

	private JobSubmissionResponse submit(TaskDefinition task, String jobId) {
		submissions.add(task);
		submittedJobIds.add(jobId);
		jobs.put(jobId, new ExecutionJob(jobId, task));
		return new JobSubmissionResponse(jobId, task.getId(), JobStatus.QUEUED);
	}

	private ExecutionJob job(int index) {
		return jobs.get(submittedJobIds.get(index));
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
