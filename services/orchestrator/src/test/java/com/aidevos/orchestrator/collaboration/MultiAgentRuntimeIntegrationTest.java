package com.aidevos.orchestrator.collaboration;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.execution.ExecutionRecordManager;
import com.aidevos.orchestrator.memory.MemoryType;
import com.aidevos.orchestrator.metrics.agent.AgentMetricsService;
import com.aidevos.orchestrator.metrics.tool.ToolMetricsService;
import com.aidevos.orchestrator.observability.ObservabilityService;
import com.aidevos.orchestrator.observability.TaskObservability;
import com.aidevos.orchestrator.observability.usage.UsageService;
import com.aidevos.orchestrator.orchestration.AgentExecutionContext;
import com.aidevos.orchestrator.orchestration.AgentExecutionResult;
import com.aidevos.orchestrator.orchestration.ExecutionGraph;
import com.aidevos.orchestrator.orchestration.ExecutionNodeStatus;
import com.aidevos.orchestrator.runtime.AgentRuntimeService;
import com.aidevos.orchestrator.runtime.AgentSession;
import com.aidevos.orchestrator.runtime.AgentSessionStatus;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.timeline.TimelineService;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

/**
 * Integration verification of multi-agent collaboration inside a graph run:
 * the team is created around the runtime session, agents join in graph
 * order, handoffs carry the execution between agents, a node failure emits
 * an ERROR to the repair agent and a completed team writes the
 * AGENT_EXPERIENCE memory record and appears in the task observability
 * bundle.
 */
class MultiAgentRuntimeIntegrationTest extends CollaborationTestBase {

	@Test
	void successfulCodeGraphCompletesTeamWithHandoffsAndMessages() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			success(AgentType.CODEX), success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1");

		assertEquals(AgentSessionStatus.COMPLETED, session.getStatus());
		AgentTeam team = collaborationService.teamForTask("task-1").orElseThrow();
		assertEquals(session.getSessionId(), team.getSessionId());
		assertEquals(AgentTeamStatus.COMPLETED, team.getStatus());
		assertEquals(List.of("HERMES", "CODEX", "TEST_AGENT"), team.getAgents());
		assertEquals(List.of("HERMES->CODEX", "CODEX->TEST_AGENT"),
			collaborationService.handoffs(team.getTeamId()));

		List<AgentMessage> messages = collaborationService.messages(team.getTeamId());
		assertTrue(messages.stream().anyMatch(message ->
			message.getMessageType() == AgentMessageType.REQUEST));
		assertTrue(messages.stream().anyMatch(message ->
			message.getMessageType() == AgentMessageType.RESULT));
		assertTrue(messages.stream().anyMatch(message ->
			message.getMessageType() == AgentMessageType.HANDOFF));
		assertEvent(EventType.AGENT_TEAM_CREATED);
		assertEvent(EventType.AGENT_HANDOFF);
		assertEvent(EventType.AGENT_COLLABORATION_COMPLETED);
		assertEquals(1, memoryRepository.list("project-x", MemoryType.AGENT_EXPERIENCE).size());

		ObservabilityService observability = new ObservabilityService(taskCenterService,
			mock(ExecutionRecordManager.class), mock(AgentMetricsService.class), traceService,
			mock(UsageService.class), mock(ToolMetricsService.class), mock(TimelineService.class),
			runtime, collaborationService);
		TaskObservability bundle = observability.taskObservability("task-1");
		assertEquals(team.getTeamId(), bundle.teamId());
		assertEquals(List.of("HERMES", "CODEX", "TEST_AGENT"), bundle.agents());
		assertEquals(2, bundle.handoffs().size());
		assertEquals(messages.size(), bundle.messages().size());
		assertEquals(1, bundle.sessions().size());
	}

	@Test
	void failedNodeSendsErrorToNextAgentAndFailsTeam() {
		task("task-1");
		AgentRuntimeService runtime = runtime(success(AgentType.HERMES),
			failure(AgentType.CODEX, "code broke"), success(AgentType.TEST_AGENT));

		AgentSession session = runtime.startSession("task-1");

		assertEquals(AgentSessionStatus.FAILED, session.getStatus());
		AgentTeam team = collaborationService.teamForTask("task-1").orElseThrow();
		assertEquals(AgentTeamStatus.FAILED, team.getStatus());
		AgentMessage error = collaborationService.messages(team.getTeamId()).stream()
			.filter(message -> message.getMessageType() == AgentMessageType.ERROR)
			.findFirst().orElseThrow();
		assertEquals("CODEX", error.getFromAgent());
		assertEquals("TEST_AGENT", error.getToAgent());
		assertEquals("code broke", error.getContent());
		assertEvent(EventType.AGENT_COLLABORATION_FAILED);
	}

	@Test
	void repairLoopSendsErrorToRepairAgentAndCompletesOnRetry() {
		task("task-1");
		int[] verifyCalls = new int[1];
		RecordingExecutor test = new RecordingExecutor(AgentType.TEST_AGENT, context -> {
			if ("TEST_AGENT_VERIFY".equals(context.getNodeId())) {
				verifyCalls[0]++;
				if (verifyCalls[0] == 1) {
					return AgentExecutionResult.of(context, ExecutionNodeStatus.FAILED,
						null, "verify broke");
				}
			}
			return AgentExecutionResult.of(context, ExecutionNodeStatus.COMPLETED,
				"verified", null);
		}, null);
		RecordingExecutor repair = success(AgentType.REPAIR_AGENT);
		RecordingExecutor codex = success(AgentType.CODEX);
		AgentRuntimeService runtime = runtime(test, repair, codex);

		TaskRecord taskRecord = task("task-1");
		ExecutionGraph graph = graphBuilder.build("task-1", "REPAIR_TASK");
		AgentExecutionContext context = new AgentExecutionContext();
		context.setTaskId("task-1");
		context.setTask(taskRecord);
		context.setWorkspaceId(taskRecord.getWorkspaceId());
		context.setGraphId(graph.getGraphId());
		context.setInput(taskRecord.getDescription());
		graphExecutor.execute(graph, context);

		AgentTeam team = collaborationService.teamForTask("task-1").orElseThrow();
		assertEquals(AgentTeamStatus.COMPLETED, team.getStatus());
		AgentMessage error = collaborationService.messages(team.getTeamId()).stream()
			.filter(message -> message.getMessageType() == AgentMessageType.ERROR)
			.findFirst().orElseThrow();
		assertEquals("TEST_AGENT", error.getFromAgent());
		assertEquals("REPAIR_AGENT", error.getToAgent());
		assertEquals("verify broke", error.getContent());
		assertTrue(collaborationService.handoffs(team.getTeamId())
			.contains("REPAIR_AGENT->CODEX"));
		assertEquals(2, verifyCalls[0], "verify must retry inside the repair loop");
		assertEquals(2, codex.calls, "CODEX_FIX must re-run after the loop reset");
		assertEvent(EventType.AGENT_COLLABORATION_COMPLETED);
	}
}
