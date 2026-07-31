package com.aidevos.orchestrator.openclaw.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenClawPropertiesTest {

	@Test
	void shouldUseLocalGatewayDefaults() {
		OpenClawProperties properties = new OpenClawProperties();

		assertEquals("ws://127.0.0.1:18789", properties.getGatewayUrl());
		assertEquals("", properties.getToken());
	}
}
