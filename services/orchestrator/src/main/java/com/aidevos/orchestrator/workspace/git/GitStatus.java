package com.aidevos.orchestrator.workspace.git;

/**
 * Structured git working-tree state: current branch plus porcelain status
 * counts. Untracked files count as added; staged and unstaged modifications
 * both count as modified.
 */
public class GitStatus {

	private final String branch;
	private final int modified;
	private final int added;
	private final int deleted;

	public GitStatus(String branch, int modified, int added, int deleted) {
		this.branch = branch;
		this.modified = modified;
		this.added = added;
		this.deleted = deleted;
	}

	public String getBranch() {
		return branch;
	}

	public int getModified() {
		return modified;
	}

	public int getAdded() {
		return added;
	}

	public int getDeleted() {
		return deleted;
	}
}
