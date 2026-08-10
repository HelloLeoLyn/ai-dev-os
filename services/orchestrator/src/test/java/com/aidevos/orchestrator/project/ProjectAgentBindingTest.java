package com.aidevos.orchestrator.project;

import java.util.List;

import com.aidevos.orchestrator.agent.AgentType;
import com.aidevos.orchestrator.audit.AuditService;
import com.aidevos.orchestrator.audit.EventQuery;
import com.aidevos.orchestrator.audit.EventType;
import com.aidevos.orchestrator.audit.InMemoryAuditRepository;
import com.aidevos.orchestrator.project.agent.ProjectAgentBinding;
import com.aidevos.orchestrator.project.agent.ProjectAgentRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17-B: per-project agent binding isolation. Project A and project B
 * can enable different agent sets without interfering.
 */
class ProjectAgentBindingTest {

	private ProjectAgentRegistry registry;
	private InMemoryAuditRepository auditRepository;

	@BeforeEach
	void setUp() {
		auditRepository = new InMemoryAuditRepository();
		registry = new ProjectAgentRegistry(new AuditService(auditRepository));
	}

	@Test
	void shouldKeepProjectAgentSetsIsolated() {
		registry.bindAgent("project-a", AgentType.CODEX, 10);
		registry.bindAgent("project-a", AgentType.OPENCLAW, 20);
		registry.bindAgent("project-b", AgentType.CODEX, 10);
		registry.bindAgent("project-b", AgentType.TEST_AGENT, 20);

		List<ProjectAgentBinding> projectA = registry.getProjectAgents("project-a");
		List<ProjectAgentBinding> projectB = registry.getProjectAgents("project-b");

		assertEquals(2, projectA.size());
		assertEquals(2, projectB.size());
		assertTrue(projectA.stream().noneMatch(binding ->
			binding.agentType() == AgentType.TEST_AGENT));
		assertTrue(projectB.stream().noneMatch(binding ->
			binding.agentType() == AgentType.OPENCLAW));
		assertTrue(registry.isEnabled("project-a", AgentType.CODEX));
		assertTrue(registry.isEnabled("project-b", AgentType.TEST_AGENT));
	}

	@Test
	void shouldUnbindAgent() {
		registry.bindAgent("project-a", AgentType.CODEX, 10);

		assertTrue(registry.unbindAgent("project-a", AgentType.CODEX));
		assertFalse(registry.isEnabled("project-a", AgentType.CODEX));
		assertTrue(registry.getProjectAgents("project-a").isEmpty());
	}

	@Test
	void shouldAuditProjectAgentBinding() {
		registry.bindAgent("project-a", AgentType.CODEX, 10);

		assertTrue(auditRepository.query(EventQuery.all()).stream()
			.anyMatch(event -> event.type() == EventType.PROJECT_AGENT_BOUND
				&& "project-a".equals(event.metadata().get("projectId"))
				&& "CODEX".equals(event.metadata().get("agentType"))));
	}
}
