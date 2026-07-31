package com.aidevos.orchestrator.openclaw.model;

import java.util.Map;

public record GatewayRequest(String type, String id, String method, Map<String, Object> params) {

	public GatewayRequest(String id, String method, Map<String, Object> params) {
		this("req", id, method, params);
	}
}
