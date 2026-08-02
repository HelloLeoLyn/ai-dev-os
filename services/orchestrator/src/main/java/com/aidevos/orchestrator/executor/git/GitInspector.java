package com.aidevos.orchestrator.executor.git;

import java.util.Arrays;
import java.util.List;

import org.springframework.stereotype.Component;

@Component
public class GitInspector {

	private final GitExecutor gitExecutor;

	public GitInspector(GitExecutor gitExecutor) {
		this.gitExecutor = gitExecutor;
	}

	public GitSnapshot capture(String workspace) {
		return new GitSnapshot(
			required(gitExecutor.branch(workspace), "branch"),
			required(gitExecutor.head(workspace), "HEAD"),
			required(gitExecutor.status(workspace), "status"),
			 required(gitExecutor.diff(workspace), "diff stat"),
			 required(gitExecutor.patch(workspace), "diff"),
			 required(gitExecutor.cachedDiff(workspace), "cached diff"),
			 parseNullSeparated(required(gitExecutor.untrackedFiles(workspace), "untracked files")));
	}

	private List<String> parseNullSeparated(String value) {
		if (value.isEmpty()) {
			return List.of();
		}
		return Arrays.stream(value.split("\\u0000", -1))
			.filter(path -> !path.isEmpty())
			.toList();
	}

	private String required(GitResult result, String operation) {
		if (!result.isSuccess()) {
			String detail = result.getError() == null || result.getError().isBlank()
				? "unknown error" : result.getError();
			throw new IllegalStateException("Git " + operation + " failed: " + detail);
		}
		return result.getOutput() == null ? "" : result.getOutput();
	}
}
