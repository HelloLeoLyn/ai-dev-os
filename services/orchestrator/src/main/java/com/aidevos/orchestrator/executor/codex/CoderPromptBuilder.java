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
		return """
			Implement the coding task in the current workspace.
			Preserve all pre-existing user changes and do not modify unrelated files.
			Do not run git commit, git push, git reset, or destructive cleanup commands.
			%s
			Task: %s
			Explicit inputs from approved predecessor artifacts: %s
			Return a JSON object matching the provided output schema.
			""".formatted(verification, context.getDescription(),
				inputs == null ? "{}" : inputs);
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
