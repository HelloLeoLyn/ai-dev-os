package com.aidevos.orchestrator.openclaw.config;

import java.time.Duration;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class OpenClawPropertiesTest {

	@Test
	void shouldUseLocalGatewayDefaults() {
		OpenClawProperties properties = new OpenClawProperties();

		assertEquals("ws://127.0.0.1:18789", properties.getGatewayUrl());
		assertEquals("", properties.getToken());
		assertEquals(Duration.ofSeconds(5), properties.getConnectTimeout());
		assertEquals(Duration.ofSeconds(30), properties.getRequestTimeout());
		assertEquals(Path.of(System.getProperty("user.home"), ".ai-dev-os", "openclaw",
				"device-identity.json"), properties.getDeviceIdentityPath());
	}
}
