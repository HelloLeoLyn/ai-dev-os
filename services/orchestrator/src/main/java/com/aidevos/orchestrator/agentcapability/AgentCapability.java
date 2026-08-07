package com.aidevos.orchestrator.agentcapability;

import java.util.List;

/**
 * A named agent capability used to match task types against registered
 * agents. Capabilities are declared by AgentDefinition instances and resolved
 * dynamically by AgentCapabilityResolver; no agent is hard-coded.
 */
public class AgentCapability {

	public static final String PLANNING = "planning";
	public static final String CODING = "coding";
	public static final String BROWSER = "browser";
	public static final String TESTING = "testing";
	public static final String ANALYSIS = "analysis";

	private final String capabilityId;
	private final String name;
	private final String description;

	private AgentCapability(String capabilityId, String name, String description) {
		this.capabilityId = capabilityId;
		this.name = name;
		this.description = description;
	}

	public String getCapabilityId() {
		return capabilityId;
	}

	public String getName() {
		return name;
	}

	public String getDescription() {
		return description;
	}

	/**
	 * Preset capability catalog: planning, coding, browser, testing and
	 * analysis.
	 */
	public static List<AgentCapability> presets() {
		return List.of(
			new AgentCapability(PLANNING, "Planning", "任务规划与分析拆解"),
			new AgentCapability(CODING, "Coding", "代码生成与修改"),
			new AgentCapability(BROWSER, "Browser", "浏览器自动化与 UI 测试"),
			new AgentCapability(TESTING, "Testing", "自动化测试执行与验证"),
			new AgentCapability(ANALYSIS, "Analysis", "数据分析与方案设计"));
	}
}
