package com.aidevos.orchestrator.executor.codex;

import java.time.Duration;
import java.util.List;

import com.aidevos.orchestrator.execution.ExecutionContext;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CodexCommandBuilderTest {

	@Test
	void shouldBuildCodexExecCommandWithDefaults() {
		CodexProperties properties = properties("codex", CodexApprovalPolicy.NEVER);
		CodexCommandBuilder builder = new CodexCommandBuilder(properties,
			prompt("Implement a new feature"), schemaProvider("/tmp/schema.json"));

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Implement a new feature");
		List<String> command = builder.build(context, "/workspace/project", CodexSandbox.WORKSPACE_WRITE);

		assertEquals(List.of("codex", "--ask-for-approval", "never", "exec", "--cd",
			"/workspace/project", "--sandbox", "workspace-write", "--json",
			"--output-schema", "/tmp/schema.json", "Implement a new feature"), command);
	}

	@Test
	void shouldHonourModelAndSandboxConfiguration() {
		CodexProperties properties = properties("/opt/codex/bin/codex", CodexApprovalPolicy.ON_REQUEST);
		CodexCommandBuilder builder = new CodexCommandBuilder(properties,
			prompt("Refactor the login module"), schemaProvider("/tmp/schema.json"));

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Refactor the login module");
		context.getParameters().put("model", "gpt-5.4");
		List<String> command = builder.build(context, "/tmp/repo", CodexSandbox.READ_ONLY);

		assertEquals("/opt/codex/bin/codex", command.getFirst());
		assertTrue(command.contains("--model"));
		assertTrue(command.contains("gpt-5.4"));
		assertTrue(command.contains("--cd"));
		assertTrue(command.contains("/tmp/repo"));
		assertTrue(command.contains("--sandbox"));
		assertTrue(command.contains("read-only"));
		assertEquals("Refactor the login module", command.getLast());
	}

	@Test
	void shouldUseProjectAnalysisOutputSchemaForProjectAnalysisTasks() {
		CodexProperties properties = properties("codex", CodexApprovalPolicy.NEVER);
		CodexCommandBuilder builder = new CodexCommandBuilder(properties,
			prompt("Analyze the project"), schemaProvider("/tmp/schema.json", "/tmp/analysis-schema.json"));

		ExecutionContext context = new ExecutionContext();
		context.setDescription("Analyze the project");
		context.getParameters().put("taskType", "project-analysis");
		List<String> command = builder.build(context, "/workspace/project", CodexSandbox.READ_ONLY);

		assertEquals("/tmp/analysis-schema.json", command.get(command.indexOf("--output-schema") + 1));
	}

	@Test
	void projectAnalysisPromptDefinesSafeEvidenceReferences() {
		CodexCommandBuilder builder=new CodexCommandBuilder(properties("codex",CodexApprovalPolicy.NEVER),
			new CoderPromptBuilder(),schemaProvider("/tmp/schema.json","/tmp/analysis-schema.json"));
		ExecutionContext context=new ExecutionContext(); context.setDescription("Analyze safely");
		context.getParameters().put("taskType","project-analysis");
		String prompt=builder.build(context,"/workspace/project",CodexSandbox.READ_ONLY).getLast();
		assertTrue(prompt.contains("use only SOURCE_FILE or ARTIFACT"));
		assertTrue(prompt.contains("git diff --check")); assertTrue(prompt.contains("codex-events.jsonl"));
		assertTrue(prompt.contains("empty evidenceRefs array is valid"));
	}

	private CodexProperties properties(String executable, CodexApprovalPolicy policy) {
		CodexProperties properties = new CodexProperties();
		properties.setExecutable(executable);
		properties.setApprovalPolicy(policy);
		properties.setTimeout(Duration.ofMinutes(1));
		return properties;
	}

	private CoderPromptBuilder prompt(String prompt) {
		CoderPromptBuilder promptBuilder = mock(CoderPromptBuilder.class);
		when(promptBuilder.build(any())).thenReturn(prompt);
		return promptBuilder;
	}

	private CodexOutputSchemaProvider schemaProvider(String path) {
		return schemaProvider(path, "/tmp/analysis-schema.json");
	}

	private CodexOutputSchemaProvider schemaProvider(String path, String analysisPath) {
		CodexOutputSchemaProvider provider = mock(CodexOutputSchemaProvider.class);
		when(provider.path()).thenReturn(path);
		when(provider.path(true)).thenReturn(analysisPath);
		return provider;
	}
}
