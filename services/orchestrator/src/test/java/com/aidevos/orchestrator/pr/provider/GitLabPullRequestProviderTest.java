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
 * GitLab Merge Request provider mapping against a local mock server: verifies
 * the request path/method/headers/body and the response mapping for create,
 * get, close and merge. Never calls the real GitLab API.
 */
class GitLabPullRequestProviderTest {

	private MockWebServer server;
	private GitProviderProperties properties;
	private GitLabPullRequestProvider provider;
	private final ObjectMapper objectMapper = new ObjectMapper();

	@BeforeEach
	void setUp() throws Exception {
		server = new MockWebServer();
		server.start();
		properties = new GitProviderProperties();
		properties.setProvider("gitlab");
		properties.setGitlabToken("gl-token");
		properties.setGitlabProjectId("123");
		properties.setGitlabBaseUrl(server.url("/").toString().replaceAll("/$", ""));
		provider = new GitLabPullRequestProvider(properties, objectMapper);
	}

	@AfterEach
	void tearDown() throws Exception {
		server.shutdown();
	}

	@Test
	void shouldCreateMergeRequest() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(201)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"iid\":7,\"web_url\":\"https://gitlab.com/group/project/-/merge_requests/7\","
				+ "\"state\":\"opened\"}"));

		GitPullRequestResult result = provider.createPullRequest(
			new GitPullRequestRequest("pr-1", "feature/x", "main", "My MR", "desc"));

		assertEquals("7", result.externalId());
		assertEquals("https://gitlab.com/group/project/-/merge_requests/7", result.url());
		assertEquals("opened", result.state());

		RecordedRequest recorded = server.takeRequest();
		assertEquals("POST", recorded.getMethod());
		assertEquals("/projects/123/merge_requests", recorded.getPath());
		assertEquals("gl-token", recorded.getHeader("PRIVATE-TOKEN"));
		JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());
		assertEquals("My MR", body.get("title").asText());
		assertEquals("feature/x", body.get("source_branch").asText());
		assertEquals("main", body.get("target_branch").asText());
		assertEquals("desc", body.get("description").asText());
	}

	@Test
	void shouldGetMergeRequest() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"iid\":7,\"web_url\":\"https://gitlab.com/group/project/-/merge_requests/7\","
				+ "\"state\":\"opened\"}"));

		GitPullRequestResult result = provider.getPullRequest("7");

		assertEquals("7", result.externalId());
		assertEquals("opened", result.state());
		RecordedRequest recorded = server.takeRequest();
		assertEquals("GET", recorded.getMethod());
		assertEquals("/projects/123/merge_requests/7", recorded.getPath());
	}

	@Test
	void shouldCloseMergeRequest() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"iid\":7,\"web_url\":\"https://gitlab.com/group/project/-/merge_requests/7\","
				+ "\"state\":\"closed\"}"));

		GitPullRequestResult result = provider.closePullRequest("7");

		assertEquals("closed", result.state());
		RecordedRequest recorded = server.takeRequest();
		assertEquals("PUT", recorded.getMethod());
		assertEquals("/projects/123/merge_requests/7", recorded.getPath());
		JsonNode body = objectMapper.readTree(recorded.getBody().readUtf8());
		assertEquals("close", body.get("state_event").asText());
	}

	@Test
	void shouldMergeMergeRequest() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"iid\":7,\"web_url\":\"https://gitlab.com/group/project/-/merge_requests/7\","
				+ "\"state\":\"merged\"}"));

		GitPullRequestResult result = provider.mergePullRequest("7");

		assertEquals("7", result.externalId());
		assertEquals("merged", result.state());
		RecordedRequest recorded = server.takeRequest();
		assertEquals("PUT", recorded.getMethod());
		assertEquals("/projects/123/merge_requests/7/merge", recorded.getPath());
	}

	@Test
	void shouldFailOnHttpError() {
		server.enqueue(new MockResponse().setResponseCode(404)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"message\":\"404 Not Found\"}"));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
			() -> provider.getPullRequest("missing"));
		assertTrue(exception.getMessage().contains("HTTP 404"),
			"missing status in: " + exception.getMessage());
	}

	@Test
	void shouldRejectMissingCredentials() {
		GitProviderProperties blank = new GitProviderProperties();
		blank.setProvider("gitlab");
		blank.setGitlabProjectId("123");
		assertThrows(IllegalStateException.class,
			() -> new GitLabPullRequestProvider(blank, objectMapper));
	}
}
