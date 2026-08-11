package com.aidevos.orchestrator.collaboration;

/**
 * The kinds of messages agents exchange inside a team: REQUEST asks the next
 * agent to work, RESPONSE answers a request, HANDOFF transfers execution
 * context, RESULT reports a finished outcome and ERROR reports a failure.
 */
public enum AgentMessageType {
	REQUEST,
	RESPONSE,
	HANDOFF,
	RESULT,
	ERROR,
	HUMAN_REQUEST,
	HUMAN_RESPONSE
}
