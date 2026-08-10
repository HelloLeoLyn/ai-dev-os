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
 * GitLab CI provider mapping against a local mock server: verifies the
 * pipeline association, status mapping and report lookup. Never calls the
 * real GitLab API.
 */
class GitLabCiProviderTest {

	private MockWebServer server;
	private GitProviderProperties properties;
	private GitLabCiProvider provider;
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
		provider = new GitLabCiProvider(properties, objectMapper);
	}

	@AfterEach
	void tearDown() throws Exception {
		server.shutdown();
	}

	@Test
	void shouldAssociateCommitWithLatestPipeline() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("[{\"id\":55,\"web_url\":\"https://gitlab.com/group/project/-/pipelines/55\","
				+ "\"status\":\"running\",\"sha\":\"abc123\"}]"));

		CiTriggerResult result = provider.trigger(
			new CiTriggerRequest("pr-1", "main", "abc123"));

		assertEquals("55", result.pipelineId());
		assertEquals("https://gitlab.com/group/project/-/pipelines/55", result.reportUrl());
		RecordedRequest recorded = server.takeRequest();
		assertEquals("GET", recorded.getMethod());
		assertEquals("/projects/123/pipelines?sha=abc123", recorded.getPath());
		assertEquals("gl-token", recorded.getHeader("PRIVATE-TOKEN"));
	}

	@Test
	void shouldReturnEmptyTriggerWhenNoPipelines() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setHeader("Content-Type", "application/json")
			.setBody("[]"));

		CiTriggerResult result = provider.trigger(
			new CiTriggerRequest("pr-1", "main", "abc123"));

		assertEquals("", result.pipelineId());
		assertEquals("", result.reportUrl());
	}

	@Test
	void shouldMapPipelineStatuses() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":55,\"web_url\":\"u\",\"status\":\"success\"}"));
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":55,\"web_url\":\"u\",\"status\":\"running\"}"));
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":55,\"web_url\":\"u\",\"status\":\"failed\"}"));
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":55,\"web_url\":\"u\",\"status\":\"canceled\"}"));
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":55,\"web_url\":\"u\",\"status\":\"pending\"}"));

		assertEquals(CiStatus.SUCCESS, provider.getStatus("55").status());
		assertEquals(CiStatus.RUNNING, provider.getStatus("55").status());
		assertEquals(CiStatus.FAILED, provider.getStatus("55").status());
		assertEquals(CiStatus.CANCELLED, provider.getStatus("55").status());
		assertEquals(CiStatus.PENDING, provider.getStatus("55").status());
		assertEquals("/projects/123/pipelines/55", server.takeRequest().getPath());
	}

	@Test
	void shouldGetReport() throws Exception {
		server.enqueue(new MockResponse().setResponseCode(200)
			.setBody("{\"id\":55,\"web_url\":\"https://gitlab.com/group/project/-/pipelines/55\"}"));

		CiReport report = provider.getReport("55");

		assertEquals("https://gitlab.com/group/project/-/pipelines/55", report.reportUrl());
		assertEquals("Pipeline 55", report.summary());
	}

	@Test
	void shouldFailOnHttpError() {
		server.enqueue(new MockResponse().setResponseCode(500)
			.setBody("{\"message\":\"Internal Server Error\"}"));

		IllegalStateException exception = assertThrows(IllegalStateException.class,
			() -> provider.getStatus("55"));
		assertTrue(exception.getMessage().contains("HTTP 500"),
			"missing status in: " + exception.getMessage());
	}

	@Test
	void shouldRejectMissingCredentials() {
		GitProviderProperties blank = new GitProviderProperties();
		blank.setProvider("gitlab");
		blank.setGitlabProjectId("123");
		assertThrows(IllegalStateException.class,
			() -> new GitLabCiProvider(blank, objectMapper));
	}
}
