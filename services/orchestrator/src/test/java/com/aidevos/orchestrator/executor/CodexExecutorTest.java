package com.aidevos.orchestrator.executor;

import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.approval.CodingApprovalService;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.execution.workspace.WorkspaceResolver;
import com.aidevos.orchestrator.execution.workspace.WorkspaceSnapshot;
import com.aidevos.orchestrator.executor.codex.CodexApprovalPolicy;
import com.aidevos.orchestrator.executor.codex.CodexCommandBuilder;
import com.aidevos.orchestrator.executor.codex.CodexErrorClassifier;
import com.aidevos.orchestrator.executor.codex.CodexExecutor;
import com.aidevos.orchestrator.executor.codex.CodexOutputSchemaProvider;
import com.aidevos.orchestrator.executor.codex.CodexProperties;
import com.aidevos.orchestrator.executor.codex.CodexResultMapper;
import com.aidevos.orchestrator.executor.codex.CoderPromptBuilder;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import com.aidevos.orchestrator.executor.git.GitInspector;
import com.aidevos.orchestrator.executor.git.GitSnapshot;
import com.aidevos.orchestrator.executor.git.UntrackedArtifactCollector;
import com.aidevos.orchestrator.modelregistry.ModelResolutionException;
import com.aidevos.orchestrator.modelregistry.ModelResolver;
import com.aidevos.orchestrator.modelregistry.ResolvedModel;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CodexExecutorTest {

	private static final ResolvedModel DEFAULT_MODEL = new ResolvedModel("AUTO", "gpt-5.6-codex",
		"openai", "codex", null, null);

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
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setTaskId("task-1");
		context.setDescription("Implement a new feature");
		context.setWorkspace("/workspace/project");
		context.getMetadata().put("approvalId", "approval-1");

		ExecutionResult result = executor(commandExecutor, "/workspace/project").execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(List.of("codex", "--ask-for-approval", "never", "--model", "gpt-5.6-codex",
			"exec", "--cd", "/workspace/project", "--sandbox", "workspace-write", "--json",
			"--output-schema", "/tmp/schema.json",
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
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Invalid task");

		ExecutionResult result = executor(commandExecutor, "/workspace/project").execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals(List.of("codex", "--ask-for-approval", "never", "--model", "gpt-5.6-codex",
			"exec", "--cd", "/workspace/project", "--sandbox", "workspace-write", "--json",
			"--output-schema", "/tmp/schema.json",
			new CoderPromptBuilder().build(context)), optionsCaptor.getValue().getCommand());
		assertEquals("/workspace/project", optionsCaptor.getValue().getWorkingDirectory());
		assertFalse(result.isSuccess());
		assertEquals("Codex failed", result.getMessage());
	}

	@Test
	void shouldPreferStructuredTurnFailureOverStderr() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(false);
		commandResult.setExitCode(1);
		commandResult.setOutput("""
			{"type":"turn.failed","failure":{"error":{"type":"usage_limit","message":"You've hit your usage limit. Please try again later."}}}
			""");
		commandResult.setError("Reading additional input from stdin...");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Invalid task");

		ExecutionResult result = executorWithClassifier(commandExecutor, "/workspace/project").execute(context);

		assertFalse(result.isSuccess());
		assertEquals("You've hit your usage limit. Please try again later.", result.getMessage());
		assertEquals("USAGE_LIMIT", result.getMetadata().get("errorCode"));
		assertEquals("You've hit your usage limit. Please try again later.",
			result.getMetadata().get("errorMessage"));
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

		IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
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

	@Test
	void shouldRouteRequestedDeepSeekModelToDeepSeekProvider() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setExitCode(0);
		commandResult.setOutput("Codex output");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ModelResolver modelResolver = mock(ModelResolver.class);
		when(modelResolver.resolve(nullable(String.class), nullable(String.class)))
			.thenReturn(new ResolvedModel("deepseek-v4-flash", "deepseek-v4-flash", "deepseek",
				"codex", "https://api.deepseek.com", "OPENAI_API_KEY"));

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Implement a new feature");
		context.getParameters().put("requestedModelId", "deepseek-v4-flash");

		ExecutionResult result = executor(commandExecutor, "/workspace/project",
			new CodexProperties(), modelResolver).execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		List<String> command = optionsCaptor.getValue().getCommand();
		assertTrue(command.contains("--model"));
		assertEquals("deepseek-v4-flash", command.get(command.indexOf("--model") + 1));
		assertTrue(command.contains("-c"));
		assertTrue(command.contains("model_provider=\"deepseek\""));
		assertTrue(command.contains("model_providers.deepseek.base_url=\"https://api.deepseek.com\""));
		assertTrue(command.contains("model_providers.deepseek.env_key=\"OPENAI_API_KEY\""));
		assertEquals("deepseek-v4-flash", result.getMetadata().get("requestedModelId"));
		assertEquals("deepseek-v4-flash", result.getMetadata().get("resolvedModelId"));
		assertEquals("deepseek", result.getMetadata().get("modelProvider"));
		assertEquals("codex", result.getMetadata().get("modelExecutor"));
	}

	@Test
	void shouldInjectCredentialOnlyIntoSubprocessEnvironment() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setExitCode(0);
		commandResult.setOutput("Codex output");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ModelResolver modelResolver = mock(ModelResolver.class);
		when(modelResolver.resolve(nullable(String.class), nullable(String.class)))
			.thenReturn(new ResolvedModel("deepseek-v4-flash", "deepseek-v4-flash", "deepseek",
				"codex", "https://api.deepseek.com", "DEEPSEEK_API_KEY"));

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Implement a new feature");
		context.getParameters().put("requestedModelId", "deepseek-v4-flash");

		Map<String, String> environment = new HashMap<>();
		environment.put("DEEPSEEK_API_KEY", "super-secret-value");
		CodexExecutor executor = executorWithEnvironment(commandExecutor, "/workspace/project",
			new CodexProperties(), modelResolver, environment);
		ExecutionResult result = executor.execute(context);

		ArgumentCaptor<CommandOptions> optionsCaptor = ArgumentCaptor.forClass(CommandOptions.class);
		verify(commandExecutor).execute(optionsCaptor.capture());
		assertEquals("super-secret-value", optionsCaptor.getValue().getEnvironment().get("DEEPSEEK_API_KEY"));
		assertFalse(String.valueOf(result.getMetadata()).contains("super-secret-value"));
		assertFalse(String.valueOf(result.getOutput()).contains("super-secret-value"));
		assertFalse(String.valueOf(result.getMessage()).contains("super-secret-value"));
	}

	@Test
	void shouldFailClosedWhenExplicitModelCannotBeResolved() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		ModelResolver modelResolver = mock(ModelResolver.class);
		when(modelResolver.resolve(nullable(String.class), nullable(String.class)))
			.thenThrow(new ModelResolutionException(ModelResolutionException.Code.MODEL_NOT_FOUND,
				"Model definition not found: deepseek-v4-flash"));

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Implement a new feature");
		context.getParameters().put("requestedModelId", "deepseek-v4-flash");

		ModelResolutionException exception = assertThrows(ModelResolutionException.class,
			() -> executor(commandExecutor, "/workspace/project", new CodexProperties(),
				modelResolver).execute(context));
		assertEquals(ModelResolutionException.Code.MODEL_NOT_FOUND, exception.code());
		verify(commandExecutor, never()).execute(any(CommandOptions.class));
	}

	@Test
	void shouldRecordAutoEvidenceWhenNoRequestedModel() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setExitCode(0);
		commandResult.setOutput("Codex output");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Implement a new feature");

		ExecutionResult result = executor(commandExecutor, "/workspace/project").execute(context);

		assertEquals("AUTO", result.getMetadata().get("requestedModelId"));
		assertEquals("gpt-5.6-codex", result.getMetadata().get("resolvedModelId"));
		assertEquals("openai", result.getMetadata().get("modelProvider"));
	}

	@Test
	void shouldNotRequireEnvironmentWhenProviderHasNoCredentialRef() {
		CommandExecutor commandExecutor = mock(CommandExecutor.class);
		CommandResult commandResult = new CommandResult();
		commandResult.setSuccess(true);
		commandResult.setExitCode(0);
		commandResult.setOutput("Codex output");
		when(commandExecutor.execute(any(CommandOptions.class))).thenReturn(commandResult);

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Implement a new feature");

		ExecutionResult result = executor(commandExecutor, "/workspace/project").execute(context);
		assertTrue(result.isSuccess());
	}

	private CodexExecutor executor(CommandExecutor commandExecutor, String workspace) {
		return executor(commandExecutor, workspace, new CodexProperties(), defaultResolver());
	}

	private CodexExecutor executor(CommandExecutor commandExecutor, String workspace,
			CodexProperties properties) {
		return executor(commandExecutor, workspace, properties, defaultResolver());
	}

	private CodexExecutor executor(CommandExecutor commandExecutor, String workspace,
			CodexProperties properties, ModelResolver modelResolver) {
		return executorWithEnvironment(commandExecutor, workspace, properties, modelResolver, Map.of());
	}

	private CodexExecutor executorWithEnvironment(CommandExecutor commandExecutor, String workspace,
			CodexProperties properties, ModelResolver modelResolver, Map<String, String> environment) {
		return executorWithEnvironmentAndClassifier(commandExecutor, workspace, properties, modelResolver,
			environment, null);
	}

	private CodexExecutor executorWithClassifier(CommandExecutor commandExecutor, String workspace) {
		return executorWithEnvironmentAndClassifier(commandExecutor, workspace, new CodexProperties(),
			defaultResolver(), Map.of(), new CodexErrorClassifier());
	}

	private CodexExecutor executorWithEnvironmentAndClassifier(CommandExecutor commandExecutor,
			String workspace, CodexProperties properties, ModelResolver modelResolver,
			Map<String, String> environment, CodexErrorClassifier classifier) {
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
			untrackedCollector, null, modelResolver, classifier) {
			@Override
			protected String environmentValue(String name) {
				return environment.get(name);
			}
		};
	}

	private ModelResolver defaultResolver() {
		ModelResolver modelResolver = mock(ModelResolver.class);
		when(modelResolver.resolve(nullable(String.class), nullable(String.class)))
			.thenReturn(DEFAULT_MODEL);
		return modelResolver;
	}

}
