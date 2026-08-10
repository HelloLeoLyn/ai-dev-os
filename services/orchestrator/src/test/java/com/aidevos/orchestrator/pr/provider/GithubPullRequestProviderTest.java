package com.aidevos.orchestrator.pr.provider;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GitHub REST provider mapping against a local mock server: verifies the
 * request path/method/headers/body and the response mapping for create, get,
 * close and merge. Never calls the real GitHub API.
 */
class GithubPullRequestProviderTest {

	private MockWebServer server;
	private GitProviderProperties properties;
	private GithubPullRequestProvider provider;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() throws Exception {
		server = new MockWebServer();
		server.start();
		properties = new GitProviderProperties();
		properties.setProvider("github");
		properties.setGithubToken("gh-token");
		properties.setGithubOwner("owner");
		properties.setGithubRepo("repo");
		properties.setGithubBaseUrl(server.url("/").toString().replaceAll("/$", ""));
		provider = new GithubPullRequestProvider(properties, objectMapper);
	}

	@AfterEach
	void tearDown() throws Exception {
		server.shutdown();
	}

	@Test
	void shouldCreatePullRequest() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(201)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"number\":42,\"html_url\":\"https://github.com/owner/repo/pull/42\","
				+ "\"state\":\"open\"}"));

		GitPullRequestResult result = provider.createPullRequest(
			new GitPullRequestRequest("pr-1", "feature/x", "main", "My PR", "desc"));

		assertEquals("42", result.externalId());
		assertEquals("https://github.com/owner/repo/pull/42", result.url());
		assertEquals("open", result.state());

		RecordedRequest recorded = server.takeRequest();
		assertEquals("POST", recorded.getMethod());
		assertEquals("/repos/owner/repo/pulls", recorded.getPath());
		assertEquals("Bearer gh-token", recorded.getHeader("Authorization"));
		assertEquals("application/vnd.github+json", recorded.getHeader("Accept"));
		JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());
		assertEquals("My PR", body.get("title").asText());
		assertEquals("feature/x", body.get("head").asText());
		assertEquals("main", body.get("base").asText());
		assertEquals("desc", body.get("body").asText());
	}

	@Test
	void shouldGetPullRequest() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"number\":42,\"html_url\":\"https://github.com/owner/repo/pull/42\","
				+ "\"state\":\"open\"}"));

		GitPullRequestResult result = provider.getPullRequest("42");

		assertEquals("42", result.externalId());
		assertEquals("open", result.state());
		RecordedRequest recorded = server.takeRequest();
		assertEquals("GET", recorded.getMethod());
		assertEquals("/repos/owner/repo/pulls/42", recorded.getPath());
	}

	@Test
	void shouldClosePullRequest() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"number\":42,\"html_url\":\"https://github.com/owner/repo/pull/42\","
				+ "\"state\":\"closed\"}"));

		GitPullRequestResult result = provider.closePullRequest("42");

		assertEquals("closed", result.state());
		RecordedRequest recorded = server.takeRequest();
		assertEquals("PATCH", recorded.getMethod());
		assertEquals("/repos/owner/repo/pulls/42", recorded.getPath());
		JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());
		assertEquals("closed", body.get("state").asText());
	}

	@Test
	void shouldMergePullRequest() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"merged\":true,\"sha\":\"abc123\"}"));

		GitPullRequestResult result = provider.mergePullRequest("42");

		assertEquals("42", result.externalId());
		RecordedRequest recorded = server.takeRequest();
		assertEquals("PUT", recorded.getMethod());
		assertEquals("/repos/owner/repo/pulls/42/merge", recorded.getPath());
	}

	@Test
	void shouldFailOnHttpError() {
		server.enqueue(new MockResponse().setResponseCode(422)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"message\":\"Validation Failed\"}"));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
			() -> provider.createPullRequest(
				new GitPullRequestRequest("pr-1", "feature/x", "main", "My PR", "desc")));
		assertTrue(exception.getMessage().contains("HTTP 422"),
			"missing status in: " + exception.getMessage());
	}

	@Test
	void shouldRejectMissingCredentials() {
		GitProviderProperties blank = new GitProviderProperties();
		blank.setProvider("github");
		blank.setGithubOwner("owner");
		blank.setGithubRepo("repo");
		assertThrows(IllegalStateException.class,
			() -> new GithubPullRequestProvider(blank, objectMapper));
	}
}
