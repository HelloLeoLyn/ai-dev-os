package com.aidevos.orchestrator.workspace;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitCommandExecutor;
import com.aidevos.orchestrator.workspace.git.GitDiff;
import com.aidevos.orchestrator.workspace.git.GitStatus;
import com.aidevos.orchestrator.workspace.git.ProcessGitCommandExecutor;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs real git commands against temporary repositories to verify the
 * structured status/diff parsing.
 */
class GitCommandExecutorTest {

	@TempDir
	Path tempDir;

	private GitCommandExecutor executor;

	@BeforeEach
	void setUp() throws Exception {
		executor = new ProcessGitCommandExecutor(new CommandExecutor());
		git(tempDir, "init", "-b", "main");
		git(tempDir, "config", "user.email", "test@example.com");
		git(tempDir, "config", "user.name", "Test");
		write(tempDir.resolve("a.txt"), "one");
		git(tempDir, "add", "a.txt");
		git(tempDir, "commit", "-m", "init");
	}

	@Test
	void shouldReportCleanRepositoryState() {
		GitStatus status = executor.status(tempDir.toString());

		assertEquals("main", status.getBranch());
		assertEquals(0, status.getModified());
		assertEquals(0, status.getAdded());
		assertEquals(0, status.getDeleted());
	}

	@Test
	void shouldCountModifiedUntrackedAndDeletedFiles() throws Exception {
		write(tempDir.resolve("a.txt"), "two");
		write(tempDir.resolve("b.txt"), "new file");
		write(tempDir.resolve("c.txt"), "to delete");
		git(tempDir, "add", "c.txt");
		git(tempDir, "commit", "-m", "add c");
		git(tempDir, "rm", "c.txt");

		GitStatus status = executor.status(tempDir.toString());

		assertEquals("main", status.getBranch());
		assertEquals(1, status.getModified());
		assertEquals(1, status.getAdded());
		assertEquals(1, status.getDeleted());
	}

	@Test
	void shouldReportDiffStatSummary() throws Exception {
		write(tempDir.resolve("a.txt"), "two");

		GitDiff diff = executor.diff(tempDir.toString());

		assertEquals(1, diff.getFilesChanged());
		assertEquals(1, diff.getInsertions());
		assertEquals(1, diff.getDeletions());
		assertTrue(diff.getStat().contains("a.txt"));
	}

	@Test
	void shouldReturnEmptyResultOutsideRepository() throws Exception {
		Path plain = Files.createTempDirectory("plain-dir");

		GitStatus status = executor.status(plain.toString());
		GitDiff diff = executor.diff(plain.toString());

		assertEquals("", status.getBranch());
		assertEquals(0, status.getModified());
		assertEquals(0, status.getAdded());
		assertEquals(0, status.getDeleted());
		assertEquals(0, diff.getFilesChanged());
	}

	private void write(Path file, String content) throws IOException {
		Files.writeString(file, content, StandardCharsets.UTF_8);
	}

	private void git(Path directory, String... args) throws Exception {
		String[] command = new String[args.length + 1];
		command[0] = "git";
		System.arraycopy(args, 0, command, 1, args.length);
		Process process = new ProcessBuilder(command)
			.directory(directory.toFile())
			.redirectErrorStream(true)
			.start();
		String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
		int exitCode = process.waitFor();
		assertEquals(0, exitCode, "git " + String.join(" ", args) + " failed: " + output);
	}
}
