package com.aidevos.orchestrator.workspace.git;

/**
 * Structured summary of `git diff --stat` output: how many files changed and
 * how many insertions/deletions were made, plus the raw stat text.
 */
public class GitDiff {

	private final int filesChanged;
	private final int insertions;
	private final int deletions;
	private final String stat;

	public GitDiff(int filesChanged, int insertions, int deletions, String stat) {
		this.filesChanged = filesChanged;
		this.insertions = insertions;
		this.deletions = deletions;
		this.stat = stat == null ? "" : stat;
	}

	public int getFilesChanged() {
		return filesChanged;
	}

	public int getInsertions() {
		return insertions;
	}

	public int getDeletions() {
		return deletions;
	}

	public String getStat() {
		return stat;
	}
}
