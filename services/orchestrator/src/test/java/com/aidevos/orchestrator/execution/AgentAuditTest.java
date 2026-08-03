package com.aidevos.orchestrator.execution;

import com.aidevos.orchestrator.agent.*;
import com.aidevos.orchestrator.audit.*;
import com.aidevos.orchestrator.executor.*;
import com.aidevos.orchestrator.manager.AgentManager;
import com.aidevos.orchestrator.model.*;
import java.util.List;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AgentAuditTest {
	@Test
	void repeatedTaskExecutionsKeepDistinctAgentSelectionEvents() throws Exception {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		AuditService audit = new AuditService(events);
		ExecutionEngine engine = engine(audit, successfulExecutor());
		TaskDefinition task = task();

		engine.execute(task);
		engine.execute(task);

		assertEquals(2, events.query(EventQuery.all()).stream()
			.filter(event -> event.type() == EventType.AGENT_SELECTED).count());
	}
	@Test
	void recordsSelectedStartedAndCompletedAgentEvents() throws Exception {
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		AuditService audit = new AuditService(events);
		ExecutionEngine engine = engine(audit, successfulExecutor());

		assertTrue(engine.execute(task(), "job-1").isSuccess());

		assertEquals(List.of(EventType.AGENT_SELECTED, EventType.AGENT_EXECUTION_STARTED,
			EventType.AGENT_EXECUTION_COMPLETED), events.query(EventQuery.all()).stream()
			.filter(event -> event.type().name().startsWith("AGENT_"))
			.map(EventRecord::type).toList());
	}

	@Test
	void recordsFailureAndAuditFailureDoesNotAffectAgentResult() throws Exception {
		AgentExecutor failed = mock(AgentExecutor.class);
		when(failed.getType()).thenReturn("mock");
		when(failed.execute(any())).thenThrow(new IllegalStateException("executor down"));
		InMemoryAuditRepository events = new InMemoryAuditRepository();
		assertFalse(engine(new AuditService(events), failed).execute(task(), "job-1").isSuccess());
		assertTrue(events.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.AGENT_EXECUTION_FAILED));

		AuditService brokenAudit = new AuditService(new AuditRepository() {
			public EventRecord append(EventRecord event) { throw new IllegalStateException("audit down"); }
			public EventRecord get(String id) { return null; }
			public List<EventRecord> query(EventQuery query) { return List.of(); }
		});
		assertTrue(engine(brokenAudit, successfulExecutor()).execute(task(), "job-2").isSuccess());
	}

	private ExecutionEngine engine(AuditService audit, AgentExecutor executor) {
		AgentManager agents = new AgentManager();
		AgentDefinition agent = new AgentDefinition();
		agent.setName("agent-1"); agent.setExecutor("mock");
		agents.register(agent);
		ExecutorRegistry registry = new ExecutorRegistry(List.of(executor));
		AgentResolver resolver = new AgentResolver(agents, new AgentSelector(agents),
			new ExecutorManager(agents, registry), audit);
		return new ExecutionEngine(resolver,
			new ExecutionRecordManager(new InMemoryExecutionRecordRepository(), audit), audit);
	}

	private AgentExecutor successfulExecutor() throws Exception {
		AgentExecutor executor = mock(AgentExecutor.class);
		when(executor.getType()).thenReturn("mock");
		ExecutionResult result = new ExecutionResult(); result.setSuccess(true);
		when(executor.execute(any())).thenReturn(result);
		return executor;
	}

	private TaskDefinition task() {
		TaskDefinition task = new TaskDefinition();
		task.setId("task-1"); task.setAgentName("agent-1");
		return task;
	}
}
