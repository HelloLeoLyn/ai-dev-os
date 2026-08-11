package com.aidevos.orchestrator.adaptive;

/**
 * The decision the adaptive service produces for a failed node: which action
 * to take (retry / switch agent / modify graph / change tool / replan), the
 * reason and confidence, the affected node and, when the action needs it, the
 * target agent or the tool to use.
 */
public class AdaptationDecision {

	private final String decisionId;
	private final String taskId;
	private final String nodeId;
	private final String reason;
	private final AdaptationAction action;
	private final double confidence;
	private final String targetAgent;
	private final String toolId;

	public AdaptationDecision(String decisionId, String taskId, String nodeId, String reason,
			AdaptationAction action, double confidence, String targetAgent, String toolId) {
		this.decisionId = decisionId;
		this.taskId = taskId;
		this.nodeId = nodeId;
		this.reason = reason == null ? "" : reason;
		this.action = action == null ? AdaptationAction.RETRY : action;
		this.confidence = confidence;
		this.targetAgent = targetAgent;
		this.toolId = toolId;
	}

	public String getDecisionId() {
		return decisionId;
	}

	public String getTaskId() {
		return taskId;
	}

	public String getNodeId() {
		return nodeId;
	}

	public String getReason() {
		return reason;
	}

	public AdaptationAction getAction() {
		return action;
	}

	public double getConfidence() {
		return confidence;
	}

	public String getTargetAgent() {
		return targetAgent;
	}

	public String getToolId() {
		return toolId;
	}
}
