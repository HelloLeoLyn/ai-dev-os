package com.aidevos.orchestrator.executor.codex;

import java.util.Map;

import com.aidevos.orchestrator.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class CoderPromptBuilder {

	public String build(ExecutionContext context) {
		String testCommand = codingString(context, "testCommand");
		Object inputs = context.getParameters().get("inputs");
		String verification = testCommand == null
			? "Run the smallest relevant verification available for the change."
			: "Run this verification command if it is safe and applicable: " + testCommand;
		String evidenceContract = projectAnalysis(context) ? """

			For project-analysis evidenceRefs, use only SOURCE_FILE or ARTIFACT.
			SOURCE_FILE ref must be a current-workspace relative path; never use an absolute path or '..'.
			ARTIFACT ref must be a real artifact name or URI from this execution. Stable names include:
			analysis-result.json, codex-events.jsonl, codex-result.txt, changes.patch,
			cached-changes.patch, git-diff-stat.txt, git-status-before.txt,
			git-status-after.txt, and untracked-files.txt.
			A command name such as 'git diff --check' is not an evidence ref. If command output is
			material evidence, reference codex-events.jsonl or another artifact that actually contains it.
			Do not invent evidence. An empty evidenceRefs array is valid when no reliable reference exists.
			""" : "";
		return """
			Implement the coding task in the current workspace.
			Preserve all pre-existing user changes and do not modify unrelated files.
			Do not run git commit, git push, git reset, or destructive cleanup commands.
			%s
			Task: %s
			Explicit inputs from approved predecessor artifacts: %s
			Return a JSON object matching the provided output schema.%s
			""".formatted(verification, context.getDescription(),
				inputs == null ? "{}" : inputs, evidenceContract);
	}

	private boolean projectAnalysis(ExecutionContext context) {
		Object value = context.getParameters().get("taskType");
		return value != null && "project-analysis".equals(String.valueOf(value));
	}

	private String codingString(ExecutionContext context, String key) {
		Object coding = context.getParameters().get("coding");
		if (coding instanceof Map<?, ?> values && values.get(key) instanceof String value
				&& !value.isBlank()) {
			return value;
		}
		return null;
	}
}
