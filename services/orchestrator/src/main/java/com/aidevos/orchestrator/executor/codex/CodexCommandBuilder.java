package com.aidevos.orchestrator.executor.codex;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.execution.ExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Builds the codex CLI command line for a coding execution:
 * codex --ask-for-approval &lt;policy&gt; [--model &lt;model&gt;] exec
 * --cd &lt;workspace&gt; --sandbox &lt;sandbox&gt; --json
 * --output-schema &lt;schema&gt; &lt;prompt&gt;. All codex invocations go
 * through this builder so the CLI contract stays in one place.
 */
@Component
public class CodexCommandBuilder {

	private final CodexProperties codexProperties;
	private final CoderPromptBuilder promptBuilder;
	private final CodexOutputSchemaProvider schemaProvider;

	public CodexCommandBuilder(CodexProperties codexProperties, CoderPromptBuilder promptBuilder,
			CodexOutputSchemaProvider schemaProvider) {
		this.codexProperties = codexProperties;
		this.promptBuilder = promptBuilder;
		this.schemaProvider = schemaProvider;
	}

	public List<String> build(ExecutionContext context, String workspacePath, CodexSandbox sandbox) {
		List<String> command = new ArrayList<>(List.of(codexProperties.getExecutable(),
			"--ask-for-approval", codexProperties.getApprovalPolicy().cliValue()));
		String model = contextString(context, "model");
		if (model != null) {
			command.add("--model");
			command.add(model);
		}
		command.addAll(List.of("exec", "--cd", workspacePath, "--sandbox", sandbox.cliValue(),
			"--json", "--output-schema", projectAnalysis(context)
				? schemaProvider.path(true) : schemaProvider.path()));
		command.add(promptBuilder.build(context));
		return List.copyOf(command);
	}

	private boolean projectAnalysis(ExecutionContext context) {
		return "project-analysis".equals(contextString(context, "taskType"));
	}

	private String contextString(ExecutionContext context, String key) {
		Object value = context.getParameters().get(key);
		return value instanceof String string && !string.isBlank() ? string : null;
	}
}
