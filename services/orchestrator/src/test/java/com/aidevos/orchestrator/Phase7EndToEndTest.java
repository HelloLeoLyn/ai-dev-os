package com.aidevos.orchestrator;

import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.agent.AgentResolver;
import com.aidevos.orchestrator.agent.AgentSelector;
import com.aidevos.orchestrator.approval.CodingApprovalProperties;
import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.audit.timeline.TimelineService;
import com.aidevos.orchestrator.execution.*;
import com.aidevos.orchestrator.executor.*;
import com.aidevos.orchestrator.job.*;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.AgentDefinition;
import com.aidevos.orchestrator.plan.*;
import com.aidevos.orchestrator.plan.approval.*;
import com.aidevos.orchestrator.plan.run.*;
import com.aidevos.orchestrator.plan.schedule.*;
import com.aidevos.orchestrator.planner.*;
import com.aidevos.orchestrator.planner.replan.*;
import com.aidevos.orchestrator.tool.*;
import com.aidevos.orchestrator.tool.approval.*;
import com.aidevos.orchestrator.tool.mcp.*;
import com.aidevos.orchestrator.tool.policy.ToolPolicyDecision;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.*;

class Phase7EndToEndTest {

	@Test
	void executesHermesApprovalSchedulerAgentMcpAuditTimelineChain() throws Exception {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		AuditService audit = new AuditService(events);
		PlanValidator validator = new PlanValidator();
		PlannerService planners = new PlannerService(List.of(new HermesPlanner()), validator,
			new ReplanValidator(validator), audit);
		PlanningResult planning = planners.createPlan(request());
		assertTrue(planning.success(), () -> planning.errors().toString());

		PlanApprovalService approvals = new PlanApprovalService(new PlanApprovalStore(), validator,
			new ObjectMapper(), audit);
		PlanApprovalRequest approval = approvals.create("request-e2e", planning.plan());
		approvals.approve(approval.getId(), "phase7-reviewer");

		McpToolProvider mcp = mcpProvider(audit);
		ToolRouter router = new ToolRouter(new ToolRegistry(List.of(mcp)),
			(definition, invocation) -> ToolPolicyDecision.allow(),
			new ToolApprovalService(new ToolApprovalStore(), new ObjectMapper()), audit);
		ToolExecutor toolExecutor = new ToolExecutor(router,
			new DefaultToolArtifactMapper(new ArtifactContentLimiter(100_000)));
		AgentManager agents = agents();
		ExecutorRegistry executors = new ExecutorRegistry(List.of(
			new ArtifactExecutor("openclaw", "screenshot", "page.png", "image/png"),
			toolExecutor,
			new ArtifactExecutor("codex", "git-diff", "changes.patch", "text/plain")));
		ExecutionRecordManager records = new ExecutionRecordManager(
			new InMemoryExecutionRecordRepository(), audit);
		ExecutionEngine engine = new ExecutionEngine(new AgentResolver(agents,
			new AgentSelector(agents), new ExecutorManager(agents, executors), audit), records, audit);
		JobStore jobs = new JobStore();
		JobWorker worker = new JobWorker(engine, records, jobs, audit, 20);
		worker.start();
		PlanScheduler scheduler = null;
		try {
			JobService jobService = new JobService(jobs, worker, audit);
			ReplanRequestService replans = new ReplanRequestService(new ReplanRequestStore(),
				new com.aidevos.orchestrator.planner.replan.FailureClassifier(),
				java.time.Clock.systemUTC(), audit);
			scheduler = new PlanScheduler(jobService, new StepTaskFactory(), approvals, replans,
				new InMemoryPlanRunRepository(), audit);
			PlanRun run = scheduler.start(approval.getId());
			long deadline = System.nanoTime() + Duration.ofSeconds(5).toNanos();
			while (!terminal(run.getStatus()) && System.nanoTime() < deadline) {
				scheduler.reconcile();
				Thread.sleep(10);
			}

			assertEquals(PlanRunStatus.SUCCESS, run.getStatus(), run::getError);
			assertEquals(4, records.getAll().size());
			List<EventType> types = events.query(EventQuery.all()).stream().map(EventRecord::type).toList();
			assertTrue(types.containsAll(List.of(EventType.PLAN_CREATED,
				EventType.PLAN_APPROVAL_APPROVED, EventType.PLAN_RUN_STARTED,
				EventType.STEP_ATTEMPT_STARTED, EventType.JOB_STARTED,
				EventType.AGENT_EXECUTION_STARTED, EventType.TOOL_STARTED,
				EventType.MCP_CALL_COMPLETED, EventType.EXECUTION_RECORD_SAVED,
				EventType.PLAN_RUN_SUCCEEDED)));
			assertEquals(events.count(new EventQuery(null, null, run.getId(), null, null, null,
				null, null, null, null, Set.of(), null, null, 0, 100)),
				new TimelineService(events).planRun(run.getId(), Set.of(), 0, 100).totalCount());
		}
		finally {
			worker.stop();
			router.close();
			mcp.close();
		}
	}

	private PlanningRequest request() {
		return new PlanningRequest("e2e", "Inspect, read, fix and verify", "hermes", null,
			"prompt-v2", Map.of("multiAgent", true, "browserUrl", "https://example.com",
				"toolName", "echo", "toolArguments", Map.of("value", "READY")), snapshot(), Map.of());
	}

	private PlanSnapshot snapshot() {
		return new PlanSnapshot(List.of(
			new PlanSnapshot.AgentSnapshot("browser-agent", "openclaw", List.of("browser"), "read-only", true),
			new PlanSnapshot.AgentSnapshot("mcp-reader", "tool", List.of("tool", "read-only"), "read-only", true),
			new PlanSnapshot.AgentSnapshot("coder", "codex", List.of("coding", "git"), "workspace-write", true),
			new PlanSnapshot.AgentSnapshot("tester", "openclaw", List.of("testing", "browser"), "read-only", true)),
			Set.of("browser", "tool", "read-only", "coding", "git", "testing"),
			List.of(new PlanSnapshot.ToolSnapshot("filesystem", "echo", ToolAccess.READ_ONLY)),
			Set.of("openclaw", "tool", "codex"), "policy-v1", Map.of());
	}

	private AgentManager agents() {
		AgentManager manager = new AgentManager();
		register(manager, "browser-agent", "openclaw", List.of("browser"));
		register(manager, "mcp-reader", "tool", List.of("tool", "read-only"));
		register(manager, "coder", "codex", List.of("coding", "git"));
		register(manager, "tester", "openclaw", List.of("testing", "browser"));
		return manager;
	}

	private void register(AgentManager manager, String name, String executor, List<String> capabilities) {
		AgentDefinition agent = new AgentDefinition();
		agent.setName(name); agent.setExecutor(executor); agent.setCapabilities(capabilities);
		agent.setEnabled(true); manager.register(agent);
	}

	private McpToolProvider mcpProvider(AuditService audit) {
		ObjectMapper mapper = new ObjectMapper();
		String script = Path.of("src/test/resources/mcp/fake-mcp-server.js")
			.toAbsolutePath().normalize().toString();
		return new McpToolProvider("filesystem", new McpClient(
			new McpStdioSession(List.of("node", script), null, mapper), mapper, Duration.ofSeconds(2)),
			mapper, audit);
	}

	private boolean terminal(PlanRunStatus status) {
		return status == PlanRunStatus.SUCCESS || status == PlanRunStatus.FAILED
			|| status == PlanRunStatus.REPLAN_REQUIRED;
	}

	private static final class ArtifactExecutor implements AgentExecutor {
		private final String type;
		private final String artifactType;
		private final String name;
		private final String mediaType;
		private ArtifactExecutor(String type, String artifactType, String name, String mediaType) {
			this.type = type; this.artifactType = artifactType; this.name = name; this.mediaType = mediaType;
		}
		public String getType() { return type; }
		public ExecutionResult execute(ExecutionContext context) {
			ExecutionArtifact artifact = new ExecutionArtifact();
			artifact.setType(artifactType); artifact.setName(name); artifact.setMediaType(mediaType);
			artifact.setContent("evidence");
			ExecutionResult result = new ExecutionResult(); result.setSuccess(true);
			result.setMessage("completed"); result.setArtifacts(List.of(artifact)); return result;
		}
	}
}
