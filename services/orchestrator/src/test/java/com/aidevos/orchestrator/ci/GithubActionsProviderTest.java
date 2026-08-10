package com.aidevos.orchestrator.ci;

import com.aidevos.orchestrator.pr.provider.GitProviderProperties;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * GitHub Actions provider mapping against a local mock server: verifies the
 * check-run association, status/conclusion mapping and report lookup. Never
 * calls the real GitHub API.
 */
class GithubActionsProviderTest {

	private MockWebServer server;
	private GitProviderProperties properties;
	private GithubActionsProvider provider;
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
		provider = new GithubActionsProvider(properties, objectMapper);
	}

	@AfterEach
	void tearDown() throws Exception {
		server.shutdown();
	}

	@Test
	void shouldAssociateCommitWithLatestCheckRun() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"total_count\":1,\"check_runs\":[{\"id\":99,"
				+ "\"html_url\":\"https://github.com/owner/repo/actions/runs/99\","
				+ "\"status\":\"completed\",\"conclusion\":\"success\",\"name\":\"CI\"}]}"));

		CiTriggerResult result = provider.trigger(
			new CiTriggerRequest("pr-1", "main", "abc123"));

		assertEquals("99", result.pipelineId());
		assertEquals("https://github.com/owner/repo/actions/runs/99", result.reportUrl());
		RecordedRequest recorded = server.takeRequest();
		assertEquals("GET", recorded.getMethod());
		assertEquals("/repos/owner/repo/commits/abc123/check-runs", recorded.getPath());
		assertEquals("Bearer gh-token", recorded.getHeader("Authorization"));
	}

	@Test
	void shouldReturnEmptyTriggerWhenNoCheckRuns() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("{\"total_count\":0,\"check_runs\":[]}"));

		CiTriggerResult result = provider.trigger(
			new CiTriggerRequest("pr-1", "main", "abc123"));

		assertEquals("", result.pipelineId());
		assertEquals("", result.reportUrl());
	}

	@Test
	void shouldMapRunStatuses() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":99,\"html_url\":\"u\",\"status\":\"completed\","
				+ "\"conclusion\":\"success\"}"));
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":99,\"html_url\":\"u\",\"status\":\"in_progress\"}"));
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":99,\"html_url\":\"u\",\"status\":\"completed\","
				+ "\"conclusion\":\"failure\"}"));
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":99,\"html_url\":\"u\",\"status\":\"completed\","
				+ "\"conclusion\":\"cancelled\"}"));
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":99,\"html_url\":\"u\",\"status\":\"queued\"}"));

		assertEquals(CiStatus.SUCCESS, provider.getStatus("99").status());
		assertEquals(CiStatus.RUNNING, provider.getStatus("99").status());
		assertEquals(CiStatus.FAILED, provider.getStatus("99").status());
		assertEquals(CiStatus.CANCELLED, provider.getStatus("99").status());
		assertEquals(CiStatus.PENDING, provider.getStatus("99").status());
		assertEquals("/repos/owner/repo/actions/runs/99", server.takeRequest().getPath());
	}

	@Test
	void shouldGetReport() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":99,\"html_url\":\"https://github.com/owner/repo/actions/runs/99\","
				+ "\"display_title\":\"CI build\"}"));

		CiReport report = provider.getReport("99");

		assertEquals("https://github.com/owner/repo/actions/runs/99", report.reportUrl());
		assertEquals("CI build", report.summary());
	}

	@Test
	void shouldFailOnHttpError() {
		server.enqueue(new MockResponse().setResponseCode(404)
			.setBody("{\"message\":\"Not Found\"}"));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
			() -> provider.getStatus("missing"));
		assertTrue(exception.getMessage().contains("HTTP 404"),
			"missing status in: " + exception.getMessage());
	}

	@Test
	void shouldRejectMissingCredentials() {
		GitProviderProperties blank = new GitProviderProperties();
		blank.setProvider("github");
		blank.setGithubOwner("owner");
		blank.setGithubRepo("repo");
		assertThrows(IllegalStateException.class,
			() -> new GithubActionsProvider(blank, objectMapper));
	}
}
