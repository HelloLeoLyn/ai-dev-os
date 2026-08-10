package com.aidevos.orchestrator.security;

import com.aidevos.orchestrator.agent.AgentType;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17-A: default security policies and agent permission matching.
 */
class SecurityPolicyTest {

	private final SecurityPolicyRegistry registry = new InMemorySecurityPolicyRegistry();

	@Test
	void shouldAllowCodexFileAndGitAccessButDenySecrets() {
		SecurityPolicy codex = registry.getPolicy(AgentType.CODEX);

		assertNotNull(codex);
		assertTrue(codex.allows(SecurityPermission.READ_FILE));
		assertTrue(codex.allows(SecurityPermission.WRITE_FILE));
		assertTrue(codex.allows(SecurityPermission.GIT_WRITE));
		assertFalse(codex.allows(SecurityPermission.SECRET_ACCESS));
	}

	@Test
	void shouldAllowOpenClawBrowserOnly() {
		SecurityPolicy openclaw = registry.getPolicy(AgentType.OPENCLAW);

		assertTrue(openclaw.allows(SecurityPermission.BROWSER_EXECUTE));
		assertFalse(openclaw.allows(SecurityPermission.GIT_WRITE));
		assertFalse(openclaw.allows(SecurityPermission.EXECUTE_COMMAND));
	}

	@Test
	void shouldAllowTestAgentReadAndCommandExecution() {
		SecurityPolicy testAgent = registry.getPolicy(AgentType.TEST_AGENT);

		assertTrue(testAgent.allows(SecurityPermission.READ_FILE));
		assertTrue(testAgent.allows(SecurityPermission.EXECUTE_COMMAND));
		assertFalse(testAgent.allows(SecurityPermission.WRITE_FILE));
		assertFalse(testAgent.allows(SecurityPermission.SECRET_ACCESS));
	}

	@Test
	void shouldInheritCodexPolicyForRepairAgent() {
		SecurityPolicy repair = registry.getPolicy(AgentType.REPAIR_AGENT);

		assertTrue(repair.allows(SecurityPermission.READ_FILE));
		assertTrue(repair.allows(SecurityPermission.WRITE_FILE));
		assertTrue(repair.allows(SecurityPermission.GIT_WRITE));
		assertFalse(repair.allows(SecurityPermission.SECRET_ACCESS));
	}

	@Test
	void shouldEvaluatePermissionThroughRegistry() {
		assertTrue(registry.checkPermission(AgentType.CODEX, SecurityPermission.GIT_WRITE));
		assertFalse(registry.checkPermission(AgentType.CODEX, SecurityPermission.SECRET_ACCESS));
		assertFalse(registry.checkPermission(AgentType.HERMES, SecurityPermission.READ_FILE));
	}
}
