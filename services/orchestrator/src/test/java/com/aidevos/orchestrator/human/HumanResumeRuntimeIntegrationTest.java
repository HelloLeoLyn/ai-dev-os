package com.aidevos.orchestrator.human;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.collaboration.AgentMessageType;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.runtime.AgentSessionStatus;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runtime resume verification after a human approval: only the downstream
 * agents run after the gate, feedback submitted while paused is delivered,
 * and the session reaches COMPLETED through the same graph topology.
 */
class HumanResumeRuntimeIntegrationTest extends HumanTestBase {

	@Test
	void approvedGateResumesAndRunsOnlyDownstreamAgents() {
		TaskRecord taskRecord = task("task-1");
		RecordingExecutor hermes = success(AgentType.HERMES);
		RecordingExecutor codex = success(AgentType.CODEX);
		RecordingExecutor test = success(AgentType.TEST_AGENT);
		AgentRuntimeService runtime = runtime(hermes, codex, test);
		ExecutionGraph graph = graphBuilder.codeGraphWithHumanGate("task-1");
		graphExecutor.execute(graph, baseContext(graph, taskRecord));

		assertEquals(1, hermes.calls);
		assertEquals(0, codex.calls, "CODEX must not run before the approval");
		assertEquals(0, test.calls);

		HumanApproval approval = humanService.getTaskApprovals("task-1").getFirst();
		humanService.approve(approval.getApprovalId(), "alice", "go");

		assertEquals(1, hermes.calls, "completed nodes must not re-run");
		assertEquals(1, codex.calls, "CODEX must run after the approval");
		assertEquals(1, test.calls, "TEST_AGENT must run after the approval");
		AgentSession session = sessionRepository.listByTask("task-1").getFirst();
		assertEquals(AgentSessionStatus.COMPLETED, session.getStatus());
		assertEquals("TEST_AGENT_VERIFY", session.getCurrentNodeId());
	}

	@Test
	void feedbackDuringPauseThenApprovalCompletesGraph() {
		TaskRecord taskRecord = task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));
		ExecutionGraph graph = graphBuilder.codeGraphWithHumanGate("task-1");
		graphExecutor.execute(graph, baseContext(graph, taskRecord));
		AgentSession session = sessionRepository.listByTask("task-1").getFirst();
		assertEquals(AgentSessionStatus.PAUSED, session.getStatus());

		humanService.addFeedback("task-1", session.getSessionId(), "CODEX", "use tdd");
		assertEvent(EventType.HUMAN_FEEDBACK_ADDED);

		HumanApproval approval = humanService.getTaskApprovals("task-1").getFirst();
		humanService.approve(approval.getApprovalId(), "alice", "ok");

		assertEquals(AgentSessionStatus.COMPLETED,
			sessionRepository.get(session.getSessionId()).getStatus());
		assertTrue(collaborationService.messages(collaborationService.teamForTask("task-1")
			.orElseThrow().getTeamId()).stream().anyMatch(message ->
			message.getMessageType() == AgentMessageType.HUMAN_RESPONSE
				&& "HUMAN".equals(message.getFromAgent())
				&& "CODEX".equals(message.getToAgent())
				&& "use tdd".equals(message.getContent())));
	}
}
