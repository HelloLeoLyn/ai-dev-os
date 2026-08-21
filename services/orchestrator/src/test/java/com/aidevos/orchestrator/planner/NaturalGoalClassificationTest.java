package com.aidevos.orchestrator.planner;

import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.execution.ValidationProfile;
import com.aidevos.orchestrator.plan.Plan;
import com.aidevos.orchestrator.plan.PlanSnapshot;
import com.aidevos.orchestrator.plan.PlanValidator;
import com.aidevos.orchestrator.plan.StepExecutionType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NaturalGoalClassificationTest {

	private final HermesPlanner planner = new HermesPlanner();
	private final PlanValidator validator = new PlanValidator();

	@Test
	void modifyThenCompileAndTestGeneratesAiPlusMavenToolSteps() {
		Plan plan = planFor("修改 UserService bug，然后编译并运行对应测试");

		assertEquals(List.of(StepExecutionType.AI_STEP, StepExecutionType.TOOL_STEP,
			StepExecutionType.TOOL_STEP, StepExecutionType.SYSTEM_STEP),
			plan.steps().stream().map(step -> step.executionType()).toList());
		assertEquals("maven", plan.steps().get(1).toolName());
		assertEquals("compile", plan.steps().get(1).toolArguments().get("command"));
		assertEquals("maven", plan.steps().get(2).toolName());
		assertEquals("test", plan.steps().get(2).toolArguments().get("command"));
		assertEquals("FAST", plan.snapshot().plannerMetadata().get("validationProfile"));
		assertTrue(validator.validate(plan).valid());
	}

	@Test
	void explicitTestClassGoalGeneratesTargetedMavenTest() {
		// V1 Final Gate: goal 明确指定测试类时，MAVEN test step 必须生成定向参数
		Plan plan = planFor("新增 V1FinalGateSmokeTest.java 冒烟测试文件并运行对应测试");

		assertEquals(StepExecutionType.TOOL_STEP, plan.steps().getFirst().executionType());
		assertEquals("maven", plan.steps().getFirst().toolName());
		assertEquals("test", plan.steps().getFirst().toolArguments().get("command"));
		assertEquals("V1FinalGateSmokeTest",
			plan.steps().getFirst().toolArguments().get("testClass"));
		assertTrue(validator.validate(plan).valid());
	}

	@Test
	void gitStatusGoalGeneratesGitToolStepOnly() {
		Plan plan = planFor("运行 git status");

		assertEquals(List.of(StepExecutionType.TOOL_STEP, StepExecutionType.SYSTEM_STEP),
			plan.steps().stream().map(step -> step.executionType()).toList());
		assertEquals("git", plan.steps().getFirst().toolName());
		assertEquals("status", plan.steps().getFirst().toolArguments().get("command"));
	}

	@Test
	void frontendBuildGoalGeneratesNpmToolStep() {
		Plan plan = planFor("运行前端 build");

		assertEquals(List.of(StepExecutionType.TOOL_STEP, StepExecutionType.SYSTEM_STEP),
			plan.steps().stream().map(step -> step.executionType()).toList());
		assertEquals("npm", plan.steps().getFirst().toolName());
		assertEquals("build", plan.steps().getFirst().toolArguments().get("command"));
	}

	@Test
	void modifyThenWaitForHumanConfirmationGeneratesAiPlusHumanGate() {
		Plan plan = planFor("修改代码后等待人工确认");

		assertEquals(List.of(StepExecutionType.AI_STEP, StepExecutionType.HUMAN_GATE,
			StepExecutionType.SYSTEM_STEP),
			plan.steps().stream().map(step -> step.executionType()).toList());
		assertEquals(StepExecutionType.HUMAN_GATE, plan.steps().get(1).executionType());
	}

	@Test
	void unrecognizedGoalFallsBackToSingleAiStep() {
		Plan plan = planFor("分析用户需求并给出建议");

		assertEquals(List.of(StepExecutionType.AI_STEP),
			plan.steps().stream().map(step -> step.executionType()).toList());
	}

	@Test
	void explicitFullRegressionGoalEscalatesToFullOnlyWhenStated() {
		assertEquals(ValidationProfile.FULL,
			StepClassifier.validationProfile("修改代码，然后做完整回归", Map.of()));
		assertEquals(ValidationProfile.TARGETED,
			StepClassifier.validationProfile("跨模块改动并运行测试", Map.of()));
		assertEquals(ValidationProfile.FAST,
			StepClassifier.validationProfile("修改 UserService bug，然后编译并运行对应测试",
				Map.of()));
	}

	private Plan planFor(String goal) {
		return planner.plan(request(goal)).toPlan();
	}

	private PlanningRequest request(String goal) {
		return new PlanningRequest("natural-" + goal.hashCode(), goal, HermesPlanner.NAME,
			null, "prompt-v1", Map.of(), snapshot(), Map.of());
	}

	private PlanSnapshot snapshot() {
		return new PlanSnapshot(List.of(new PlanSnapshot.AgentSnapshot("coder", "codex",
			List.of("coding", "git"), "workspace-write", true)),
			Set.of("coding", "git"), List.of(), Set.of("codex"), "policy-v1", Map.of());
	}
}
