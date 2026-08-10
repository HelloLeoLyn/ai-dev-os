package com.aidevos.orchestrator.pr.provider;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

/**
 * GitLab Merge Request API provider (selected with
 * aidevos.git.provider=gitlab). Requires GITLAB_TOKEN and GITLAB_PROJECT_ID;
 * a missing credential fails startup with a clear error. Only
 * creates/reads/closes/merges merge requests - never commits or pushes.
 */
@Component
@ConditionalOnProperty(prefix = "aidevos.git", name = "provider", havingValue = "gitlab")
public class GitLabPullRequestProvider implements GitProvider {

	private static final String DEFAULT_BASE_URL = "https://gitlab.com/api/v4";

	private final GitProviderProperties properties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	@Autowired
	public GitLabPullRequestProvider(GitProviderProperties properties, ObjectMapper objectMapper) {
		this(properties, objectMapper, HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build());
	}

	GitLabPullRequestProvider(GitProviderProperties properties, ObjectMapper objectMapper,
			HttpClient httpClient) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
		validate();
	}

	@Override
	public GitPullRequestResult createPullRequest(GitPullRequestRequest request) {
		String path = mergeRequestsPath();
		Map<String, Object> body = Map.of(
			"source_branch", value(request.branch()),
			"target_branch", value(request.targetBranch()),
			"title", value(request.title()),
			"description", value(request.description()));
		return map(send("POST", path, body), null);
	}

	@Override
	public GitPullRequestResult getPullRequest(String externalId) {
		return map(send("GET", mergeRequestPath(externalId), null), externalId);
	}

	@Override
	public GitPullRequestResult closePullRequest(String externalId) {
		return map(send("PUT", mergeRequestPath(externalId),
			Map.of("state_event", "close")), externalId);
	}

	@Override
	public GitPullRequestResult mergePullRequest(String externalId) {
		return map(send("PUT", mergeRequestPath(externalId) + "/merge", Map.of()), externalId);
	}

	private JsonNode send(String method, String path, Map<String, Object> body) {
		try {
			HttpRequest.Builder request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl() + path))
				.timeout(Duration.ofSeconds(30))
				.header("PRIVATE-TOKEN", properties.getGitlabToken())
				.header("Content-Type", "application/json");
			if (body == null) {
				request.method(method, HttpRequest.BodyPublishers.noBody());
			}
			else {
				request.method(method, HttpRequest.BodyPublishers.ofString(
					objectMapper.writeValueAsString(body)));
			}
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

	private GitPullRequestResult map(JsonNode node, String fallbackExternalId) {
		if (node == null) {
			throw new IllegalStateException("GitLab API returned an empty response");
		}
		String externalId = text(node, "iid");
		if (externalId.isBlank() && fallbackExternalId != null) {
			externalId = fallbackExternalId;
		}
		return new GitPullRequestResult(externalId, text(node, "web_url"),
			text(node, "state"));
	}

	private String mergeRequestsPath() {
		return "/projects/" + projectId() + "/merge_requests";
	}

	private String mergeRequestPath(String externalId) {
		return mergeRequestsPath() + "/" + externalId;
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

	private void validate() {
		if (blank(properties.getGitlabToken())) {
			throw new IllegalStateException(
				"GITLAB_TOKEN is required when aidevos.git.provider=gitlab");
		}
		if (blank(properties.getGitlabProjectId())) {
			throw new IllegalStateException(
				"GITLAB_PROJECT_ID is required when aidevos.git.provider=gitlab");
		}
	}

	private String text(JsonNode node, String field) {
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
