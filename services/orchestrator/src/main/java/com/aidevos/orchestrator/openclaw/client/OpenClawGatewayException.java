package com.aidevos.orchestrator.openclaw.client;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

public class OpenClawGatewayException extends RuntimeException {

	private final Map<String, Object> gatewayError;

	public OpenClawGatewayException(Map<String, Object> gatewayError) {
		super(buildMessage(gatewayError));
		this.gatewayError = gatewayError == null
				? Collections.emptyMap()
				: Collections.unmodifiableMap(new LinkedHashMap<>(gatewayError));
	}

	public Object getCode() {
		return gatewayError.get("code");
	}

	public Object getGatewayMessage() {
		return gatewayError.get("message");
	}

	public Object getDetails() {
		return gatewayError.get("details");
	}

	public Map<String, Object> getGatewayError() {
		return gatewayError;
	}

	private static String buildMessage(Map<String, Object> gatewayError) {
		if (gatewayError == null || gatewayError.isEmpty()) {
			return "OpenClaw Gateway request failed";
		}
		Object code = gatewayError.get("code");
		Object message = gatewayError.get("message");
		if (code != null && message != null) {
			return "OpenClaw Gateway request failed [" + code + "]: " + message;
		}
		if (message != null) {
			return "OpenClaw Gateway request failed: " + message;
		}
		if (code != null) {
			return "OpenClaw Gateway request failed [" + code + "]";
		}
		return "OpenClaw Gateway request failed";
	}
}
