package com.aidevos.orchestrator.executor.codex;

import java.util.Map;

import com.aidevos.orchestrator.execution.ExecutionContext;
import org.springframework.stereotype.Component;

@Component
public class CoderPromptBuilder {

	public String build(ExecutionContext context) {
		return build(context, null);
	}

	public String build(ExecutionContext context, ApprovedExecutionHandoff handoff) {
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
		String executionHandoff = handoff == null ? "" : """

			The AI Dev OS orchestrator has already obtained the required human approval for this exact execution phase.
			Approved authority: %s / %s
			Approved execution workspace: %s
			The plan has already been reviewed and approved by the orchestrator.
			This invocation is the approved execution phase.
			Execute the approved task now.
			Do not request another confirmation for this already-approved plan/workspace-write authority.
			This approval does NOT authorize unrelated operations, other workspaces, git push, deployment, or additional authorities.
			Continue to obey all other AGENTS.md rules and task constraints.
			""".formatted(handoff.authority(), handoff.operation(), handoff.executionWorkspace());
		return """
			Implement the coding task in the current workspace.
			Preserve all pre-existing user changes and do not modify unrelated files.
			Do not run git commit, git push, git reset, or destructive cleanup commands.
			%s
			Task: %s
			Explicit inputs from approved predecessor artifacts: %s
			Return a JSON object matching the provided output schema.%s%s
			""".formatted(verification, context.getDescription(),
				inputs == null ? "{}" : inputs, evidenceContract, executionHandoff);
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
