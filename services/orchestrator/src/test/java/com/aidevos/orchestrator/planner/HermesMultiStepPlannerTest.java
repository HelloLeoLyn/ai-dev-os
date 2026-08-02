package com.aidevos.orchestrator.planner;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.plan.Dependency;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanValidationResult;
import com.aidevos.orchestrator.plan.PlanValidator;
import com.aidevos.orchestrator.tool.ToolAccess;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class HermesMultiStepPlannerTest {

	private final HermesPlanner planner = new HermesPlanner();
	private final PlanValidator validator = new PlanValidator();

	@Test
	void shouldGenerateValidatedFourStepDevelopmentPlan() {
		PlanDraft draft = planner.plan(request(snapshot()));
		Plan plan = draft.toPlan();

		assertEquals(List.of("browser-inspect", "mcp-read", "code-fix", "browser-verify"),
			plan.steps().stream().map(step -> step.id()).toList());
		assertEquals(List.of("browser-agent", "mcp-reader", "coder", "tester"),
			plan.steps().stream().map(step -> step.assignment().agentName()).toList());
		assertEquals(List.of(
			new Dependency("browser-inspect", "mcp-read", true),
			new Dependency("browser-inspect", "code-fix", true),
			new Dependency("mcp-read", "code-fix", true),
			new Dependency("code-fix", "browser-verify", true)), plan.dependencies());
		assertEquals(List.of("screenshot", "mcp-text", "git-diff", "screenshot"),
			plan.steps().stream().map(step -> step.expectedArtifacts().getFirst().type()).toList());
		assertEquals(List.of("browserEvidence", "sourceContext"),
			plan.steps().get(2).inputArtifacts().stream().map(reference -> reference.inputKey())
				.toList());
		assertTrue(validator.validate(plan).valid());
	}

	@Test
	void validatorShouldRejectUnknownAgentAndTool() {
		PlanSnapshot invalidSnapshot = new PlanSnapshot(snapshot().agents().stream()
			.filter(agent -> !"tester".equals(agent.name())).toList(), snapshot().capabilities(),
			List.of(), snapshot().executors(), "policy-v1", Map.of());

		PlanValidationResult validation = validator.validate(planner.plan(request(invalidSnapshot))
			.toPlan());

		assertFalse(validation.valid());
		assertTrue(validation.errors().contains("UNKNOWN_AGENT:tester"));
		assertTrue(validation.errors().contains("UNKNOWN_TOOL:filesystem/read_text_file"));
	}

	@Test
	void shouldRetainSingleStepCompatibilityUnlessMultiAgentRequested() {
		PlanningRequest request = new PlanningRequest("single", "Analyze only", HermesPlanner.NAME,
			null, "prompt-v1", Map.of(), snapshot(), Map.of());

		PlanDraft draft = planner.plan(request);

		assertEquals(1, draft.steps().size());
		assertEquals("step-1", draft.steps().getFirst().id());
	}

	private PlanningRequest request(PlanSnapshot snapshot) {
		return new PlanningRequest("login-fix", "Check and fix the Web login page",
			HermesPlanner.NAME, null, "prompt-v2",
			Map.of("multiAgent", true, "browserUrl", "https://example.com",
				"sourcePath", "src/login.js", "workspace", "/tmp/login-fixture",
				"testCommand", "npm test"),
			snapshot, Map.of("scenario", "phase-6b"));
	}

	private PlanSnapshot snapshot() {
		return new PlanSnapshot(List.of(
			new PlanSnapshot.AgentSnapshot("browser-agent", "openclaw", List.of("browser"),
				"read-only", true),
			new PlanSnapshot.AgentSnapshot("mcp-reader", "tool", List.of("tool", "read-only"),
				"read-only", true),
			new PlanSnapshot.AgentSnapshot("coder", "codex", List.of("coding", "git"),
				"workspace-write", true),
			new PlanSnapshot.AgentSnapshot("tester", "openclaw", List.of("testing", "browser"),
				"read-only", true)),
			Set.of("browser", "tool", "read-only", "coding", "git", "testing"),
			List.of(new PlanSnapshot.ToolSnapshot("filesystem", "read_text_file",
				ToolAccess.READ_ONLY)), Set.of("openclaw", "tool", "codex"), "policy-v1",
			Map.of("planner", "hermes"));
	}
}
