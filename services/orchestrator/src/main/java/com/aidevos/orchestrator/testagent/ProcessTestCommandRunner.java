package com.aidevos.orchestrator.testagent;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Local-process implementation of TestCommandRunner. Runs the command via the
 * platform shell with a configurable working directory and timeout. Output is
 * captured and returned for the audit log / test result.
 */
@Component
public class ProcessTestCommandRunner implements TestCommandRunner {

	private final String defaultWorkdir;
	private final Duration timeout;

	public ProcessTestCommandRunner(
			@Value("${testagent.workdir:${user.dir}}") String defaultWorkdir,
			@Value("${testagent.command-timeout:10m}") Duration timeout) {
		this.defaultWorkdir = defaultWorkdir;
		this.timeout = timeout;
	}

	@Override
	public TestCommandResult run(String command, String workdir) {
		if (command == null || command.isBlank()) {
			throw new IllegalStateException("Test command is required");
		}
		ProcessBuilder builder = new ProcessBuilder("sh", "-c", command);
		builder.redirectErrorStream(true);
		String effectiveWorkdir = workdir == null || workdir.isBlank() ? defaultWorkdir : workdir;
		builder.directory(new File(effectiveWorkdir));
		Process process;
		try {
			process = builder.start();
		}
		catch (IOException exception) {
			throw new IllegalStateException("Failed to start test command: " + command, exception);
		}

		Capture capture = new Capture(process);
		capture.start();
		try {
			boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!finished) {
				process.destroyForcibly();
				process.waitFor(5, TimeUnit.SECONDS);
				throw new IllegalStateException(
					"Test command timed out after " + timeout.toSeconds() + "s: " + command);
			}
			return new TestCommandResult(process.exitValue(), capture.join(), "");
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			throw new IllegalStateException("Test command interrupted: " + command, exception);
		}
		finally {
			capture.interrupt();
		}
	}

	private static final class Capture {

		private final Process process;
		private volatile String output = "";
		private Thread thread;

		private Capture(Process process) {
			this.process = process;
		}

		private void start() {
			thread = new Thread(() -> {
				try {
					output = new String(process.getInputStream().readAllBytes(),
						StandardCharsets.UTF_8);
				}
				catch (IOException exception) {
					output = "";
				}
			}, "test-command-output");
			thread.setDaemon(true);
			thread.start();
		}

		private String join() {
			try {
				thread.join(5_000);
			}
			catch (InterruptedException exception) {
				Thread.currentThread().interrupt();
			}
			return output;
		}

		private void interrupt() {
			if (thread != null && thread.isAlive()) {
				thread.interrupt();
			}
		}
	}
}
