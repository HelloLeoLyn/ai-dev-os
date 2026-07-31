package com.aidevos.orchestrator.openclaw.model;

import java.util.Map;

public record GatewayResponse(String type, String id, boolean ok, Map<String, Object> payload,
		Map<String, Object> error) {
}
