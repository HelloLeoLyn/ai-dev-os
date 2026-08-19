package com.aidevos.orchestrator.executor.codex;

import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.execution.ExecutionContext;
import org.springframework.stereotype.Component;

/**
 * Builds the codex CLI command line for a coding execution:
 * codex --ask-for-approval &lt;policy&gt; --model &lt;model&gt; [-c provider config] exec
 * --cd &lt;workspace&gt; --sandbox &lt;sandbox&gt; --json
 * --output-schema &lt;schema&gt; &lt;prompt&gt;. All codex invocations go
 * through this builder so the CLI contract stays in one place.
 *
 * The resolved model/provider come from the trusted ModelResolver and are
 * passed through execution context parameters; the builder never hardcodes a
 * model and never omits --model, so the CLI can never silently fall back to
 * its default OpenAI provider.
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
		return build(context, workspacePath, sandbox, null);
	}

	public List<String> build(ExecutionContext context, String workspacePath, CodexSandbox sandbox,
			ApprovedExecutionHandoff handoff) {
		List<String> command = new ArrayList<>(List.of(codexProperties.getExecutable(),
			"--ask-for-approval", codexProperties.getApprovalPolicy().cliValue()));
		command.add("--model");
		command.add(resolvedModel(context));
		addProviderOverrides(context, command);
		command.addAll(List.of("exec", "--cd", workspacePath, "--sandbox", sandbox.cliValue(),
			"--json", "--output-schema", projectAnalysis(context)
				? schemaProvider.path(true) : schemaProvider.path()));
		command.add(handoff == null ? promptBuilder.build(context) : promptBuilder.build(context, handoff));
		return List.copyOf(command);
	}

	private void addProviderOverrides(ExecutionContext context, List<String> command) {
		String providerId = contextString(context, "modelProvider");
		String baseUrl = contextString(context, "providerBaseUrl");
		if (baseUrl == null) {
			return;
		}
		if (providerId == null) {
			throw new IllegalStateException("Provider base URL is set but provider id is missing");
		}
		command.add("-c");
		command.add("model_provider=\"" + providerId + "\"");
		command.add("-c");
		command.add("model_providers." + providerId + ".base_url=\"" + baseUrl + "\"");
		String credentialRef = contextString(context, "credentialRef");
		if (credentialRef != null) {
			command.add("-c");
			command.add("model_providers." + providerId + ".env_key=\"" + credentialRef + "\"");
		}
	}

	private String resolvedModel(ExecutionContext context) {
		String model = contextString(context, "model");
		if (model == null) {
			throw new IllegalStateException(
				"Resolved model is missing; model resolution must fail closed before the CLI runs");
		}
		return model;
	}

	private boolean projectAnalysis(ExecutionContext context) {
		return "project-analysis".equals(contextString(context, "taskType"));
	}

	private String contextString(ExecutionContext context, String key) {
		Object value = context.getParameters().get(key);
		return value instanceof String string && !string.isBlank() ? string : null;
	}
}
