package com.aidevos.orchestrator.testagent.browser;

import java.io.File;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.util.Comparator;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Playwright-backed browser test executor. Runs the Playwright test command
 * through the platform shell and captures the newest PNG from the Playwright
 * output directory into the per-test artifact directory.
 */
@Component
@ConditionalOnProperty(prefix = "testagent.browser", name = "executor",
	havingValue = "playwright", matchIfMissing = true)
public class PlaywrightBrowserTestExecutor implements BrowserTestExecutor {

	private static final String DEFAULT_SCREENSHOTS_SUBDIR = "test-results";

	private static final String SCREENSHOT_NAME = "screenshot.png";

	private final String workdir;
	private final String screenshotsDir;
	private final String artifactsDir;
	private final Duration timeout;
	private final String defaultCommand;

	public PlaywrightBrowserTestExecutor(
			@Value("${testagent.browser.workdir:${user.dir}}") String workdir,
			@Value("${testagent.browser.screenshots-dir:}") String screenshotsDir,
			@Value("${testagent.artifacts-dir:${user.dir}/test-artifacts}") String artifactsDir,
			@Value("${testagent.browser.timeout:15m}") Duration timeout,
			@Value("${testagent.browser.command:npx playwright test}") String defaultCommand) {
		this.workdir = workdir;
		this.screenshotsDir = screenshotsDir;
		this.artifactsDir = artifactsDir;
		this.timeout = timeout;
		this.defaultCommand = defaultCommand;
	}

	@Override
	public BrowserTestResult execute(String testId, String command) {
		String effectiveCommand = command == null || command.isBlank() ? defaultCommand : command.trim();
		Process process;
		try {
			ProcessBuilder builder = new ProcessBuilder("sh", "-c", effectiveCommand);
			builder.redirectErrorStream(true);
			builder.directory(new File(workdir));
			process = builder.start();
		}
		catch (IOException exception) {
			return BrowserTestResult.failure(null,
				"Failed to start browser test command: " + exception.getMessage(), null);
		}

		Capture capture = new Capture(process);
		capture.start();
		try {
			boolean finished = process.waitFor(timeout.toMillis(), TimeUnit.MILLISECONDS);
			if (!finished) {
				process.destroyForcibly();
				process.waitFor(5, TimeUnit.SECONDS);
				return BrowserTestResult.failure(null,
					"Browser test command timed out after " + timeout.toSeconds() + "s", null);
			}
			String output = capture.join();
			String screenshot = captureScreenshot(testId);
			if (process.exitValue() == 0) {
				return BrowserTestResult.success(output, screenshot);
			}
			return BrowserTestResult.failure(output, "exit code " + process.exitValue(), screenshot);
		}
		catch (InterruptedException exception) {
			Thread.currentThread().interrupt();
			process.destroyForcibly();
			return BrowserTestResult.failure(null, "Browser test command interrupted", null);
		}
		finally {
			capture.interrupt();
		}
	}

	private String captureScreenshot(String testId) {
		Path screenshots = screenshotsDirectory();
		if (!Files.isDirectory(screenshots)) {
			return null;
		}
		Path newest;
		try (Stream<Path> files = Files.list(screenshots)) {
			newest = files.filter(Files::isRegularFile)
				.filter(path -> path.getFileName().toString().toLowerCase().endsWith(".png"))
				.max(Comparator.comparingLong(this::lastModified))
				.orElse(null);
		}
		catch (IOException exception) {
			return null;
		}
		if (newest == null) {
			return null;
		}
		try {
			Path targetDir = Path.of(artifactsDir, testId);
			Files.createDirectories(targetDir);
			Path target = targetDir.resolve(SCREENSHOT_NAME);
			Files.copy(newest, target, StandardCopyOption.REPLACE_EXISTING);
			return target.toAbsolutePath().toString();
		}
		catch (IOException exception) {
			return null;
		}
	}

	private Path screenshotsDirectory() {
		if (screenshotsDir != null && !screenshotsDir.isBlank()) {
			return Path.of(screenshotsDir);
		}
		return Path.of(workdir, DEFAULT_SCREENSHOTS_SUBDIR);
	}

	private long lastModified(Path path) {
		try {
			return Files.getLastModifiedTime(path).toMillis();
		}
		catch (IOException exception) {
			return 0L;
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
			}, "browser-test-output");
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
