package com.aidevos.orchestrator.executor.codex;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.aidevos.orchestrator.approval.ApprovalStatus;
import com.aidevos.orchestrator.approval.CodingApprovalRequest;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.workspace.WorkspaceResolver;
import com.aidevos.orchestrator.execution.workspace.WorkspaceSnapshot;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.git.GitInspector;
import com.aidevos.orchestrator.executor.git.GitSnapshot;
import com.aidevos.orchestrator.executor.git.UntrackedArtifactCollector;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanStatus;
import com.aidevos.orchestrator.plan.approval.PlanApprovalRequest;
import com.aidevos.orchestrator.plan.approval.PlanApprovalService;
import tools.jackson.databind.ObjectMapper;

class CodexApprovalHandoffTest {

	@Test
	void verifiedApprovalsProduceScopedHandoffForCodex() {
		CommandExecutor commands = mock(CommandExecutor.class);
		CommandResult success = new CommandResult(); success.setSuccess(true); success.setExitCode(0);
		when(commands.execute(any(CommandOptions.class))).thenReturn(success);
		PlanApprovalService plans = mock(PlanApprovalService.class);
		CodingApprovalService coding = mock(CodingApprovalService.class);
		when(coding.requireApproval(any(), any(), any())).thenReturn(null);
		when(coding.get("coding-approval")).thenReturn(consumedCodingApproval("task-1", "job-1", "/runtime/task-1"));
		when(plans.get("plan-approval")).thenReturn(consumedPlanApproval());

		ExecutionContext context = context("/runtime/task-1");
		CodexExecutor executor = executor(commands, coding, plans, "/runtime/task-1");
		executor.execute(context);

		var command = org.mockito.ArgumentCaptor.forClass(com.aidevos.orchestrator.executor.command.CommandOptions.class);
		org.mockito.Mockito.verify(commands).execute(command.capture());
		String prompt = command.getValue().getCommand().getLast();
		assertTrue(prompt.contains("approved execution phase"));
		assertTrue(prompt.contains("CODING / WORKSPACE_WRITE"));
	}

	@Test
	void mismatchedWorkspaceFailsClosedBeforeCodexStarts() {
		CommandExecutor commands = mock(CommandExecutor.class);
		CodingApprovalService coding = mock(CodingApprovalService.class);
		when(coding.requireApproval(any(), any(), any())).thenReturn(null);
		when(coding.get("coding-approval")).thenReturn(consumedCodingApproval("task-1", "job-1", "/runtime/task-1"));
		PlanApprovalService plans = mock(PlanApprovalService.class);
		when(plans.get("plan-approval")).thenReturn(consumedPlanApproval());

		CodexExecutor executor = executor(commands, coding, plans, "/runtime/other-task");
		assertThrows(IllegalStateException.class, () -> executor.execute(context("/runtime/other-task")));
		verifyNoInteractions(commands);
	}

	@Test
	void pendingCodingApprovalDoesNotStartCodex() {
		CommandExecutor commands = mock(CommandExecutor.class);
		CodingApprovalService coding = mock(CodingApprovalService.class);
		CodingApprovalRequest pending = new CodingApprovalRequest("coding-approval", "task-1", "job-1",
			"/runtime/task-1", "workspace-write", "approved");
		when(coding.requireApproval(any(), any(), any())).thenReturn(pending);
		CodexExecutor executor = executor(commands, coding, mock(PlanApprovalService.class), "/runtime/task-1");

		executor.execute(context("/runtime/task-1"));

		verifyNoInteractions(commands);
	}

	@Test
	void selfReportedApprovalMetadataCannotCreateHandoff() {
		CommandExecutor commands = mock(CommandExecutor.class);
		CodingApprovalService coding = mock(CodingApprovalService.class);
		when(coding.requireApproval(any(), any(), any())).thenReturn(null);
		when(coding.get("coding-approval")).thenReturn(consumedCodingApproval("task-1", "job-1", "/runtime/task-1"));
		PlanApprovalService plans = mock(PlanApprovalService.class);
		when(plans.get("plan-approval")).thenReturn(consumedPlanApproval());
		ExecutionContext context = context("/runtime/task-1");
		context.getMetadata().remove("planApprovalId");
		context.getMetadata().put("executionPhase", "APPROVED_EXECUTION");

		assertThrows(IllegalStateException.class, () -> executor(commands, coding, plans,
			"/runtime/task-1").execute(context));
		verifyNoInteractions(commands);
	}

	@Test
	void wrongAuthorityIsRejected() {
		CommandExecutor commands = mock(CommandExecutor.class);
		CodingApprovalService coding = mock(CodingApprovalService.class);
		when(coding.requireApproval(any(), any(), any())).thenReturn(null);
		CodingApprovalRequest approval = new CodingApprovalRequest("coding-approval", "task-1", "job-1",
			"/runtime/task-1", "workspace-write", "approved", "DEPLOY", "DEPLOY");
		approval.approve(); approval.consume();
		when(coding.get("coding-approval")).thenReturn(approval);
		PlanApprovalService plans = mock(PlanApprovalService.class);
		when(plans.get("plan-approval")).thenReturn(consumedPlanApproval());

		assertThrows(IllegalStateException.class, () -> executor(commands, coding, plans,
			"/runtime/task-1").execute(context("/runtime/task-1")));
		verifyNoInteractions(commands);
	}

	@Test
	void approvalFromAnotherJobIsRejected() {
		CommandExecutor commands = mock(CommandExecutor.class);
		CodingApprovalService coding = mock(CodingApprovalService.class);
		when(coding.requireApproval(any(), any(), any())).thenReturn(null);
		when(coding.get("coding-approval")).thenReturn(consumedCodingApproval("task-1", "job-other", "/runtime/task-1"));
		PlanApprovalService plans = mock(PlanApprovalService.class);
		when(plans.get("plan-approval")).thenReturn(consumedPlanApproval());

		assertThrows(IllegalStateException.class, () -> executor(commands, coding, plans,
			"/runtime/task-1").execute(context("/runtime/task-1")));
		verifyNoInteractions(commands);
	}

	@Test
	void planVersionMismatchIsRejected() {
		CommandExecutor commands = mock(CommandExecutor.class);
		CodingApprovalService coding = mock(CodingApprovalService.class);
		when(coding.requireApproval(any(), any(), any())).thenReturn(null);
		when(coding.get("coding-approval")).thenReturn(consumedCodingApproval("task-1", "job-1", "/runtime/task-1"));
		PlanApprovalService plans = mock(PlanApprovalService.class);
		when(plans.get("plan-approval")).thenReturn(consumedPlanApproval(2));

		assertThrows(IllegalStateException.class, () -> executor(commands, coding, plans,
			"/runtime/task-1").execute(context("/runtime/task-1")));
		verifyNoInteractions(commands);
	}

	private ExecutionContext context(String workspace) {
		ExecutionContext context = new ExecutionContext();
		context.setTaskId("task-1"); context.setJobId("job-1"); context.setDescription("Create smoke test");
		context.setParameters(Map.of("executionMode", "READ_WRITE", "coding", Map.of("sandbox", "workspace-write")));
		context.getMetadata().put("approvalId", "coding-approval");
		context.getMetadata().put("planApprovalId", "plan-approval");
		context.getMetadata().put("planId", "plan-1"); context.getMetadata().put("planVersion", "1");
		context.setWorkspace(workspace);
		return context;
	}

	private CodexExecutor executor(CommandExecutor commands, CodingApprovalService coding,
			PlanApprovalService plans, String workspace) {
		WorkspaceResolver resolver = mock(WorkspaceResolver.class);
		when(resolver.resolve(any())).thenReturn(new WorkspaceSnapshot(workspace, "repo"));
		GitInspector git = mock(GitInspector.class);
		when(git.capture(workspace)).thenReturn(new GitSnapshot("main\n", "head\n", "", "", "", "", List.of()));
		CodexProperties properties = new CodexProperties(); properties.setTimeout(Duration.ofMinutes(1));
		CodexOutputSchemaProvider schema = mock(CodexOutputSchemaProvider.class);
		when(schema.path()).thenReturn("/tmp/schema.json");
		return new CodexExecutor(commands, resolver, git, new CodexResultMapper(new ObjectMapper()), coding,
			new ArtifactContentLimiter(10_000), properties,
			new CodexCommandBuilder(properties, new CoderPromptBuilder(), schema),
			mock(UntrackedArtifactCollector.class), plans);
	}

	private CodingApprovalRequest consumedCodingApproval(String taskId, String jobId, String workspace) {
		CodingApprovalRequest approval = new CodingApprovalRequest("coding-approval", taskId, jobId,
			workspace, "workspace-write", "approved");
		approval.approve(); approval.consume();
		return approval;
	}

	private PlanApprovalRequest consumedPlanApproval() {
		return consumedPlanApproval(1);
	}

	private PlanApprovalRequest consumedPlanApproval(int version) {
		Plan plan = new Plan("plan-1", version, "goal", PlanStatus.APPROVED, List.of(), List.of(), null,
			Instant.parse("2026-08-18T00:00:00Z"));
		PlanApprovalRequest approval = new PlanApprovalRequest("plan-approval", "task-1", plan,
			"hash", Instant.parse("2026-08-18T00:00:00Z"));
		approval.approve("user", Instant.parse("2026-08-18T00:01:00Z")); approval.consume();
		return approval;
	}
}
