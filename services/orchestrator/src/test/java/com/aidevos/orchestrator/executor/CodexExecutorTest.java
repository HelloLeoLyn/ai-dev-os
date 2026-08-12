package com.aidevos.orchestrator.executor;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.execution.workspace.WorkspaceResolver;
import com.aidevos.orchestrator.execution.workspace.WorkspaceSnapshot;
import com.aidevos.orchestrator.executor.codex.CodexCommandBuilder;
import com.aidevos.orchestrator.executor.codex.CodexExecutor;
import com.aidevos.orchestrator.executor.codex.CodexResultMapper;
import com.aidevos.orchestrator.executor.git.GitInspector;
import com.aidevos.orchestrator.executor.git.GitSnapshot;
import com.aidevos.orchestrator.executor.git.UntrackedArtifactCollector;
import tools.jackson.databind.ObjectMapper;
import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.executor.codex.CodexProperties;
import com.aidevos.orchestrator.executor.codex.CodexApprovalPolicy;
import com.aidevos.orchestrator.executor.codex.CoderPromptBuilder;
import com.aidevos.orchestrator.executor.codex.CodexOutputSchemaProvider;
import java.time.Duration;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodexExecutorTest {

	@Test
	void shouldReturnCodexType() {
		assertEquals("codex", executor(mock(CommandExecutor.class), "/workspace/project").getType());
	}

	@Test
	void shouldExecuteCodexCommandAndConvertSuccessfulResult() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setExitCode(0);
		commandResult.setOutput("Codex output");
		List<String> command = List.of("codex", "exec", "Implement a new feature");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setTaskId("task-1");
		context.setDescription("Implement a new feature");
		context.setWorkspace("/workspace/project");
		context.getMetadata().put("approvalId", "approval-1");

		ExecutionResult result = executor(commandExecutor, "/workspace/project").execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(List.of("codex", "--ask-for-approval", "never", "exec", "--cd", "/workspace/project", "--sandbox",
			"workspace-write", "--json", "--output-schema", "/tmp/schema.json",
			new CoderPromptBuilder().build(context)), optionsCaptor.getValue().getCommand());
		assertEquals("/workspace/project", optionsCaptor.getValue().getWorkingDirectory());
		assertTrue(result.isSuccess());
		assertEquals("Task executed successfully", result.getMessage());
		assertEquals("Codex output", result.getOutput());
		assertEquals(7, result.getArtifacts().size());
		assertEquals("/workspace/project", result.getMetadata().get("workspace"));
		assertEquals("workspace-write", result.getMetadata().get("sandbox"));
		assertEquals("main", result.getMetadata().get("branch"));
		assertEquals("approval-1", result.getApprovalId());
	}

	@Test
	void shouldConvertFailedCommandResult() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(false);
		commandResult.setExitCode(1);
		commandResult.setError("Codex failed");
		List<String> command = List.of("codex", "exec", "Invalid task");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Invalid task");

		ExecutionResult result = executor(commandExecutor, "/workspace/project").execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(List.of("codex", "--ask-for-approval", "never", "exec", "--cd", "/workspace/project", "--sandbox",
			"workspace-write", "--json", "--output-schema", "/tmp/schema.json",
			new CoderPromptBuilder().build(context)), optionsCaptor.getValue().getCommand());
		assertEquals("/workspace/project", optionsCaptor.getValue().getWorkingDirectory());
		assertFalse(result.isSuccess());
		assertEquals("Codex failed", result.getMessage());
	}

	@Test
	void shouldApplyExecutorConfiguration() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);
		ExecutionContext context = new ExecutionContext();
		context.setDescription("Implement a new feature");
		context.setWorkspace("/default/workspace");
		context.setParameters(Map.of(
			"workspace", "/configured/workspace",
			"model", "gpt-5.6-codex"));

		executor(commandExecutor, "/configured/workspace").execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(List.of("codex", "--ask-for-approval", "never", "--model", "gpt-5.6-codex",
			"exec", "--cd", "/configured/workspace", "--sandbox", "workspace-write", "--json",
			"--output-schema", "/tmp/schema.json", new CoderPromptBuilder().build(context)),
			optionsCaptor.getValue().getCommand());
		assertEquals("/configured/workspace", optionsCaptor.getValue().getWorkingDirectory());
	}

	@Test
	void shouldRejectUnsupportedSandbox() {
		ExecutionContext context = new ExecutionContext();
		context.setDescription("Unsafe task");
		context.setParameters(Map.of("coding", Map.of("sandbox", "danger-full-access")));

		IllegalArgumentException exception = org.junit.jupiter.api.Assertions.assertThrows(
			IllegalArgumentException.class,
			() -> executor(mock(CommandExecutor.class), "/workspace/project").execute(context));

		assertEquals("Unsupported Codex sandbox: danger-full-access", exception.getMessage());
	}

	@Test
	void shouldForceReadOnlySandboxForReadOnlyTask() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);
		ExecutionContext context = new ExecutionContext();
		context.setDescription("Inspect repository");
		context.getParameters().put("executionMode", "READ_ONLY");
		context.getParameters().put("sandbox", "workspace-write");

		ExecutionResult result = executor(commandExecutor, "/workspace/project").execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		List<String> command = optionsCaptor.getValue().getCommand();
		assertEquals("read-only", command.get(command.indexOf("--sandbox") + 1));
		assertEquals("read-only", result.getMetadata().get("sandbox"));
	}

	@Test
	void shouldUseConfiguredExecutableAndApprovalPolicy() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);
		CodexProperties properties = new CodexProperties();
		properties.setExecutable("/opt/codex/bin/codex");
		properties.setApprovalPolicy(CodexApprovalPolicy.ON_REQUEST);
		ExecutionContext context = new ExecutionContext();
		context.setDescription("Validate configuration");

		executor(commandExecutor, "/workspace/project", properties).execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals("/opt/codex/bin/codex", optionsCaptor.getValue().getCommand().get(0));
		assertEquals(List.of("--ask-for-approval", "on-request"),
			optionsCaptor.getValue().getCommand().subList(1, 3));
	}

	private CodexExecutor executor(CommandExecutor commandExecutor, String workspace) {
		return executor(commandExecutor, workspace, new CodexProperties());
	}

	private CodexExecutor executor(CommandExecutor commandExecutor, String workspace,
			CodexProperties properties) {
		WorkspaceResolver resolver = mock(WorkspaceResolver.class);
		when(resolver.resolve(any(ExecutionContext.class)))
			.thenReturn(new WorkspaceSnapshot(workspace, "project"));
		GitInspector gitInspector = mock(GitInspector.class);
		when(gitInspector.capture(workspace)).thenReturn(new GitSnapshot(
			"main\n", "abc123\n", "", "", "", "", List.of()));
		properties.setTimeout(Duration.ofMinutes(1));
		CodexOutputSchemaProvider schemaProvider = mock(CodexOutputSchemaProvider.class);
		when(schemaProvider.path()).thenReturn("/tmp/schema.json");
		UntrackedArtifactCollector untrackedCollector = mock(UntrackedArtifactCollector.class);
		when(untrackedCollector.collect(any(), any())).thenReturn(List.of());
		return new CodexExecutor(commandExecutor, resolver, gitInspector,
			new CodexResultMapper(new ObjectMapper()), mock(CodingApprovalService.class),
			new ArtifactContentLimiter(10_000), properties,
			new CodexCommandBuilder(properties, new CoderPromptBuilder(), schemaProvider),
			untrackedCollector);
	}
}
