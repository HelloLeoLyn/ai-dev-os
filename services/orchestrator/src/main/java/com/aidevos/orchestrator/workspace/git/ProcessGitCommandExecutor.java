package com.aidevos.orchestrator.workspace.git;

import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import org.springframework.stereotype.Component;

/**
 * Process-based git executor backed by the shared CommandExecutor. Commands
 * that fail (for example a directory that is not a git repository) degrade to
 * an empty structured result instead of raising raw process errors.
 */
@Component
public class ProcessGitCommandExecutor implements GitCommandExecutor {

	private static final Pattern DIFF_STAT_SUMMARY = Pattern.compile(
		"(\\d+) files? changed(?:, (\\d+) insertions?\\(\\+\\))?(?:, (\\d+) deletions?\\(-\\))?");

	private final CommandExecutor commandExecutor;

	public ProcessGitCommandExecutor(CommandExecutor commandExecutor) {
		this.commandExecutor = commandExecutor;
	}

	@Override
	public GitStatus status(String path) {
		String branch = branch(path);
		CommandResult porcelain = run(List.of("git", "status", "--porcelain"), path);
		int modified = 0;
		int added = 0;
		int deleted = 0;
		if (porcelain.isSuccess() && porcelain.getOutput() != null) {
			for (String line : porcelain.getOutput().split("\\R")) {
				if (line.isBlank()) {
					continue;
				}
				switch (line.charAt(0)) {
					case 'A', '?' -> added++;
					case 'D' -> deleted++;
					default -> modified++;
				}
			}
		}
		return new GitStatus(branch, modified, added, deleted);
	}

	@Override
	public GitDiff diff(String path) {
		CommandResult result = run(List.of("git", "diff", "--stat"), path);
		String stat = result.isSuccess() ? result.getOutput() : "";
		return new GitDiff(parseInt(stat, 1), parseInt(stat, 2), parseInt(stat, 3), stat);
	}

	@Override
	public String patch(String path) {
		CommandResult result = run(List.of("git", "diff", "--no-ext-diff"), path);
		return result.isSuccess() && result.getOutput() != null ? result.getOutput() : "";
	}

	@Override
	public String commit(String path, String message) {
		run(List.of("git", "add", "-A"), path);
		CommandResult commitResult = run(List.of("git", "commit", "-m", message), path);
		if (!commitResult.isSuccess()) {
			return "";
		}
		return currentCommitHash(path);
	}

	@Override
	public String currentCommitHash(String path) {
		CommandResult result = run(List.of("git", "rev-parse", "HEAD"), path);
		return result.isSuccess() && result.getOutput() != null ? result.getOutput().trim() : "";
	}

	@Override
	public String listRemotes(String path) {
		CommandResult result = run(List.of("git", "remote", "-v"), path);
		return result.isSuccess() && result.getOutput() != null ? result.getOutput() : "";
	}

	@Override
	public boolean push(String path, String remote, String branch) {
		CommandResult result = run(List.of("git", "push", remote, branch), path);
		return result.isSuccess();
	}

	private String branch(String path) {
		CommandResult result = run(List.of("git", "branch", "--show-current"), path);
		return result.isSuccess() && result.getOutput() != null
			? result.getOutput().trim() : "";
	}

	private int parseInt(String stat, int group) {
		Matcher matcher = DIFF_STAT_SUMMARY.matcher(stat == null ? "" : stat);
		if (!matcher.find() || matcher.group(group) == null) {
			return 0;
		}
		try {
			return Integer.parseInt(matcher.group(group));
		}
		catch (NumberFormatException exception) {
			return 0;
		}
	}

	private CommandResult run(List<String> command, String path) {
		CommandOptions options = new CommandOptions();
		options.setCommand(command);
		options.setWorkingDirectory(path);
		return commandExecutor.execute(options);
	}
}
