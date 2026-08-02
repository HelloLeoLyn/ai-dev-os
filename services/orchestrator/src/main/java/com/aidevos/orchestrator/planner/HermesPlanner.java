package com.aidevos.orchestrator.planner;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.aidevos.orchestrator.plan.AgentAssignment;
import com.aidevos.orchestrator.plan.ArtifactReference;
import com.aidevos.orchestrator.plan.Dependency;
import com.aidevos.orchestrator.plan.ExpectedArtifact;
import com.aidevos.orchestrator.plan.FailurePolicy;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanStep;
import com.aidevos.orchestrator.plan.RetryPolicy;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.planner.replan.ReplanRequest;
import org.springframework.stereotype.Component;

@Component
public class HermesPlanner implements Planner {

	public static final String NAME = "hermes";

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public PlanDraft plan(PlanningRequest request) {
		if (Boolean.TRUE.equals(request.structuredInput().get("multiAgent"))) {
			return multiStepPlan(request);
		}
		PlanSnapshot.AgentSnapshot agent = request.snapshot() == null ? null
			: request.snapshot().agents().stream().filter(PlanSnapshot.AgentSnapshot::enabled)
				.findFirst().orElse(null);
		AgentAssignment assignment = agent == null
			? new AgentAssignment(null, List.of(), List.of())
			: new AgentAssignment(agent.name(), agent.capabilities(), List.of());
		PlanStep step = new PlanStep("step-1", "Handle request", request.goal(),
			StepStatus.PLANNED, assignment, null, null, Map.of(),
			List.of(new ExpectedArtifact("result", "result", "application/json", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);
		return new PlanDraft("plan-" + request.requestId(), 1, request.goal(), List.of(step),
			List.of(), request.snapshot(), name(), request.model(), request.promptVersion(),
			request.metadata());
	}

	private PlanDraft multiStepPlan(PlanningRequest request) {
		Map<String, Object> input = request.structuredInput();
		String browserUrl = text(input, "browserUrl", "https://example.com");
		String sourcePath = text(input, "sourcePath", "README.md");
		String toolProvider = text(input, "toolProvider", "filesystem");
		String toolName = text(input, "toolName", "read_text_file");

		PlanStep browser = new PlanStep("browser-inspect", "Inspect login page",
			"Open the target page and capture its observable state.", StepStatus.PLANNED,
			new AgentAssignment("browser-agent", List.of("browser"), List.of()),
			Map.of("browser", Map.of("action", "navigate", "url", browserUrl,
				"screenshot", true)), List.of(), null, null, Map.of(),
			List.of(new ExpectedArtifact("screenshot", null, "image/png", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);

		PlanStep inspectSource = new PlanStep("mcp-read", "Read relevant source",
			"Read only the explicitly selected project file through MCP.", StepStatus.PLANNED,
			new AgentAssignment("mcp-reader", List.of("tool", "read-only"), List.of()),
			Map.of(), List.of(new ArtifactReference("browser-inspect", "screenshot", null,
				"browserEvidence", true)), toolProvider, toolName, Map.of("path", sourcePath),
			List.of(new ExpectedArtifact("mcp-text", null, "text/plain", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);

		Map<String, Object> coding = new LinkedHashMap<>();
		coding.put("sandbox", "workspace-write");
		Object workspace = input.get("workspace");
		if (workspace instanceof String path && !path.isBlank()) {
			coding.put("workspace", path);
		}
		Object testCommand = input.get("testCommand");
		if (testCommand instanceof String command && !command.isBlank()) {
			coding.put("testCommand", command);
		}
		PlanStep coder = new PlanStep("code-fix", "Implement the fix", request.goal(),
			StepStatus.PLANNED,
			new AgentAssignment("coder", List.of("coding", "git"), List.of()),
			Map.of("coding", coding),
			List.of(
				new ArtifactReference("browser-inspect", "screenshot", null,
					"browserEvidence", true),
				new ArtifactReference("mcp-read", "mcp-text", null,
					"sourceContext", true)),
			null, null, Map.of(),
			List.of(new ExpectedArtifact("git-diff", "changes.patch", "text/plain", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.REQUEST_REPLAN, false);

		PlanStep tester = new PlanStep("browser-verify", "Verify the fix",
			"Run browser regression verification against the target page.", StepStatus.PLANNED,
			new AgentAssignment("tester", List.of("testing", "browser"), List.of()),
			Map.of("browser", Map.of("action", "navigate", "url", browserUrl,
				"screenshot", true)),
			List.of(new ArtifactReference("code-fix", "git-diff", "changes.patch",
				"codeChanges", true)), null, null, Map.of(),
			List.of(new ExpectedArtifact("screenshot", null, "image/png", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.REQUEST_REPLAN, false);

		List<Dependency> dependencies = List.of(
			new Dependency("browser-inspect", "mcp-read", true),
			new Dependency("browser-inspect", "code-fix", true),
			new Dependency("mcp-read", "code-fix", true),
			new Dependency("code-fix", "browser-verify", true));
		return new PlanDraft("plan-" + request.requestId(), 1, request.goal(),
			List.of(browser, inspectSource, coder, tester), dependencies, request.snapshot(),
			name(), request.model(), request.promptVersion(), request.metadata());
	}

	private String text(Map<String, Object> input, String key, String fallback) {
		Object value = input.get(key);
		return value instanceof String text && !text.isBlank() ? text : fallback;
	}

	@Override
	public PlanDraft replan(ReplanRequest request) {
		Map<String, Object> metadata = new java.util.LinkedHashMap<>(
			request.originalPlan().snapshot().plannerMetadata());
		metadata.put("replanRequestId", request.id());
		metadata.put("failureClassification", request.failureClassification().name());
		return new PlanDraft(request.originalPlanId(), request.originalPlanVersion() + 1,
			request.originalPlan().goal(), request.originalPlan().steps(),
			request.originalPlan().dependencies(), request.originalPlan().snapshot(), name(),
			metadataValue(metadata, "model"), metadataValue(metadata, "promptVersion"), metadata);
	}

	private String metadataValue(Map<String, Object> metadata, String key) {
		Object value = metadata.get(key);
		return value instanceof String text ? text : null;
	}
}
