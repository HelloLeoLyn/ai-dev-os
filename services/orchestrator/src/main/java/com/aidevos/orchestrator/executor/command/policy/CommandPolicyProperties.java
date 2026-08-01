package com.aidevos.orchestrator.executor.command.policy;

import java.util.ArrayList;
import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "command.policy")
public class CommandPolicyProperties {

	private boolean enabled;

	private PolicyAction defaultAction = PolicyAction.DENY;

	private List<Rule> rules = new ArrayList<>();

	public boolean isEnabled() {
		return enabled;
	}

	public void setEnabled(boolean enabled) {
		this.enabled = enabled;
	}

	public PolicyAction getDefaultAction() {
		return defaultAction;
	}

	public void setDefaultAction(PolicyAction defaultAction) {
		this.defaultAction = defaultAction;
	}

	public List<Rule> getRules() {
		return rules;
	}

	public void setRules(List<Rule> rules) {
		this.rules = rules;
	}

	public static class Rule {

		private String id;

		private String executable;

		private List<String> argumentPrefix = new ArrayList<>();

		private PolicyAction action = PolicyAction.DENY;

		public String getId() {
			return id;
		}

		public void setId(String id) {
			this.id = id;
		}

		public String getExecutable() {
			return executable;
		}

		public void setExecutable(String executable) {
			this.executable = executable;
		}

		public List<String> getArgumentPrefix() {
			return argumentPrefix;
		}

		public void setArgumentPrefix(List<String> argumentPrefix) {
			this.argumentPrefix = argumentPrefix;
		}

		public PolicyAction getAction() {
			return action;
		}

		public void setAction(PolicyAction action) {
			this.action = action;
		}
	}
}
