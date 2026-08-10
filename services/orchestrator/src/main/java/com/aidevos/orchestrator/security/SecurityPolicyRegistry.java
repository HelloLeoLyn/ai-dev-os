package com.aidevos.orchestrator.security;

import com.aidevos.orchestrator.agent.AgentType;

/**
 * Registry of security policies: registration, lookup by agent type and
 * permission evaluation.
 */
public interface SecurityPolicyRegistry {

	void register(SecurityPolicy policy);

	SecurityPolicy getPolicy(AgentType agentType);

	boolean checkPermission(AgentType agentType, SecurityPermission permission);

	java.util.List<SecurityPolicy> listPolicies();
}
