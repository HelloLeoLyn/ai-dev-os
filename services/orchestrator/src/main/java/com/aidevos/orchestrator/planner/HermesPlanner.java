package com.aidevos.orchestrator.planner;

import java.util.LinkedHashMap;
import java.util.Optional;
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
import com.aidevos.orchestrator.plan.StepExecutionType;
import com.aidevos.orchestrator.plan.StepStatus;
import com.aidevos.orchestrator.planner.replan.ReplanRequest;
import org.springframework.stereotype.Component;

@Component
public class HermesPlanner implements Planner {

	public static final String NAME = "hermes";
	private static final ExpectedArtifact CODER_RESULT = new ExpectedArtifact("git-diff",
		"changes.patch", "text/plain", true, 1);

	@Override
	public String name() {
		return NAME;
	}

	@Override
	public PlanDraft plan(PlanningRequest request) {
		if ("project-analysis".equals(request.structuredInput().get("taskType"))) {
			return projectAnalysisPlan(request);
		}
		if (Boolean.TRUE.equals(request.structuredInput().get("toolPlan"))) {
			return toolchainPlan(request);
		}
		if (Boolean.TRUE.equals(request.structuredInput().get("multiAgent"))) {
			return multiStepPlan(request);
		}
		List<NaturalGoalClassifier.StepIntent> intents = NaturalGoalClassifier.classify(
			request.goal());
		if (!intents.isEmpty()) {
			return naturalPlan(request, intents);
		}
		boolean readWrite = "READ_WRITE".equals(request.metadata().get("executionMode"));
		PlanSnapshot.AgentSnapshot agent = request.snapshot() == null ? null
			: request.snapshot().agents().stream()
				.filter(PlanSnapshot.AgentSnapshot::enabled)
				.filter(candidate -> !readWrite
					|| (candidate.capabilities().containsAll(List.of("coding", "git"))
						&& !"mock".equals(candidate.executor())))
				.findFirst().orElse(null);
		AgentAssignment assignment = agent == null
			? new AgentAssignment(null, readWrite ? List.of("coding", "git") : List.of(), List.of())
			: new AgentAssignment(agent.name(), readWrite ? List.of("coding", "git")
				: agent.capabilities(), List.of());
		PlanStep step = new PlanStep("step-1", "Handle request", request.goal(),
			StepStatus.PLANNED, assignment, null, null, Map.of(),
			readWrite ? coderExpectedArtifacts()
				: List.of(new ExpectedArtifact("result", "result", "application/json", true, 1)),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false, readWrite);
		return new PlanDraft("plan-" + request.requestId(), 1, request.goal(), List.of(step),
			List.of(), request.snapshot(), name(), request.model(), request.promptVersion(),
			request.metadata());
	}

	/**
	 * Builds a classified plan from the natural language step intents: an AI
	 * code step (when the goal implies code changes), deterministic tool steps
	 * and human gates in occurrence order. Tool steps never submit an AI job.
	 */
	private PlanDraft naturalPlan(PlanningRequest request,
			List<NaturalGoalClassifier.StepIntent> intents) {
		Map<String, Object> input = request.structuredInput();
		String workspace = text(input, "workspace", null);
		AgentAssignment coder = new AgentAssignment("coder", List.of("coding", "git"), List.of());
		Map<String, Object> toolParameters = new LinkedHashMap<>();
		if (workspace != null && !workspace.isBlank()) {
			toolParameters.put("workingDirectory", workspace);
		}
		toolParameters.put("timeoutSeconds", 300);

		List<PlanStep> steps = new java.util.ArrayList<>();
		List<Dependency> dependencies = new java.util.ArrayList<>();
		String previousId = null;
		int index = 1;
		for (NaturalGoalClassifier.StepIntent intent : intents) {
			String id = "step-" + index;
			// SELF-HOSTING-GATE-BLOCKER-02-FIX：Execution/Validation 职责分离——
			// 普通 coding goal 提到测试但无显式测试类时，不生成必然被 Scheduler
			// fail-closed 拒绝的 targeted maven test step（测试范围交给 Validation Center）；
			// 显式测试类（如 "运行 V1FinalGateSmokeTest.java"）仍生成 targeted step。
			if (intent.kind() == NaturalGoalClassifier.Kind.TOOL
					&& "maven".equals(intent.toolName()) && "test".equals(intent.command())
					&& NaturalGoalClassifier.extractTestTarget(request.goal()).isEmpty()) {
				continue;
			}
			PlanStep step = switch (intent.kind()) {
				case AI -> new PlanStep(id, "Modify code", request.goal(),
					StepStatus.PLANNED, coder, Map.of("sandbox", "workspace-write"),
					List.of(), null, null, Map.of(), coderExpectedArtifacts(),
					RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false, null, true,
					StepExecutionType.AI_STEP);
				case TOOL -> {
					Map<String, Object> toolArgs = new LinkedHashMap<>();
					toolArgs.put("command", intent.command());
					// V1 Final Gate: MAVEN test step 必须带显式测试类目标（targeted test），
					// 无法可靠提取时由执行层 fail closed，绝不默认全量 mvn test
					if ("maven".equals(intent.toolName()) && "test".equals(intent.command())) {
						NaturalGoalClassifier.extractTestTarget(request.goal())
							.ifPresent(target -> toolArgs.put("testClass", target));
					}
					yield new PlanStep(id, stepTitle(intent), stepTitle(intent),
						StepStatus.PLANNED, coder, Map.copyOf(toolParameters), List.of(),
						"deterministic", intent.toolName(), toolArgs, List.of(),
						RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false, null, false,
						StepExecutionType.TOOL_STEP);
				}
				case HUMAN_GATE -> new PlanStep(id, "Human approval",
					"Waiting for human approval", StepStatus.PLANNED, coder, Map.of(),
					List.of(), null, null, Map.of(), List.of(), RetryPolicy.noRetry(),
					FailurePolicy.STOP_PLAN, false, null, false,
					StepExecutionType.HUMAN_GATE);
			};
			if (previousId != null) {
				dependencies.add(new Dependency(previousId, id, false));
			}
			previousId = id;
			steps.add(step);
			index++;
		}
		// Real system bookkeeping: every natural plan ends with a SYSTEM_STEP
		// that records the run deterministically (never an AI job).
		String bookkeepingId = "step-bookkeeping";
		PlanStep bookkeeping = new PlanStep(bookkeepingId, "Record run bookkeeping",
			"Persist a deterministic bookkeeping record for this run",
			StepStatus.PLANNED, coder, Map.of("action", "run-bookkeeping"), List.of(),
			null, null, Map.of(), List.of(), RetryPolicy.noRetry(),
			FailurePolicy.STOP_PLAN, false, null, false, StepExecutionType.SYSTEM_STEP);
		if (previousId != null) {
			dependencies.add(new Dependency(previousId, bookkeepingId, false));
		}
		steps.add(bookkeeping);
		Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
		metadata.put("validationProfile",
			StepClassifier.validationProfile(request.goal(), input).name());
		if (workspace != null && !workspace.isBlank()) {
			metadata.put("workspacePath", workspace);
		}
		PlanSnapshot snapshot = request.snapshot() == null ? null
			: new PlanSnapshot(request.snapshot().agents(), request.snapshot().capabilities(),
				request.snapshot().tools(), request.snapshot().executors(),
				request.snapshot().policyVersion(), Map.copyOf(metadata));
		return new PlanDraft("plan-" + request.requestId(), 1, request.goal(), steps,
			dependencies, snapshot, name(), request.model(), request.promptVersion(),
			Map.copyOf(metadata));
	}

	private String stepTitle(NaturalGoalClassifier.StepIntent intent) {
		if ("maven".equals(intent.toolName())) {
			return "compile".equals(intent.command()) ? "Compile" : "Run targeted tests";
		}
		if ("npm".equals(intent.toolName())) {
			return "build".equals(intent.command()) ? "Frontend build" : "Frontend tests";
		}
		if ("git".equals(intent.toolName())) {
			return "git " + intent.command();
		}
		return "Health check";
	}

	/**
	 * A code-change toolchain plan: one AI step to change the code, then
	 * deterministic compile and targeted-test steps. The tool steps carry
	 * structured metadata the deterministic executor consumes directly and
	 * never submit an AI job.
	 */
	private PlanDraft toolchainPlan(PlanningRequest request) {
		Map<String, Object> input = request.structuredInput();
		String workspace = text(input, "workspace", null);
		AgentAssignment coder = new AgentAssignment("coder", List.of("coding", "git"), List.of());

		Map<String, Object> compileParameters = new LinkedHashMap<>();
		Map<String, Object> testParameters = new LinkedHashMap<>();
		if (workspace != null && !workspace.isBlank()) {
			compileParameters.put("workingDirectory", workspace);
			testParameters.put("workingDirectory", workspace);
		}
		compileParameters.put("timeoutSeconds", 300);
		testParameters.put("timeoutSeconds", 300);

		PlanStep fixCode = new PlanStep("fix-code", "Modify code", request.goal(),
			StepStatus.PLANNED, coder, Map.of("sandbox", "workspace-write"), List.of(),
			null, null, Map.of(), coderExpectedArtifacts(), RetryPolicy.noRetry(),
			FailurePolicy.STOP_PLAN, false, null, true, StepExecutionType.AI_STEP);
		PlanStep compile = new PlanStep("compile", "Compile", "Compile the modified code",
			StepStatus.PLANNED, coder, Map.copyOf(compileParameters), List.of(),
			"deterministic", "maven", Map.of("command", "compile"), List.of(),
			RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false, null, false,
			StepExecutionType.TOOL_STEP);
		Map<String, Object> testArgs = new LinkedHashMap<>();
		testArgs.put("command", "test");
		testArgs.put("profile", "FAST");
		// SELF-HOSTING-GATE-BLOCKER-02-FIX：toolchain plan 只在有显式测试类时生成
		// targeted maven test step；无显式测试类的普通 coding 任务不生成无效 step，
		// 测试范围交给 Validation Center（Execution/Validation 职责分离）。
		Optional<String> testTarget = NaturalGoalClassifier.extractTestTarget(request.goal());
		testTarget.ifPresent(target -> testArgs.put("testClass", target));
		PlanStep test = new PlanStep("test", "Run targeted tests",
			"Run the targeted tests for the change", StepStatus.PLANNED, coder,
			Map.copyOf(testParameters), List.of(), "deterministic", "maven",
			testArgs, List.of(), RetryPolicy.noRetry(),
			FailurePolicy.STOP_PLAN, false, null, false, StepExecutionType.TOOL_STEP);

		List<PlanStep> steps = new java.util.ArrayList<>(List.of(fixCode, compile));
		List<Dependency> dependencies = new java.util.ArrayList<>(
			List.of(new Dependency("fix-code", "compile", false)));
		if (testTarget.isPresent()) {
			steps.add(test);
			dependencies.add(new Dependency("compile", "test", false));
		}
		Map<String, Object> metadata = new LinkedHashMap<>(request.metadata());
		metadata.put("validationProfile", StepClassifier.validationProfile(input).name());
		if (workspace != null && !workspace.isBlank()) {
			metadata.put("workspacePath", workspace);
		}
		PlanSnapshot snapshot = request.snapshot() == null ? null
			: new PlanSnapshot(request.snapshot().agents(), request.snapshot().capabilities(),
				request.snapshot().tools(), request.snapshot().executors(),
				request.snapshot().policyVersion(), Map.copyOf(metadata));
		return new PlanDraft("plan-" + request.requestId(), 1, request.goal(),
			List.copyOf(steps), List.copyOf(dependencies), snapshot, name(),
			request.model(), request.promptVersion(), Map.copyOf(metadata));
	}

	private PlanDraft projectAnalysisPlan(PlanningRequest request) {
		PlanSnapshot.AgentSnapshot analyst = request.snapshot() == null ? null
			: request.snapshot().agents().stream()
				.filter(PlanSnapshot.AgentSnapshot::enabled)
				.filter(agent -> agent.capabilities().contains("analysis"))
				.filter(agent -> !"mock".equals(agent.executor()))
				.filter(agent -> "read-only".equals(agent.permissionLevel()))
				.findFirst().orElse(null);
		AgentAssignment assignment = analyst == null
			? new AgentAssignment(null, List.of("analysis", "read-only"), List.of())
			: new AgentAssignment(analyst.name(), List.of("analysis", "read-only"), List.of());
		PlanStep step = new PlanStep("analyze-project", "Analyze project", request.goal(),
			StepStatus.PLANNED, assignment,
			Map.of("sandbox", "read-only"), List.of(), null, null, Map.of(),
			List.of(new ExpectedArtifact("codex-result", "codex-result.txt", "text/plain",
				true, 1)), RetryPolicy.noRetry(), FailurePolicy.STOP_PLAN, false);
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
		Map<String, Object> toolArguments = objectMap(input.get("toolArguments"));
		if (toolArguments.isEmpty()) toolArguments = Map.of("path", sourcePath);

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
				"browserEvidence", true)), toolProvider, toolName, toolArguments,
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
			coderExpectedArtifacts(),
			RetryPolicy.noRetry(), FailurePolicy.REQUEST_REPLAN, false, true);

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

	private List<ExpectedArtifact> coderExpectedArtifacts() {
		return List.of(CODER_RESULT);
	}

	private String text(Map<String, Object> input, String key, String fallback) {
		Object value = input.get(key);
		return value instanceof String text && !text.isBlank() ? text : fallback;
	}

	private Map<String, Object> objectMap(Object value) {
		if (!(value instanceof Map<?, ?> source)) return Map.of();
		Map<String, Object> copy = new LinkedHashMap<>();
		for (Map.Entry<?, ?> entry : source.entrySet()) {
			if (entry.getKey() instanceof String key) copy.put(key, entry.getValue());
		}
		return Map.copyOf(copy);
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
