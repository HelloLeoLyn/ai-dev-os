package com.aidevos.orchestrator.openclaw.model;

import java.util.Map;

public record GatewayEvent(String type, String event, Map<String, Object> payload, Long seq,
		GatewayStateVersion stateVersion) {
}
