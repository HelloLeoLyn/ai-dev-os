package com.aidevos.orchestrator.execution.tool;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

import com.aidevos.orchestrator.execution.FailureClass;
import com.aidevos.orchestrator.execution.FailureClassifier;
import com.aidevos.orchestrator.executor.command.CommandExecutor;
import com.aidevos.orchestrator.executor.command.CommandOptions;
import com.aidevos.orchestrator.executor.command.CommandResult;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

/**
 * Deterministic tool execution: git / maven / npm / shell / http health /
 * workspace status / validation run directly through the command executor or
 * an HTTP client. No LLM is involved and no tool provider is required.
 */
@Service
public class ToolExecutionService {

	private static final Duration DEFAULT_TIMEOUT = Duration.ofMinutes(2);
	private static final Duration HEALTH_TIMEOUT = Duration.ofSeconds(10);

	private final CommandExecutor commandExecutor;
	private final FailureClassifier classifier;
	private final HttpClient httpClient;

	@Autowired
	public ToolExecutionService(CommandExecutor commandExecutor, FailureClassifier classifier) {
		this(commandExecutor, classifier, HttpClient.newBuilder()
			.connectTimeout(DEFAULT_TIMEOUT).build());
	}

	public ToolExecutionService(CommandExecutor commandExecutor, FailureClassifier classifier,
			HttpClient httpClient) {
		this.commandExecutor = commandExecutor;
		this.classifier = classifier;
		this.httpClient = httpClient;
	}

	public ToolExecutionResult execute(ToolExecutionRequest request) {
		Instant started = Instant.now();
		try {
			if (request.tool() == DeterministicTool.HTTP_HEALTH) {
				return executeHealth(request, started);
			}
			CommandOptions options = new CommandOptions();
			options.setCommand(command(request));
			options.setWorkingDirectory(request.workdir());
			options.setTimeout(request.timeout() == null ? DEFAULT_TIMEOUT : request.timeout());
			options.setEnvironment(request.environment());
			CommandResult result = commandExecutor.execute(options);
			return new ToolExecutionResult(request.tool(), result.isSuccess(),
				result.getExitCode(), result.getOutput(), result.getError(),
				Duration.between(started, Instant.now()).toMillis(),
				classifier.classify(request.tool(), result));
		}
		catch (RuntimeException exception) {
			return new ToolExecutionResult(request.tool(), false, -1, null,
				exception.getMessage(), Duration.between(started, Instant.now()).toMillis(),
				classifier.classify(request.tool(), exception));
		}
	}

	private ToolExecutionResult executeHealth(ToolExecutionRequest request, Instant started) {
		String url = request.arguments().isEmpty() ? null : request.arguments().get(0);
		if (url == null || url.isBlank()) {
			return new ToolExecutionResult(request.tool(), false, -1, null,
				"HTTP_HEALTH requires a URL argument", 0, FailureClass.HEALTH_CHECK_FAILED);
		}
		try {
			HttpRequest httpRequest = HttpRequest.newBuilder(URI.create(url))
				.GET().timeout(HEALTH_TIMEOUT).build();
			HttpResponse<String> response = httpClient.send(httpRequest,
				HttpResponse.BodyHandlers.ofString());
			boolean success = response.statusCode() >= 200 && response.statusCode() < 300;
			return new ToolExecutionResult(request.tool(), success, response.statusCode(),
				response.body(), null, Duration.between(started, Instant.now()).toMillis(),
				success ? null : FailureClass.HEALTH_CHECK_FAILED);
		}
		catch (Exception exception) {
			return new ToolExecutionResult(request.tool(), false, -1, null,
				exception.getMessage(), Duration.between(started, Instant.now()).toMillis(),
				FailureClass.HEALTH_CHECK_FAILED);
		}
	}

	private List<String> command(ToolExecutionRequest request) {
		List<String> args = request.arguments();
		return switch (request.tool()) {
			case GIT -> prepend("git", args.isEmpty() ? List.of("status") : args);
			case MAVEN -> prepend("mvn", args);
			case NPM -> prepend("npm", args);
			case SHELL -> List.of("sh", "-c", String.join(" ", args));
			case WORKSPACE -> List.of("git", "status", "--porcelain");
			case VALIDATION -> List.of("git", "diff", "--check");
			case HTTP_HEALTH -> throw new IllegalStateException("HTTP_HEALTH is executed directly");
		};
	}

	private static List<String> prepend(String first, List<String> rest) {
		List<String> result = new ArrayList<>(rest.size() + 1);
		result.add(first);
		result.addAll(rest);
		return result;
	}
}
