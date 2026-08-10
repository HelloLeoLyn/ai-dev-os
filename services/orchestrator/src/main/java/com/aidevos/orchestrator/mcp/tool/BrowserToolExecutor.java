package com.aidevos.orchestrator.mcp.tool;

import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.testagent.browser.BrowserTestExecutor;
import com.aidevos.orchestrator.testagent.browser.BrowserTestResult;
import org.springframework.stereotype.Component;

/**
 * Browser tool: routes the operation through the existing BrowserTestExecutor
 * abstraction (OpenClaw browser or Playwright) instead of duplicating the
 * browser pipeline.
 */
@Component
public class BrowserToolExecutor implements McpToolExecutor {

	private final BrowserTestExecutor browserTestExecutor;

	public BrowserToolExecutor(BrowserTestExecutor browserTestExecutor) {
		this.browserTestExecutor = browserTestExecutor;
	}

	@Override
	public ToolDefinition definition() {
		return new ToolDefinition("browser", "Browser", ToolType.BROWSER,
			"Execute a browser operation or UI test through the browser test executor",
			Map.of("testId", "String", "command", "String"),
			Set.of(ToolPermission.EXECUTE));
	}

	@Override
	public ToolExecutionResult execute(ToolExecutionRequest request) {
		String testId = string(request, "testId");
		String command = string(request, "command");
		if (testId == null || testId.isBlank()) {
			return ToolExecutionResult.failure("Missing required parameter: testId", Map.of());
		}
		if (command == null || command.isBlank()) {
			return ToolExecutionResult.failure("Missing required parameter: command", Map.of());
		}
		try {
			BrowserTestResult result = browserTestExecutor.execute(testId, command);
			Map<String, Object> metadata = result.screenshotPath() == null ? Map.of()
				: Map.of("screenshot", result.screenshotPath());
			if (result.succeeded()) {
				return ToolExecutionResult.success(result.output() == null ? "ok"
					: result.output(), metadata);
			}
			return ToolExecutionResult.failure(
				result.errorMessage() == null ? "Browser operation failed" : result.errorMessage(),
				metadata);
		}
		catch (RuntimeException exception) {
			return ToolExecutionResult.failure(exception.getMessage(), Map.of());
		}
	}

	private String string(ToolExecutionRequest request, String key) {
		Object value = request.parameters().get(key);
		return value == null ? null : String.valueOf(value);
	}
}
