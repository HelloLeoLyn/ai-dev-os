package com.aidevos.orchestrator.validation.browser;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.browser.BrowserResultMapper;
import com.aidevos.orchestrator.browser.BrowserTaskPromptBuilder;
import com.aidevos.orchestrator.execution.ArtifactContentLimiter;
import com.aidevos.orchestrator.openclaw.client.OpenClawWebSocketClient;
import com.aidevos.orchestrator.openclaw.config.OpenClawProperties;
import com.aidevos.orchestrator.openclaw.service.OpenClawTaskService;
import com.aidevos.orchestrator.taskcenter.*;
import com.aidevos.orchestrator.testagent.browser.OpenClawBrowserTestExecutor;
import com.aidevos.orchestrator.validation.*;
import com.aidevos.orchestrator.validation.provider.ProjectCapabilityDetector;
import com.aidevos.orchestrator.workspace.*;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.api.io.TempDir;
import tools.jackson.databind.ObjectMapper;

@EnabledIfEnvironmentVariable(named = "OPENCLAW_GATEWAY_TOKEN", matches = ".+")
class RealBrowserAcceptanceEndToEndTest {
	@TempDir Path tempDir;

	@Test void realPassFailScreenshotAuditAndReadOnlyWorkspace() throws Exception {
		Path workspace = Files.createDirectories(tempDir.resolve("workspace"));
		Files.writeString(workspace.resolve("README.md"), "browser acceptance fixture\n");
		git(workspace, "init", "-b", "main"); git(workspace, "config", "user.email", "browser@example.test");
		git(workspace, "config", "user.name", "Browser Fixture"); git(workspace, "add", "."); git(workspace, "commit", "-m", "fixture");
		GitState before = state(workspace);
		HttpServer server = HttpServer.create(new InetSocketAddress("0.0.0.0", 0), 0);
		server.createContext("/", exchange -> { byte[] body = page().getBytes(StandardCharsets.UTF_8); exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8"); exchange.sendResponseHeaders(200, body.length); exchange.getResponseBody().write(body); exchange.close(); });
		server.start();
		try {
			String url = "http://127.0.0.1:" + server.getAddress().getPort() + "/";
			Fixture fixture = fixture(workspace, url);
			ValidationRun pass = fixture.service.start("task-browser", "pass");
			ValidationCheck passCheck = browser(pass);
			assertEquals(ValidationStatus.SUCCESS, passCheck.getStatus(), passCheck.getErrorMessage());
			assertFalse(passCheck.getArtifactIds().isEmpty());
			assertTrue(fixture.events.query(EventQuery.all()).stream().anyMatch(e -> "task-browser".equals(e.taskId()) && e.type() == EventType.BROWSER_VALIDATION_COMPLETED));

			ValidationRun fail = fixture.service.start("task-browser", "fail");
			ValidationCheck failCheck = browser(fail);
			assertEquals(ValidationStatus.FAILED, failCheck.getStatus(), failCheck.getErrorMessage());
			assertNotNull(failCheck.getErrorMessage());
			assertFalse(failCheck.getArtifactIds().isEmpty());
			assertTrue(failCheck.getMetadata().toString().contains("screenshotArtifactId"));
			assertEquals(before, state(workspace));
		}
		finally { server.stop(0); }
	}

	private Fixture fixture(Path path, String url) {
		ObjectMapper mapper = new ObjectMapper(); OpenClawProperties openClaw = new OpenClawProperties();
		openClaw.setGatewayUrl("ws://127.0.0.1:18789"); openClaw.setToken(System.getenv("OPENCLAW_GATEWAY_TOKEN"));
		openClaw.setRequestTimeout(Duration.ofMinutes(3)); openClaw.setAgentWaitTimeout(Duration.ofMinutes(3));
		OpenClawWebSocketClient client = new OpenClawWebSocketClient(openClaw, mapper);
		OpenClawTaskService tasksApi = new OpenClawTaskService(client, openClaw);
		InMemoryValidationArtifactRepository artifacts = new InMemoryValidationArtifactRepository();
		ValidationEvidenceService evidence = new ValidationEvidenceService(artifacts, new ArtifactContentLimiter(262144));
		InMemoryAuditRepository events = new InMemoryAuditRepository(); AuditService audit = new AuditService(events);
		OpenClawBrowserTestExecutor browserExecutor = new OpenClawBrowserTestExecutor(tasksApi,
			new BrowserTaskPromptBuilder(mapper), new BrowserResultMapper(mapper), mapper, "main");
		BrowserScenarioProperties properties = new BrowserScenarioProperties();
		properties.setScenarios(List.of(scenario("pass", url, "Saved successfully"), scenario("fail", url, "Never shown")));
		BrowserScenarioCatalog catalog = new BrowserScenarioCatalog(properties);
		BrowserValidationProvider provider = new BrowserValidationProvider(browserExecutor,
			new BrowserUrlPolicy(properties), evidence, audit, mapper, "openclaw");
		TaskRecord task = new TaskRecord("task-browser", "Browser acceptance", "real browser", "project-browser", "workspace-browser", ExecutionMode.READ_ONLY);
		TaskCenterService taskCenter = mock(TaskCenterService.class); when(taskCenter.getTask("task-browser")).thenReturn(Optional.of(task));
		Workspace bound = new Workspace("workspace-browser", "project-browser", path.toString(), "main", WorkspaceStatus.READY, Instant.now(), Instant.now());
		WorkspaceService workspaces = mock(WorkspaceService.class); when(workspaces.getWorkspace("workspace-browser")).thenReturn(Optional.of(bound));
		when(workspaces.checkProjectOwnership("project-browser", "workspace-browser")).thenReturn(true);
		ValidationService service = new ValidationService(new InMemoryValidationRepository(), taskCenter, workspaces,
			new ProjectCapabilityDetector(mapper), List.of(provider), evidence, audit, catalog);
		return new Fixture(service, events);
	}

	private BrowserScenario scenario(String id, String url, String expected) {
		return new BrowserScenario(id, id, url, true, List.of(
			new BrowserStep("navigate", "Open fixture", BrowserAction.NAVIGATE, null, null, 15000L, List.of(), false, true),
			new BrowserStep("input", "Enter name", BrowserAction.INPUT, "#name", "Ada", 10000L, List.of(), false, true),
			new BrowserStep("save", "Save", BrowserAction.CLICK, "#save", null, 10000L,
				List.of(new BrowserAssertion(BrowserAssertionType.TEXT, "#status", expected, 10000L)), false, true),
			new BrowserStep("screenshot", "Capture result", BrowserAction.SCREENSHOT, null, null, 10000L, List.of(), true, true)));
	}
	private ValidationCheck browser(ValidationRun run) { return run.getChecks().stream().filter(c -> c.getType() == ValidationCheckType.BROWSER).findFirst().orElseThrow(); }
	private void git(Path path, String... args) throws Exception { ProcessBuilder b = new ProcessBuilder(); b.command(java.util.stream.Stream.concat(java.util.stream.Stream.of("git"), java.util.Arrays.stream(args)).toList()); b.directory(path.toFile()); Process p = b.start(); assertEquals(0, p.waitFor(), new String(p.getErrorStream().readAllBytes(), StandardCharsets.UTF_8)); }
	private String output(Path path, String... args) throws Exception { ProcessBuilder b = new ProcessBuilder(); b.command(java.util.stream.Stream.concat(java.util.stream.Stream.of("git"), java.util.Arrays.stream(args)).toList()); b.directory(path.toFile()); Process p = b.start(); String value = new String(p.getInputStream().readAllBytes(), StandardCharsets.UTF_8); assertEquals(0, p.waitFor()); return value; }
	private GitState state(Path path) throws Exception { return new GitState(output(path,"rev-parse","HEAD"), output(path,"status","--porcelain"), output(path,"diff","--binary")); }
	private String page() { return "<!doctype html><html><body><label>Name <input id='name'></label><button id='save'>Save</button><div id='status'></div><script>document.querySelector('#save').onclick=()=>document.querySelector('#status').textContent='Saved successfully';</script></body></html>"; }
	private record Fixture(ValidationService service, InMemoryAuditRepository events) { }
	private record GitState(String head, String status, String diff) { }
}
