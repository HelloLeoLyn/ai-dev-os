package com.aidevos.orchestrator.plan.schedule;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.execution.ExecutionBudget;
import com.aidevos.orchestrator.execution.ExecutionRecordRepository;
import com.aidevos.orchestrator.execution.FailureClassifier;
import com.aidevos.orchestrator.execution.tool.DeterministicTool;
import com.aidevos.orchestrator.execution.tool.ToolExecutionRequest;
import com.aidevos.orchestrator.execution.tool.ToolExecutionResult;
import com.aidevos.orchestrator.execution.tool.ToolExecutionService;
import com.aidevos.orchestrator.human.HumanApproval;
import com.aidevos.orchestrator.human.HumanApprovalRepository;
import com.aidevos.orchestrator.human.HumanApprovalStatus;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.job.ExecutionJob;
import com.aidevos.orchestrator.job.JobService;
import com.aidevos.orchestrator.job.JobStatus;
import com.aidevos.orchestrator.job.JobSubmissionResponse;
import com.aidevos.orchestrator.model.TaskDefinition;
import com.aidevos.orchestrator.model.ExecutionRecord;
import com.aidevos.orchestrator.plan.Dependency;
import com.aidevos.orchestrator.plan.ArtifactReference;
import com.aidevos.orchestrator.plan.ExpectedArtifact;
import com.aidevos.orchestrator.plan.ExecutablePlanGuard;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.StepExecutionType;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.run.PlanRun;
import com.aidevos.orchestrator.plan.run.PlanRunStatus;
import com.aidevos.orchestrator.plan.run.StepAttempt;
import com.aidevos.orchestrator.plan.run.StepRun;
import com.aidevos.orchestrator.plan.run.StepRunStatus;
import com.aidevos.orchestrator.plan.run.PlanRunRepository;
import com.aidevos.orchestrator.plan.run.InMemoryPlanRunRepository;
import com.aidevos.orchestrator.planner.replan.ReplanRequestService;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class PlanScheduler {

	private static final long POLL_MILLIS = 25;
	private static final Duration COORDINATOR_LEASE = Duration.ofSeconds(30);

	private final JobService jobService;
	private final StepTaskFactory taskFactory;
	private final PlanApprovalService approvalService;
	private final ReplanRequestService replanRequestService;
	private final Clock clock;
	private final PlanRunRepository runRepository;
	private final AuditService auditService;
	private final String coordinatorId = "scheduler-" + UUID.randomUUID();
	private volatile ToolExecutionService toolExecutionService;
	private volatile ExecutionRecordRepository executionRecordRepository;
	private volatile HumanApprovalRepository humanApprovalRepository;
	private final ScheduledExecutorService monitor = Executors.newSingleThreadScheduledExecutor(
		Thread.ofPlatform().daemon().name("plan-run-monitor").factory());

	@Autowired(required = false)
	public void setToolExecutionService(ToolExecutionService service) {
		this.toolExecutionService = service;
	}

	@Autowired(required = false)
	public void setExecutionRecordRepository(ExecutionRecordRepository repository) {
		this.executionRecordRepository = repository;
	}

	@Autowired(required = false)
	public void setHumanApprovalRepository(HumanApprovalRepository repository) {
		this.humanApprovalRepository = repository;
	}

	public PlanScheduler(JobService jobService, StepTaskFactory taskFactory,
			PlanApprovalService approvalService, ReplanRequestService replanRequestService) {
		this(jobService, taskFactory, approvalService, replanRequestService,
			new InMemoryPlanRunRepository(), Clock.systemUTC(), AuditService.noop());
	}

	@Autowired
	public PlanScheduler(JobService jobService, StepTaskFactory taskFactory,
			PlanApprovalService approvalService, ReplanRequestService replanRequestService,
			PlanRunRepository runRepository, AuditService auditService) {
		this(jobService, taskFactory, approvalService, replanRequestService,
			runRepository, Clock.systemUTC(), auditService);
	}

	PlanScheduler(JobService jobService, StepTaskFactory taskFactory,
			PlanApprovalService approvalService, ReplanRequestService replanRequestService,
			Clock clock) {
		this(jobService, taskFactory, approvalService, replanRequestService,
			new InMemoryPlanRunRepository(), clock, AuditService.noop());
	}

	PlanScheduler(JobService jobService, StepTaskFactory taskFactory,
			PlanApprovalService approvalService, ReplanRequestService replanRequestService,
			PlanRunRepository runRepository, Clock clock) {
		this(jobService, taskFactory, approvalService, replanRequestService, runRepository, clock,
			AuditService.noop());
	}

	PlanScheduler(JobService jobService, StepTaskFactory taskFactory,
			PlanApprovalService approvalService, ReplanRequestService replanRequestService,
			PlanRunRepository runRepository, Clock clock, AuditService auditService) {
		this.jobService = jobService;
		this.taskFactory = taskFactory;
		this.approvalService = approvalService;
		this.replanRequestService = replanRequestService;
		this.clock = clock;
		this.runRepository = runRepository;
		this.auditService = auditService;
	}

	@PostConstruct
	void startMonitor() {
		monitor.scheduleWithFixedDelay(this::safeReconcile, POLL_MILLIS, POLL_MILLIS,
			TimeUnit.MILLISECONDS);
	}

	public synchronized PlanRun start(String approvalId) {
		PlanApprovalRequest approval = approvalService.get(approvalId);
		if (approval == null) {
			throw new IllegalArgumentException("Plan approval not found: " + approvalId);
		}
		if (runRepository.findRunIdByApproval(approvalId) != null) {
			throw new IllegalStateException("Plan approval has already started a run");
		}
		if (approval.getStatus() == ApprovalStatus.CONSUMED) {
			throw new IllegalStateException("Plan approval has already been consumed");
		}
		if (approval.getStatus() != ApprovalStatus.APPROVED) {
			throw new IllegalStateException("Plan must be approved before execution");
		}
		Plan plan = approval.getPlan();
		ExecutablePlanGuard.requireExecutable(plan);
		String runId = runId(approvalId);
		List<StepRun> stepRuns = plan.steps().stream()
			.map(step -> new StepRun(stepRunId(runId, step.id()), step.id()))
			.toList();
		PlanRun run = new PlanRun(runId, approvalId, approval.getRequestId(), plan, stepRuns,
			Instant.now(clock));
		PlanRun stored = runRepository.createIfAbsent(approvalId, run);
		if (stored != run) {
			throw new IllegalStateException("Plan approval has already started a run");
		}
		Instant now = Instant.now(clock);
		Optional<PlanRun> claimed = runRepository.claimCoordinator(runId, coordinatorId, now,
			COORDINATOR_LEASE);
		if (claimed.isEmpty()) {
			unregister(approvalId, runId);
			throw new IllegalStateException("Plan run coordinator is held by another instance");
		}
		PlanRun current = claimed.get();
		long token = current.getCoordinatorToken();
		int claimedVersion = current.getVersion();
		boolean consumedApproval = false;
		try {
			PlanApprovalRequest consumedRequest = approvalService.consume(approvalId);
			if (consumedRequest.getStatus() != ApprovalStatus.CONSUMED) {
				throw new IllegalStateException("Plan approval was not consumed");
			}
			consumedApproval = true;
			auditService.planRunCreated(current);
			PlanRunStatus before = current.getStatus();
			current.markRunning(now);
			advance(current);
			runRepository.saveIfUnchanged(current, claimedVersion);
			auditService.planRunTransition(current, before.name(), current.getStatus().name());
		}
		catch (RuntimeException exception) {
			if (!consumedApproval) {
				unregister(approvalId, runId);
			}
			throw exception;
		}
		finally {
			runRepository.releaseCoordinator(runId, coordinatorId, token);
		}
		return current;
	}

	private void unregister(String approvalId, String runId) {
		runRepository.remove(approvalId, runId);
	}

	public PlanRun get(String runId) {
		return runRepository.get(runId);
	}

	public List<PlanRun> getAll() {
		return runRepository.getAll();
	}

	public void reconcile() {
		for (PlanRun run : new ArrayList<>(runRepository.getAll())) {
			reconcile(run);
		}
	}

	private void safeReconcile() {
		try {
			reconcile();
		}
		catch (RuntimeException ignored) {
			// An individual run is transitioned to FAILED by reconcile whenever possible.
		}
	}

	private void reconcile(PlanRun run) {
		if (terminal(run.getStatus())) {
			return;
		}
		Instant now = Instant.now(clock);
		Optional<PlanRun> claimed = runRepository.claimCoordinator(run.getId(), coordinatorId,
			now, COORDINATOR_LEASE);
		if (claimed.isEmpty()) {
			return;
		}
		PlanRun current = claimed.get();
		long token = current.getCoordinatorToken();
		int claimedVersion = current.getVersion();
		try {
			consumeApprovalIfPending(current);
			PlanRunStatus beforeStatus = current.getStatus();
			try {
				StepRun active = activeStep(current);
				if (active == null) {
					advance(current);
					return;
				}
				StepAttempt attempt = active.getCurrentAttempt();
				ExecutionJob job = attempt == null || attempt.getJobId() == null
					? null : jobService.get(attempt.getJobId());
				if (job == null) {
					if (attempt != null) {
				StepExecutionType executionType = step(current.getPlan(),
					active.getStepId()).executionType();
				if (executionType == null || executionType == StepExecutionType.AI_STEP) {
					repairMissingJob(current, active, attempt);
					return;
				}
				if (executionType == StepExecutionType.HUMAN_GATE) {
					completeHumanGateIfDecided(current, active, attempt);
					return;
				}
				if (active.getStatus() == StepRunStatus.RUNNING) {
					// A deterministic step left RUNNING by a crash window is
					// re-run without an LLM; HUMAN_GATE stays paused.
					executeDeterministicStep(current,
								step(current.getPlan(), active.getStepId()), active, attempt);
						}
						return;
					}
					fail(current, active, attempt, "Submitted job not found", null, null, false);
					return;
				}
				if (job.getStatus() == JobStatus.WAITING_APPROVAL) {
					String from = active.getStatus().name();
					attempt.markWaitingApproval();
					active.markWaitingApproval();
					current.markWaitingApproval();
					if (!from.equals(active.getStatus().name())) {
						auditService.stepEvent(EventType.STEP_WAITING_APPROVAL, current, active,
							attempt, from, active.getStatus().name());
					}
					return;
				}
				if (job.getStatus() == JobStatus.QUEUED || job.getStatus() == JobStatus.RUNNING) {
					String from = active.getStatus().name();
					attempt.markRunning();
					active.markRunning();
					current.markRunning(Instant.now(clock));
					if (StepRunStatus.WAITING_APPROVAL.name().equals(from)) {
						auditService.stepEvent(EventType.STEP_RESUMED, current, active, attempt,
							from, active.getStatus().name());
					}
					return;
				}
				if (job.getStatus() == JobStatus.FAILED) {
					fail(current, active, attempt, job.getErrorMessage(),
						job.getExecutionRecordId(), job, false);
					return;
				}
				if (!resultSatisfies(current, active, job.getResult())) {
					String artifactFailure = artifactFailureMessage(current, active, job.getResult());
					String artifactErrorCode = artifactFailure.startsWith("EXPECTED_WORKSPACE_CHANGE_NOT_FOUND")
						? "EXPECTED_WORKSPACE_CHANGE_NOT_FOUND" : "ARTIFACT_CONTRACT_MISMATCH";
					auditService.executionFlow("ARTIFACT_GATE_FAILED", current.getOriginalTaskId(), current.getId(),
						active.getId(), job.getId(), job.getApprovalId(),
						attempt.getId(), job.getExecutionRecordId(), null, null,
						job.getStatus().name(), "FAILED", artifactFailure, artifactErrorCode);
					fail(current, active, attempt, artifactFailure,
						job.getExecutionRecordId(), job, true);
					return;
				}
				auditService.executionFlow("ARTIFACT_GATE_PASSED", current.getOriginalTaskId(), current.getId(),
					active.getId(), job.getId(), job.getApprovalId(), attempt.getId(),
					job.getExecutionRecordId(), null, null, job.getStatus().name(), "SUCCESS",
					"expected artifacts satisfied", null);
				attempt.markSuccess(job.getExecutionRecordId(), now);
				active.markSuccess(now);
				auditService.stepEvent(EventType.STEP_SUCCEEDED, current, active, attempt, "RUNNING",
					active.getStatus().name());
				advance(current);
			}
			finally {
				if (runRepository.saveIfUnchanged(current, claimedVersion)) {
					auditService.planRunTransition(current, beforeStatus.name(),
						current.getStatus().name());
				}
			}
		}
		finally {
			runRepository.releaseCoordinator(current.getId(), coordinatorId, token);
		}
	}

	private void advance(PlanRun run) {
		if (run.getSteps().stream().allMatch(step -> step.getStatus() == StepRunStatus.SUCCESS)) {
			run.markSuccess(Instant.now(clock));
			return;
		}
		StepRun next = run.getSteps().stream()
			.filter(step -> step.getStatus() == StepRunStatus.PENDING)
			.filter(step -> dependenciesSucceeded(run, step.getStepId()))
			.findFirst().orElse(null);
		if (next == null) {
			run.markFailed("No executable step remains", Instant.now(clock));
			return;
		}
		PlanStep definition = step(run.getPlan(), next.getStepId());
		int attemptNumber = next.getAttempts().size() + 1;
		StepAttempt attempt = next.startAttempt(
			attemptId(run.getId(), next.getStepId(), attemptNumber), Instant.now(clock));
		auditService.stepEvent(EventType.STEP_ATTEMPT_STARTED, run, next, attempt, "PENDING",
			next.getStatus().name());
		try {
			if (definition.executionType() == StepExecutionType.TOOL_STEP
					|| definition.executionType() == StepExecutionType.SYSTEM_STEP) {
				executeDeterministicStep(run, definition, next, attempt);
			}
			else if (definition.executionType() == StepExecutionType.HUMAN_GATE) {
				pauseForHumanGate(run, next, attempt);
			}
			else {
				TaskDefinition task = taskFactory.create(run, definition, next, attempt,
					resolveInputs(run, definition));
				JobSubmissionResponse submission = jobService.submit(task, jobId(attempt.getId()));
				attempt.bindJob(submission.jobId());
				auditService.stepEvent(EventType.STEP_JOB_BOUND, run, next, attempt,
					attempt.getStatus().name(), attempt.getStatus().name());
			}
		}
		catch (RuntimeException exception) {
			fail(run, next, attempt, errorMessage(exception), null, null, false);
		}
	}

	/**
	 * Runs a TOOL_STEP / SYSTEM_STEP without an LLM through the deterministic
	 * executor. The tool is retried up to the budget maxToolRetries and the
	 * outcome is persisted as an ExecutionRecord with a FailureClass.
	 */
	private void executeDeterministicStep(PlanRun run, PlanStep definition, StepRun step,
			StepAttempt attempt) {
		if (toolExecutionService == null) {
			throw new IllegalStateException("Deterministic tool execution service unavailable");
		}
		ExecutionBudget budget = ExecutionBudget.resolve(plannerMetadata(run));
		ToolExecutionRequest request = ToolExecutionRequest.of(deterministicTool(definition),
			arguments(definition), workspacePath(run));
		ToolExecutionResult result = null;
		int maxRetries = Math.max(0, budget.maxToolRetries());
		for (int attemptNumber = 0; attemptNumber <= maxRetries; attemptNumber++) {
			result = toolExecutionService.execute(request);
			if (result.success() || !FailureClassifier.isRetryable(result.failureClass())) {
				break;
			}
		}
		ExecutionRecord record = persistToolRecord(run, definition, step, attempt, result, budget);
		String recordId = record == null ? null : record.getId();
		Instant now = Instant.now(clock);
		if (result.success()) {
			attempt.markSuccess(recordId, now);
			step.markSuccess(now);
			auditService.stepEvent(EventType.STEP_SUCCEEDED, run, step, attempt, "RUNNING",
				step.getStatus().name());
		}
		else {
			fail(run, step, attempt, toolFailureMessage(result), recordId, null, false);
		}
	}

	/**
	 * A HUMAN_GATE step pauses the run for external approval without calling
	 * an LLM or submitting a job.
	 */
	private void pauseForHumanGate(PlanRun run, StepRun step, StepAttempt attempt) {
		String from = step.getStatus().name();
		attempt.markWaitingApproval();
		step.markWaitingApproval();
		run.markWaitingApproval();
		ensureHumanGateApproval(run, step);
		if (!from.equals(step.getStatus().name())) {
			auditService.stepEvent(EventType.STEP_WAITING_APPROVAL, run, step, attempt, from,
				step.getStatus().name());
		}
	}

	/**
	 * Creates the PENDING human gate approval for a paused HUMAN_GATE step.
	 * The approval id is deterministic (runId + stepId) so retries and
	 * reconcile ticks never create a duplicate.
	 */
	private void ensureHumanGateApproval(PlanRun run, StepRun step) {
		if (humanApprovalRepository == null) {
			return;
		}
		String approvalId = humanGateApprovalId(run, step);
		if (humanApprovalRepository.get(approvalId) != null) {
			return;
		}
		humanApprovalRepository.save(new HumanApproval(approvalId, run.getOriginalTaskId(),
			null, null, step.getStepId(), HumanApprovalStatus.PENDING, "plan-scheduler",
			null, null, Instant.now(clock), null));
	}

	/**
	 * Applies a human decision to a paused HUMAN_GATE step: APPROVED resumes
	 * the step (and the run) and advances to the next step; REJECTED
	 * terminates the step and the run. Idempotent for repeated calls.
	 */
	private void completeHumanGateIfDecided(PlanRun run, StepRun step, StepAttempt attempt) {
		if (humanApprovalRepository == null || step.getStatus() != StepRunStatus.WAITING_APPROVAL) {
			return;
		}
		HumanApproval approval = humanApprovalRepository.get(humanGateApprovalId(run, step));
		if (approval == null) {
			return;
		}
		Instant now = Instant.now(clock);
		if (approval.getStatus() == HumanApprovalStatus.APPROVED) {
			String from = step.getStatus().name();
			attempt.markSuccess(null, now);
			step.markSuccess(now);
			run.markRunning(now);
			auditService.stepEvent(EventType.STEP_RESUMED, run, step, attempt, from,
				step.getStatus().name());
			auditService.stepEvent(EventType.STEP_SUCCEEDED, run, step, attempt, from,
				step.getStatus().name());
			advance(run);
		}
		else if (approval.getStatus() == HumanApprovalStatus.REJECTED) {
			String message = "Human gate rejected"
				+ (approval.getReviewer() == null || approval.getReviewer().isBlank()
					? "" : " by " + approval.getReviewer());
			fail(run, step, attempt, message, null, null, false);
		}
	}

	/**
	 * Approves a paused HUMAN_GATE step. Repeated approval is idempotent;
	 * approving after a rejection conflicts and fails closed.
	 */
	public HumanApproval approveHumanGate(String runId, String stepId, String reviewer,
			String comment) {
		return decideHumanGate(runId, stepId, reviewer, comment, true);
	}

	/**
	 * Rejects a paused HUMAN_GATE step, terminating the step and the run.
	 * Repeated rejection is idempotent; rejecting after an approval conflicts.
	 */
	public HumanApproval rejectHumanGate(String runId, String stepId, String reviewer,
			String comment) {
		return decideHumanGate(runId, stepId, reviewer, comment, false);
	}

	private HumanApproval decideHumanGate(String runId, String stepId, String reviewer,
			String comment, boolean approve) {
		if (humanApprovalRepository == null) {
			throw new IllegalStateException("Human gate approval service unavailable");
		}
		PlanRun run = runRepository.get(runId);
		if (run == null) {
			throw new IllegalArgumentException("Plan run not found: " + runId);
		}
		StepRun step = run.getSteps().stream().filter(candidate -> candidate.getStepId()
			.equals(stepId)).findFirst().orElseThrow(
				() -> new IllegalArgumentException("Step not found: " + stepId));
		PlanStep definition = step(run.getPlan(), stepId);
		if (definition.executionType() != StepExecutionType.HUMAN_GATE) {
			throw new IllegalStateException("Step is not a HUMAN_GATE: " + stepId);
		}
		HumanApproval approval = humanApprovalRepository.get(humanGateApprovalId(run, step));
		if (approval == null) {
			throw new IllegalStateException("Human gate is not waiting for approval");
		}
		HumanApprovalStatus desired = approve ? HumanApprovalStatus.APPROVED
			: HumanApprovalStatus.REJECTED;
		if (approval.getStatus() == desired) {
			return approval;
		}
		if (approval.getStatus() != HumanApprovalStatus.PENDING) {
			throw new IllegalStateException("Human gate already decided: "
				+ approval.getStatus());
		}
		if (approve) {
			approval.approve(reviewer == null ? "" : reviewer, comment);
		}
		else {
			approval.reject(reviewer == null ? "" : reviewer, comment);
		}
		humanApprovalRepository.save(approval);
		return approval;
	}

	private String humanGateApprovalId(PlanRun run, StepRun step) {
		return "gate-" + run.getId() + ":" + step.getStepId();
	}

	private ExecutionRecord persistToolRecord(PlanRun run, PlanStep definition, StepRun step,
			StepAttempt attempt, ToolExecutionResult result, ExecutionBudget budget) {
		if (executionRecordRepository == null) {
			return null;
		}
		ExecutionRecord record = new ExecutionRecord();
		record.setId("tool-" + UUID.randomUUID());
		record.setExecutionId(record.getId());
		record.setTaskId(run.getOriginalTaskId());
		record.setAgentName(definition.assignment() == null ? null
			: definition.assignment().agentName());
		record.setExecutorName("deterministic");
		record.setOperation(definition.toolName() == null || definition.toolName().isBlank()
			? result.tool().name() : definition.toolName());
		record.setStatus(result.success() ? "SUCCESS" : "FAILED");
		record.setMessage(result.success() ? "Tool step succeeded" : "Tool step failed");
		record.setOutput(result.output());
		record.setExitCode(result.exitCode());
		record.setErrorCode(result.failureClass() == null ? null : result.failureClass().name());
		record.setErrorMessage(result.error());
		record.setPlanRunId(run.getId());
		record.setStepRunId(step.getId());
		record.setAttemptId(attempt.getId());
		record.setWorkspace(workspacePath(run));
		record.setExecutionType(definition.executionType().name());
		record.setValidationProfile(budget.validationProfile().name());
		Instant now = Instant.now(clock);
		record.setStartedAt(now.minusMillis(result.durationMs()));
		record.setCompletedAt(now);
		executionRecordRepository.save(record);
		return record;
	}

	private DeterministicTool deterministicTool(PlanStep definition) {
		String toolName = definition.toolName() == null || definition.toolName().isBlank()
			? "shell" : definition.toolName();
		return switch (toolName.toLowerCase()) {
			case "git" -> DeterministicTool.GIT;
			case "maven", "mvn" -> DeterministicTool.MAVEN;
			case "npm" -> DeterministicTool.NPM;
			case "shell", "sh" -> DeterministicTool.SHELL;
			case "http", "health", "http_health" -> DeterministicTool.HTTP_HEALTH;
			case "workspace" -> DeterministicTool.WORKSPACE;
			case "validation" -> DeterministicTool.VALIDATION;
			default -> throw new IllegalStateException("Unsupported deterministic tool: " + toolName);
		};
	}

	private List<String> arguments(PlanStep definition) {
		Object url = definition.toolArguments().get("url");
		if (url != null) {
			return List.of(String.valueOf(url));
		}
		Object args = definition.toolArguments().get("args");
		if (args instanceof List<?> list) {
			return list.stream().map(String::valueOf).toList();
		}
		Object command = definition.toolArguments().get("command");
		if (command instanceof String text && !text.isBlank()) {
			return List.of(text);
		}
		return List.of();
	}

	private Map<String, Object> plannerMetadata(PlanRun run) {
		if (run.getPlan() == null || run.getPlan().snapshot() == null) {
			return Map.of();
		}
		return run.getPlan().snapshot().plannerMetadata();
	}

	private String workspacePath(PlanRun run) {
		Object path = plannerMetadata(run).get("workspacePath");
		return path == null ? null : String.valueOf(path);
	}

	private String toolFailureMessage(ToolExecutionResult result) {
		String error = result.error();
		if (error == null || error.isBlank()) {
			return "Tool step failed: " + result.tool() + " (exit " + result.exitCode() + ")";
		}
		return "Tool step failed: " + result.tool() + " (exit " + result.exitCode()
			+ "): " + error;
	}

	/**
	 * Recreates the job binding for an attempt that was started but whose job
	 * submission never completed (crash window) or whose job row is missing.
	 * The deterministic job id makes the submission idempotent: a job created
	 * before the crash is reused instead of duplicated.
	 */
	private void repairMissingJob(PlanRun run, StepRun step, StepAttempt attempt) {
		PlanStep definition = step(run.getPlan(), step.getStepId());
		try {
			TaskDefinition task = taskFactory.create(run, definition, step, attempt,
				resolveInputs(run, definition));
			JobSubmissionResponse submission = jobService.submit(task, jobId(attempt.getId()));
			attempt.bindJob(submission.jobId());
			auditService.stepEvent(EventType.STEP_JOB_BOUND, run, step, attempt,
				attempt.getStatus().name(), attempt.getStatus().name());
		}
		catch (RuntimeException exception) {
			fail(run, step, attempt, errorMessage(exception), null, null, false);
		}
	}

	/**
	 * Closes the create-run / consume-approval crash window: when a run exists
	 * for an approval that is still APPROVED, the approval is consumed
	 * atomically by the recovery reconcile.
	 */
	private void consumeApprovalIfPending(PlanRun run) {
		PlanApprovalRequest approval = approvalService.get(run.getApprovalId());
		if (approval != null && approval.getStatus() == ApprovalStatus.APPROVED) {
			approvalService.consume(run.getApprovalId());
		}
	}

	private static String runId(String approvalId) {
		return "run-" + approvalId;
	}

	private static String stepRunId(String runId, String stepId) {
		return runId + ":step:" + stepId;
	}

	private static String attemptId(String runId, String stepId, int attemptNumber) {
		return runId + ":step:" + stepId + ":attempt:" + attemptNumber;
	}

	private static String jobId(String attemptId) {
		return "job-" + attemptId;
	}

	private Map<String, Object> resolveInputs(PlanRun run, PlanStep step) {
		Map<String, Object> inputs = new java.util.LinkedHashMap<>();
		for (ArtifactReference reference : step.inputArtifacts()) {
			StepRun sourceRun = stepRun(run, reference.fromStepId());
			StepAttempt sourceAttempt = sourceRun.getCurrentAttempt();
			ExecutionJob sourceJob = sourceAttempt == null ? null
				: jobService.get(sourceAttempt.getJobId());
			ExecutionArtifact artifact = sourceJob == null || sourceJob.getResult() == null
				? null : sourceJob.getResult().getArtifacts().stream()
					.filter(item -> reference.artifactType().equals(item.getType()))
					.filter(item -> reference.artifactName() == null
						|| reference.artifactName().isBlank()
						|| reference.artifactName().equals(item.getName()))
					.findFirst().orElse(null);
			if (artifact == null) {
				if (reference.required()) {
					throw new IllegalStateException("Required input artifact is unavailable: "
						+ reference.inputKey());
				}
				continue;
			}
			Map<String, Object> value = new java.util.LinkedHashMap<>();
			value.put("fromStepId", reference.fromStepId());
			value.put("executionRecordId", sourceJob.getExecutionRecordId());
			value.put("type", artifact.getType());
			value.put("name", artifact.getName());
			value.put("mediaType", artifact.getMediaType());
			value.put("uri", artifact.getUri());
			value.put("content", artifact.getContent());
			inputs.put(reference.inputKey(), value);
		}
		return inputs;
	}

	private boolean dependenciesSucceeded(PlanRun run, String stepId) {
		return run.getPlan().dependencies().stream()
			.filter(dependency -> dependency.toStepId().equals(stepId))
			.map(Dependency::fromStepId)
			.allMatch(source -> stepRun(run, source).getStatus() == StepRunStatus.SUCCESS);
	}

	private boolean resultSatisfies(PlanRun run, StepRun stepRun, ExecutionResult result) {
		if (result == null || !result.isSuccess()) {
			return false;
		}
		PlanStep step = step(run.getPlan(), stepRun.getStepId());
		if (!artifactsSatisfy(step, result)) return false;
		return !step.requiresWorkspaceChange() || hasWorkspaceChange(result);
	}

	private boolean artifactsSatisfy(PlanStep step, ExecutionResult result) {
		for (ExpectedArtifact expected : step.expectedArtifacts()) {
			long count = result.getArtifacts().stream()
				.filter(artifact -> matches(artifact, expected))
				.count();
			if (count < expected.minimumCount()) {
				return false;
			}
		}
		return true;
	}

	private boolean hasWorkspaceChange(ExecutionResult result) {
		return result.getArtifacts().stream().anyMatch(artifact -> {
			if ("git-diff".equals(artifact.getType())
					&& "changes.patch".equals(artifact.getName())) {
				return artifact.getContent() != null && !artifact.getContent().isBlank();
			}
			if ("git-untracked-files".equals(artifact.getType())) {
				Object count = artifact.getMetadata().get("count");
				return count instanceof Number number && number.longValue() > 0;
			}
			return "git-untracked-file".equals(artifact.getType());
		});
	}

	private String artifactFailureMessage(PlanRun run, StepRun step, ExecutionResult result) {
		PlanStep definition = step(run.getPlan(), step.getStepId());
		if (result != null && artifactsSatisfy(definition, result)
				&& definition.requiresWorkspaceChange() && !hasWorkspaceChange(result)) {
			return "EXPECTED_WORKSPACE_CHANGE_NOT_FOUND: required workspace change was not produced";
		}
		return "Expected artifact requirements were not satisfied";
	}

	private boolean matches(ExecutionArtifact artifact, ExpectedArtifact expected) {
		return same(expected.type(), artifact.getType())
			&& optionalSame(expected.name(), artifact.getName())
			&& optionalSame(expected.mediaType(), artifact.getMediaType());
	}

	private boolean same(String expected, String actual) {
		return expected != null && expected.equals(actual);
	}

	private boolean optionalSame(String expected, String actual) {
		return expected == null || expected.isBlank() || expected.equals(actual);
	}

	private void fail(PlanRun run, StepRun step, StepAttempt attempt, String error,
			String recordId, ExecutionJob job, boolean artifactMissing) {
		String message = error == null || error.isBlank() ? "Step execution failed" : error;
		Instant now = Instant.now(clock);
		if (attempt != null) {
			attempt.markFailed(recordId, message, now);
		}
		step.markFailed(message, now);
		auditService.stepEvent(EventType.STEP_FAILED, run, step, attempt, "RUNNING",
			step.getStatus().name());
		PlanStep definition = step(run.getPlan(), step.getStepId());
		if (definition.failurePolicy() == FailurePolicy.REQUEST_REPLAN) {
			replanRequestService.create(run, step, job, message, artifactMissing);
			run.markReplanRequired(message, now);
		}
		else {
			run.markFailed(message, now);
		}
	}

	private StepRun activeStep(PlanRun run) {
		return run.getSteps().stream()
			.filter(step -> step.getStatus() == StepRunStatus.RUNNING
				|| step.getStatus() == StepRunStatus.WAITING_APPROVAL)
			.findFirst().orElse(null);
	}

	private StepRun stepRun(PlanRun run, String stepId) {
		return run.getSteps().stream().filter(step -> step.getStepId().equals(stepId))
			.findFirst().orElseThrow();
	}

	private PlanStep step(Plan plan, String stepId) {
		return plan.steps().stream().filter(step -> step.id().equals(stepId))
			.findFirst().orElseThrow();
	}

	private boolean terminal(PlanRunStatus status) {
		return status == PlanRunStatus.SUCCESS || status == PlanRunStatus.FAILED
			|| status == PlanRunStatus.REPLAN_REQUIRED;
	}

	private String errorMessage(RuntimeException exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}

	@PreDestroy
	void stopMonitor() {
		monitor.shutdownNow();
	}
}
