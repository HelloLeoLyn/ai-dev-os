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
 * GitHub REST API pull request provider (selected with
 * aidevos.git.provider=github). Requires GITHUB_TOKEN, GITHUB_OWNER and
 * GITHUB_REPO; a missing credential fails startup with a clear error. Only
 * creates/reads/closes/merges pull requests - never commits or pushes.
 */
@Component
@ConditionalOnProperty(prefix = "aidevos.git", name = "provider", havingValue = "github")
public class GithubPullRequestProvider implements GitProvider {

	private static final String DEFAULT_BASE_URL = "https://api.github.com";

	private final GitProviderProperties properties;
	private final ObjectMapper objectMapper;
	private final HttpClient httpClient;

	@Autowired
	public GithubPullRequestProvider(GitProviderProperties properties, ObjectMapper objectMapper) {
		this(properties, objectMapper, HttpClient.newBuilder()
			.connectTimeout(Duration.ofSeconds(10))
			.build());
	}

	GithubPullRequestProvider(GitProviderProperties properties, ObjectMapper objectMapper,
			HttpClient httpClient) {
		this.properties = properties;
		this.objectMapper = objectMapper;
		this.httpClient = httpClient;
		validate();
	}

	@Override
	public GitPullRequestResult createPullRequest(GitPullRequestRequest request) {
		String path = pullsPath() + "/pulls";
		Map<String, Object> body = Map.of(
			"title", value(request.title()),
			"head", value(request.branch()),
			"base", value(request.targetBranch()),
			"body", value(request.description()));
		return map(send("POST", path, body), null);
	}

	@Override
	public GitPullRequestResult getPullRequest(String externalId) {
		return map(send("GET", pullPath(externalId), null), externalId);
	}

	@Override
	public GitPullRequestResult closePullRequest(String externalId) {
		return map(send("PATCH", pullPath(externalId), Map.of("state", "closed")), externalId);
	}

	@Override
	public GitPullRequestResult mergePullRequest(String externalId) {
		return map(send("PUT", pullPath(externalId) + "/merge", Map.of("merge_method", "merge")),
			externalId);
	}

	private JsonNode send(String method, String path, Map<String, Object> body) {
		try {
			HttpRequest.Builder request = HttpRequest.newBuilder()
				.uri(URI.create(baseUrl() + path))
				.timeout(Duration.ofSeconds(30))
				.header("Accept", "application/vnd.github+json")
				.header("Authorization", "Bearer " + properties.getGithubToken())
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

	private GitPullRequestResult map(JsonNode node, String fallbackExternalId) {
		if (node == null) {
			throw new IllegalStateException("GitHub API returned an empty response");
		}
		String externalId = text(node, "number");
		if (externalId.isBlank() && fallbackExternalId != null) {
			externalId = fallbackExternalId;
		}
		return new GitPullRequestResult(externalId, text(node, "html_url"),
			text(node, "state"));
	}

	private String pullsPath() {
		return "/repos/" + properties.getGithubOwner() + "/" + properties.getGithubRepo();
	}

	private String pullPath(String externalId) {
		return pullsPath() + "/pulls/" + externalId;
	}

	private String baseUrl() {
		String base = properties.getGithubBaseUrl();
		if (base == null || base.isBlank()) {
			return DEFAULT_BASE_URL;
		}
		return base.endsWith("/") ? base.substring(0, base.length() - 1) : base;
	}

	private void validate() {
		if (blank(properties.getGithubToken())) {
			throw new IllegalStateException(
				"GITHUB_TOKEN is required when aidevos.git.provider=github");
		}
		if (blank(properties.getGithubOwner())) {
			throw new IllegalStateException(
				"GITHUB_OWNER is required when aidevos.git.provider=github");
		}
		if (blank(properties.getGithubRepo())) {
			throw new IllegalStateException(
				"GITHUB_REPO is required when aidevos.git.provider=github");
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
