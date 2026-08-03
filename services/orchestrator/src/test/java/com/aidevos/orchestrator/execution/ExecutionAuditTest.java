package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.*;
import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.executor.*;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ExecutionAuditTest {
	@Test
	void recordsExecutionAndRecordEvents() throws Exception {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		AuditService audit = new AuditService(events);
		ExecutionRecordManager records = new ExecutionRecordManager(
			new InMemoryExecutionRecordRepository(), audit);
		AgentExecutor executor = mock(AgentExecutor.class);
		when(executor.getType()).thenReturn("mock");
		ExecutionResult result = new ExecutionResult(); result.setSuccess(true);
		when(executor.execute(any())).thenReturn(result);
		AgentManager agents = new AgentManager();
		AgentDefinition agent = new AgentDefinition(); agent.setName("agent-1"); agent.setExecutor("mock");
		agents.register(agent);
		ExecutorRegistry registry = new ExecutorRegistry(List.of(executor));
		ExecutionEngine engine = new ExecutionEngine(new AgentResolver(agents,
			new AgentSelector(agents), new ExecutorManager(agents, registry)), records, audit);
		TaskDefinition task = new TaskDefinition(); task.setId("task-1"); task.setAgentName("agent-1");

		assertTrue(engine.execute(task, "job-1").isSuccess());

		assertEquals(List.of(EventType.AGENT_EXECUTION_STARTED, EventType.EXECUTION_STARTED,
			EventType.AGENT_EXECUTION_COMPLETED, EventType.EXECUTION_RECORD_SAVED,
			EventType.EXECUTION_COMPLETED), events.query(EventQuery.all()).stream()
			.map(EventRecord::type).toList());
		assertEquals("job-1", events.query(EventQuery.all()).getLast().jobId());
	}
}
