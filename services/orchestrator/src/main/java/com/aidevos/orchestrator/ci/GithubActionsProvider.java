package com.aidevos.orchestrator.ci;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

import com.aidevos.orchestrator.pr.provider.GitProviderProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GitHub Actions CI provider (aidevos.ci.provider=github): associates a
 * commit with its latest check run, maps run status/conclusion to CiStatus
 * and exposes the run report url. Requires GITHUB_TOKEN, GITHUB_OWNER and
 * GITHUB_REPO; a missing credential fails startup with a clear error. This
 * phase only observes status; it never triggers repairs or modifies code.
 */
@Component
@ConditionalOnProperty(prefix = "aidevos.ci", name = "provider", havingValue = "github")
public class GithubActionsProvider implements CiProvider {

	private static final String DEFAULT_BASE_URL = "https://api.github.com";

	private final GitProviderProperties properties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	@Autowired
	public GithubActionsProvider(GitProviderProperties properties, ObjectMapper objectMapper) {
		this(properties, objectMapper, HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build());
	}

	GithubActionsProvider(GitProviderProperties properties, ObjectMapper objectMapper,
			HttpClient httpClient) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
		validate();
	}

	@Override
	public CiTriggerResult trigger(CiTriggerRequest request) {
		// Associate the commit with its latest GitHub check run.
		String path = "/repos/" + owner() + "/" + repo()
			+ "/commits/" + value(request.commitHash()) + "/check-runs";
		JsonNode node = send("GET", path, null);
		JsonNode runs = node == null ? null : node.path("check_runs");
		if (runs != null && runs.isArray() && !runs.isEmpty()) {
			JsonNode first = runs.get(0);
			return new CiTriggerResult(text(first, "id"), text(first, "html_url"));
		}
		return new CiTriggerResult("", "");
	}

	@Override
	public CiRunResult getStatus(String pipelineId) {
		JsonNode node = send("GET", runsPath(pipelineId), null);
		return new CiRunResult(mapStatus(node), text(node, "html_url"));
	}

	@Override
	public CiReport getReport(String pipelineId) {
		JsonNode node = send("GET", runsPath(pipelineId), null);
		return new CiReport(text(node, "html_url"), text(node, "display_title"));
	}

	private CiStatus mapStatus(JsonNode node) {
		String status = text(node, "status");
		String conclusion = text(node, "conclusion");
		if ("completed".equals(status)) {
			return switch (conclusion) {
				case "success" -> CiStatus.SUCCESS;
				case "failure" -> CiStatus.FAILED;
				default -> CiStatus.CANCELLED;
			};
		}
		if ("in_progress".equals(status)) {
			return CiStatus.RUNNING;
		}
		return CiStatus.PENDING;
	}

	private String runsPath(String pipelineId) {
		return "/repos/" + owner() + "/" + repo() + "/actions/runs/" + pipelineId;
	}

	private String owner() {
		return properties.getGithubOwner();
	}

	private String repo() {
		return properties.getGithubRepo();
	}

	private String baseUrl() {
		String base = properties.getGithubBaseUrl();
		if (base == null || base.isBlank()) {
			return DEFAULT_BASE_URL;
		}
		return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	private JsonNode send(String method, String path, Object ignoredBody) {
		try {
			HttpRequest.Builder request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl() + path))
				.timeout(Duration.ofSeconds(30))
				.header("Accept", "application/vnd.github+json")
				.header("Authorization", "Bearer " + properties.getGithubToken());
			request.method(method, HttpRequest.BodyPublishers.noBody());
			HttpResponse<String> response = httpClient.send(request.build(),
				HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("GitHub API " + method + " " + path
					+ " failed with HTTP " + response.statusCode() + ": " + response.body());
			}
			String content = response.body();
			return content == null || content.isBlank() ? null : objectMapper.readTree(content);
		}
		catch (IllegalStateException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalStateException("GitHub API " + method + " " + path
				+ " failed: " + message(exception), exception);
		}
	}

	private void validate() {
		if (blank(properties.getGithubToken())) {
			throw new IllegalStateException(
				"GITHUB_TOKEN is required when aidevos.ci.provider=github");
		}
		if (blank(properties.getGithubOwner())) {
			throw new IllegalStateException(
				"GITHUB_OWNER is required when aidevos.ci.provider=github");
		}
		if (blank(properties.getGithubRepo())) {
			throw new IllegalStateException(
				"GITHUB_REPO is required when aidevos.ci.provider=github");
		}
	}

	private String text(JsonNode node, String field) {
		if (node == null) {
			return "";
		}
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? "" : value.asText();
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

	private String message(Exception exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}
}
