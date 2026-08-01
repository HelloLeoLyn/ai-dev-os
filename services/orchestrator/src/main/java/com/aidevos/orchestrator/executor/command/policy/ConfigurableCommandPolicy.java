package com.aidevos.orchestrator.executor.command.policy;

import java.util.List;

import com.aidevos.orchestrator.executor.command.CommandOptions;
import org.springframework.stereotype.Component;

@Component
public class ConfigurableCommandPolicy implements CommandPolicy {

	private static final String POLICY_DISABLED = "policy-disabled";
	private static final String DEFAULT_RULE = "default";

	private final CommandPolicyProperties properties;

	public ConfigurableCommandPolicy(CommandPolicyProperties properties) {
		this.properties = properties;
	}

	@Override
	public PolicyDecision evaluate(CommandOptions options) {
		if (!properties.isEnabled()) {
			return PolicyDecision.allow(POLICY_DISABLED);
		}

		List<String> command = options.getCommand();
		if (command == null || command.isEmpty() || command.getFirst() == null
				|| command.getFirst().isBlank()) {
			return PolicyDecision.deny("invalid-command");
		}

		for (CommandPolicyProperties.Rule rule : properties.getRules()) {
			if (matches(rule, command)) {
				return decision(rule.getAction(), rule.getId());
			}
		}
		return decision(properties.getDefaultAction(), DEFAULT_RULE);
	}

	private boolean matches(CommandPolicyProperties.Rule rule, List<String> command) {
		if (rule.getExecutable() == null || !rule.getExecutable().equals(command.getFirst())) {
			return false;
		}

		List<String> argumentPrefix = rule.getArgumentPrefix();
		if (argumentPrefix == null || argumentPrefix.isEmpty()) {
			return true;
		}
		if (command.size() - 1 < argumentPrefix.size()) {
			return false;
		}
		return command.subList(1, 1 + argumentPrefix.size()).equals(argumentPrefix);
	}

	private PolicyDecision decision(PolicyAction action, String ruleId) {
		return switch (action) {
			case ALLOW -> PolicyDecision.allow(ruleId);
			case DENY -> PolicyDecision.deny(ruleId);
			case REQUIRE_APPROVAL -> PolicyDecision.requireApproval(ruleId);
		};
	}
}
