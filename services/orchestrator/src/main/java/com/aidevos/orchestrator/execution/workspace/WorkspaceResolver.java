package com.aidevos.orchestrator.execution.workspace;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.execution.ExecutionContext;
import com.aidevos.orchestrator.executor.git.GitExecutor;
import com.aidevos.orchestrator.executor.git.GitResult;
import org.springframework.stereotype.Component;

@Component
public class WorkspaceResolver {

	private final CodingWorkspaceProperties properties;
	private final GitExecutor gitExecutor;

	public WorkspaceResolver(CodingWorkspaceProperties properties, GitExecutor gitExecutor) {
		this.properties = properties;
		this.gitExecutor = gitExecutor;
	}

	public WorkspaceSnapshot resolve(ExecutionContext context) {
		String configured = codingString(context, "workspace");
		String candidate = configured != null ? configured : string(context.getParameters(), "workspace");
		if (candidate == null || candidate.isBlank()) {
			candidate = context.getWorkspace();
		}
		if (candidate == null || candidate.isBlank()) {
			throw new IllegalArgumentException("Coding workspace is required");
		}

		Path workspace = realDirectory(candidate, "Coding workspace");
		validateAllowed(workspace);
		validateGitRepository(workspace);
		Path name = workspace.getFileName();
		return new WorkspaceSnapshot(workspace.toString(), name == null ? workspace.toString() : name.toString());
	}

	private void validateAllowed(Path workspace) {
		List<String> configuredRoots = properties.getAllowedRoots();
		List<String> roots = configuredRoots == null || configuredRoots.isEmpty()
			? List.of(System.getProperty("user.dir")) : configuredRoots;
		boolean allowed = roots.stream()
			.map(root -> realDirectory(root, "Coding workspace allowed root"))
			.anyMatch(workspace::startsWith);
		if (!allowed) {
			throw new IllegalArgumentException("Coding workspace is outside allowed roots: " + workspace);
		}
	}

	private void validateGitRepository(Path workspace) {
		GitResult result = gitExecutor.isRepository(workspace.toString());
		if (!result.isSuccess() || !"true".equals(result.getOutput().trim())) {
			throw new IllegalArgumentException("Coding workspace is not inside a Git repository: " + workspace);
		}
	}

	private Path realDirectory(String value, String label) {
		try {
			Path path = Path.of(value).toRealPath();
			if (!Files.isDirectory(path)) {
				throw new IllegalArgumentException(label + " is not a directory: " + path);
			}
			return path;
		}
		catch (IOException exception) {
			throw new IllegalArgumentException(label + " does not exist: " + value, exception);
		}
	}

	private String codingString(ExecutionContext context, String key) {
		Object coding = context.getParameters().get("coding");
		return coding instanceof Map<?, ?> values ? string(values, key) : null;
	}

	private String string(Map<?, ?> values, String key) {
		Object value = values.get(key);
		return value instanceof String text && !text.isBlank() ? text : null;
	}
}
