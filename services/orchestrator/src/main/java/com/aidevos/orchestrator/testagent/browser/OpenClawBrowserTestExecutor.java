package com.aidevos.orchestrator.testagent.browser;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.aidevos.orchestrator.browser.BrowserResultMapper;
import com.aidevos.orchestrator.browser.BrowserTaskPromptBuilder;
import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionResult;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskRequest;
import com.aidevos.orchestrator.openclaw.model.OpenClawTaskResult;
import com.aidevos.orchestrator.openclaw.service.OpenClawTaskService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * Browser tests through the existing OpenClaw gateway pipeline. The test
 * command is treated as a browser operation (URL or JSON browser parameters);
 * the prompt is built with {@link BrowserTaskPromptBuilder} and executed via
 * {@link OpenClawTaskService}. OpenClaw core is not modified.
 */
@Component
@ConditionalOnProperty(prefix = "testagent.browser", name = "executor", havingValue = "openclaw")
public class OpenClawBrowserTestExecutor implements BrowserTestExecutor {

	private static final String SCREENSHOT_TYPE = "screenshot";

	private final OpenClawTaskService openClawTaskService;
	private final BrowserTaskPromptBuilder promptBuilder;
	private final BrowserResultMapper resultMapper;
	private final ObjectMapper objectMapper;
	private final String agentId;

	public OpenClawBrowserTestExecutor(OpenClawTaskService openClawTaskService,
			BrowserTaskPromptBuilder promptBuilder, BrowserResultMapper resultMapper,
			ObjectMapper objectMapper,
			@Value("${testagent.browser.openclaw-agent:browser-agent}") String agentId) {
		this.openClawTaskService = openClawTaskService;
		this.promptBuilder = promptBuilder;
		this.resultMapper = resultMapper;
		this.objectMapper = objectMapper;
		this.agentId = agentId;
	}

	@Override
	public BrowserTestResult execute(String testId, String command) {
		try {
			Map<String, Object> parameters = browserParameters(command);
			String prompt = promptBuilder.build("UI test " + testId, Map.of("browser", parameters));
			OpenClawTaskResult taskResult = openClawTaskService
				.execute(new OpenClawTaskRequest(agentId, prompt))
				.join();
			if (!taskResult.successful()) {
				return BrowserTestResult.failure(null,
					"OpenClaw browser task finished with status " + taskResult.status(), null);
			}
			ExecutionResult holder = new ExecutionResult();
			resultMapper.map(taskResult.output(), holder);
			String screenshot = firstScreenshot(holder.getArtifacts());
			return BrowserTestResult.success(holder.getOutput(), screenshot);
		}
		catch (RuntimeException exception) {
			String error = exception.getMessage() == null || exception.getMessage().isBlank()
				? exception.getClass().getSimpleName() : exception.getMessage();
			return BrowserTestResult.failure(null, error, null);
		}
	}

	private Map<String, Object> browserParameters(String command) {
		if (command == null || command.isBlank()) {
			return Map.of("action", "navigate");
		}
		String trimmed = command.trim();
		if (trimmed.startsWith("{")) {
			try {
				JsonNode node = objectMapper.readTree(trimmed);
				if (node.isObject()) {
					Map<String, Object> parameters = new LinkedHashMap<>();
					node.properties().forEach(entry ->
						parameters.put(entry.getKey(), entry.getValue().asText()));
					parameters.putIfAbsent("action", "navigate");
					return parameters;
				}
			}
			catch (Exception ignored) {
				// Fall through to URL handling when the command is not valid JSON.
			}
		}
		return Map.of("action",
			trimmed.toLowerCase(Locale.ROOT).contains("screenshot") ? "screenshot" : "navigate",
			"url", trimmed);
	}

	private String firstScreenshot(List<ExecutionArtifact> artifacts) {
		for (ExecutionArtifact artifact : artifacts) {
			if (SCREENSHOT_TYPE.equals(artifact.getType()) && artifact.getUri() != null) {
				return artifact.getUri();
			}
		}
		return null;
	}
}
