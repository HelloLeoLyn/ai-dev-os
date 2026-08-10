package com.aidevos.orchestrator.mcp.tool;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import org.springframework.stereotype.Component;

/**
 * Git tool: delegates to the shared GitCommandExecutor so every git call
 * stays behind the single git entry point. Supports status, diff, patch,
 * branch and hash operations on a workspace path.
 */
@Component
public class GitToolExecutor implements McpToolExecutor {

	private final GitCommandExecutor gitCommandExecutor;

	public GitToolExecutor(GitCommandExecutor gitCommandExecutor) {
		this.gitCommandExecutor = gitCommandExecutor;
	}

	@Override
	public ToolDefinition definition() {
		return new ToolDefinition("git", "Git", ToolType.GIT,
			"Inspect the workspace git repository (status, diff, patch, branch, hash)",
			Map.of("path", "String", "operation", "status|diff|patch|branch|hash"),
			Set.of(ToolPermission.READ, ToolPermission.WRITE));
	}

	@Override
	public ToolExecutionResult execute(ToolExecutionRequest request) {
		String path = string(request, "path");
		if (path == null || path.isBlank()) {
			return ToolExecutionResult.failure("Missing required parameter: path", Map.of());
		}
		String operation = string(request, "operation");
		if (operation == null) {
			operation = "status";
		}
		try {
			return switch (operation) {
				case "status" -> {
					GitStatus status = gitCommandExecutor.status(path);
					Map<String, Object> metadata = new LinkedHashMap<>();
					metadata.put("branch", status.getBranch());
					metadata.put("modified", status.getModified());
					metadata.put("added", status.getAdded());
					metadata.put("deleted", status.getDeleted());
					yield ToolExecutionResult.success(
						"branch=" + status.getBranch() + " modified=" + status.getModified()
							+ " added=" + status.getAdded() + " deleted=" + status.getDeleted(),
						metadata);
				}
				case "diff" -> {
					GitDiff diff = gitCommandExecutor.diff(path);
					Map<String, Object> metadata = new LinkedHashMap<>();
					metadata.put("filesChanged", diff.getFilesChanged());
					metadata.put("insertions", diff.getInsertions());
					metadata.put("deletions", diff.getDeletions());
					yield ToolExecutionResult.success(
						diff.getFilesChanged() + " files changed", metadata);
				}
				case "patch" -> ToolExecutionResult.success(
					gitCommandExecutor.patch(path), Map.of("path", path));
				case "branch" -> ToolExecutionResult.success(
					gitCommandExecutor.status(path).getBranch(), Map.of("path", path));
				case "hash" -> ToolExecutionResult.success(
					gitCommandExecutor.currentCommitHash(path), Map.of("path", path));
				default -> ToolExecutionResult.failure(
					"Unsupported git operation: " + operation, Map.of());
			};
		}
		catch (RuntimeException exception) {
			return ToolExecutionResult.failure(exception.getMessage(), Map.of("path", path));
		}
	}

	private String string(ToolExecutionRequest request, String key) {
		Object value = request.parameters().get(key);
		return value == null ? null : String.valueOf(value);
	}
}
