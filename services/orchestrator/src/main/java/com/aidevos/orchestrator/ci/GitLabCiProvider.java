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
 * GitLab CI provider (aidevos.ci.provider=gitlab): associates a commit with
 * its latest pipeline, maps pipeline status to CiStatus and exposes the
 * report url. Requires GITLAB_TOKEN and GITLAB_PROJECT_ID; a missing
 * credential fails startup with a clear error. This phase only observes
 * status; it never triggers repairs or modifies code.
 */
@Component
@ConditionalOnProperty(prefix = "aidevos.ci", name = "provider", havingValue = "gitlab")
public class GitLabCiProvider implements CiProvider {

	private static final String DEFAULT_BASE_URL = "https://gitlab.com/api/v4";

	private final GitProviderProperties properties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	@Autowired
	public GitLabCiProvider(GitProviderProperties properties, ObjectMapper objectMapper) {
		this(properties, objectMapper, HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build());
	}

	GitLabCiProvider(GitProviderProperties properties, ObjectMapper objectMapper,
			HttpClient httpClient) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
		validate();
	}

	@Override
	public CiTriggerResult trigger(CiTriggerRequest request) {
		// Associate the commit with its latest pipeline for the ref.
		String path = "/projects/" + projectId() + "/pipelines?sha=" + value(request.commitHash());
		JsonNode node = send("GET", path, null);
		if (node != null && node.isArray() && !node.isEmpty()) {
			JsonNode first = node.get(0);
			return new CiTriggerResult(text(first, "id"), text(first, "web_url"));
		}
		return new CiTriggerResult("", "");
	}

	@Override
	public CiRunResult getStatus(String pipelineId) {
		JsonNode node = send("GET", pipelinePath(pipelineId), null);
		return new CiRunResult(mapStatus(node), text(node, "web_url"));
	}

	@Override
	public CiReport getReport(String pipelineId) {
		JsonNode node = send("GET", pipelinePath(pipelineId), null);
		return new CiReport(text(node, "web_url"), "Pipeline " + text(node, "id"));
	}

	private CiStatus mapStatus(JsonNode node) {
		String status = text(node, "status");
		return switch (status) {
			case "success" -> CiStatus.SUCCESS;
			case "failed" -> CiStatus.FAILED;
			case "canceled", "cancelled", "skipped", "manual" -> CiStatus.CANCELLED;
			case "running" -> CiStatus.RUNNING;
			default -> CiStatus.PENDING;
		};
	}

	private String pipelinePath(String pipelineId) {
		return "/projects/" + projectId() + "/pipelines/" + pipelineId;
	}

	private String projectId() {
		return properties.getGitlabProjectId().replace("/", "%2F");
	}

	private String baseUrl() {
		String base = properties.getGitlabBaseUrl();
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
				.header("PRIVATE-TOKEN", properties.getGitlabToken());
			request.method(method, HttpRequest.BodyPublishers.noBody());
			HttpResponse<String> response = httpClient.send(request.build(),
				HttpResponse.BodyHandlers.ofString());
			if (response.statusCode() < 200 || response.statusCode() >= 300) {
				throw new IllegalStateException("GitLab API " + method + " " + path
					+ " failed with HTTP " + response.statusCode() + ": " + response.body());
			}
			String content = response.body();
			return content == null || content.isBlank() ? null : objectMapper.readTree(content);
		}
		catch (IllegalStateException exception) {
			throw exception;
		}
		catch (Exception exception) {
			throw new IllegalStateException("GitLab API " + method + " " + path
				+ " failed: " + message(exception), exception);
		}
	}

	private void validate() {
		if (blank(properties.getGitlabToken())) {
			throw new IllegalStateException(
				"GITLAB_TOKEN is required when aidevos.ci.provider=gitlab");
		}
		if (blank(properties.getGitlabProjectId())) {
			throw new IllegalStateException(
				"GITLAB_PROJECT_ID is required when aidevos.ci.provider=gitlab");
		}
	}

	private String text(JsonNode node, String field) {
		if (node == null) {
			return "";
		}
		JsonNode value = node.get(field);
		return value == null || value.isNull() ? "" : value.asText();
	}

	private String value(String value) {
		return value == null ? "" : value;
	}

	private boolean blank(String value) {
		return value == null || value.isBlank();
	}

	private String message(Exception exception) {
		return exception.getMessage() == null || exception.getMessage().isBlank()
			? exception.getClass().getSimpleName() : exception.getMessage();
	}
}
