package com.aidevos.orchestrator.security;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.aidevos.orchestrator.agent.AgentType;
import org.springframework.stereotype.Component;

/**
 * In-memory security policy registry with the default production policies:
 * CODEX reads/writes files and git but never secrets, OPENCLAW executes the
 * browser, TEST_AGENT reads files and runs test commands, REPAIR_AGENT
 * inherits the CODEX policy.
 */
@Component
public class InMemorySecurityPolicyRegistry implements SecurityPolicyRegistry {

	private static final SecurityPolicy DEFAULT_CODEX = new SecurityPolicy("codex-policy",
		AgentType.CODEX,
		Set.of(SecurityPermission.READ_FILE, SecurityPermission.WRITE_FILE,
			SecurityPermission.GIT_WRITE),
		Set.of(SecurityPermission.SECRET_ACCESS), Set.of());

	private static final SecurityPolicy DEFAULT_OPENCLAW = new SecurityPolicy("openclaw-policy",
		AgentType.OPENCLAW, Set.of(SecurityPermission.BROWSER_EXECUTE), Set.of(), Set.of());

	private static final SecurityPolicy DEFAULT_TEST_AGENT = new SecurityPolicy("test-agent-policy",
		AgentType.TEST_AGENT,
		Set.of(SecurityPermission.READ_FILE, SecurityPermission.EXECUTE_COMMAND),
		Set.of(), Set.of());

	private static final SecurityPolicy DEFAULT_REPAIR_AGENT = new SecurityPolicy("repair-policy",
		AgentType.REPAIR_AGENT,
		Set.of(SecurityPermission.READ_FILE, SecurityPermission.WRITE_FILE,
			SecurityPermission.GIT_WRITE),
		Set.of(SecurityPermission.SECRET_ACCESS), Set.of());

	private static final SecurityPolicy DEFAULT_HERMES = new SecurityPolicy("hermes-policy",
		AgentType.HERMES, Set.of(), Set.of(), Set.of());

	private final Map<AgentType, SecurityPolicy> policies = new LinkedHashMap<>();

	public InMemorySecurityPolicyRegistry() {
		register(DEFAULT_CODEX);
		register(DEFAULT_OPENCLAW);
		register(DEFAULT_TEST_AGENT);
		register(DEFAULT_REPAIR_AGENT);
		register(DEFAULT_HERMES);
	}

	@Override
	public void register(SecurityPolicy policy) {
		if (policy == null || policy.agentType() == null) {
			throw new IllegalArgumentException("Policy and agent type are required");
		}
		if (policies.containsKey(policy.agentType())) {
			throw new IllegalStateException("Duplicate policy for agent: " + policy.agentType());
		}
		policies.put(policy.agentType(), policy);
	}

	@Override
	public SecurityPolicy getPolicy(AgentType agentType) {
		return agentType == null ? null : policies.get(agentType);
	}

	@Override
	public boolean checkPermission(AgentType agentType, SecurityPermission permission) {
		SecurityPolicy policy = getPolicy(agentType);
		return policy != null && policy.allows(permission);
	}

	@Override
	public List<SecurityPolicy> listPolicies() {
		return List.copyOf(new ArrayList<>(policies.values()));
	}
}
