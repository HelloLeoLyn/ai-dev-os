package com.aidevos.orchestrator.validation.browser;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.junit.jupiter.api.Test;

class BrowserUrlPolicyTest {
	private final BrowserScenarioProperties properties = new BrowserScenarioProperties();
	private final BrowserUrlPolicy policy = new BrowserUrlPolicy(properties);

	@Test void allowsLoopbackAndConfiguredProjectUrl() {
		assertEquals("localhost", policy.requireAllowed("http://localhost:4173/login").getHost());
		properties.setAllowedBaseUrls(List.of("https://preview.example.test/"));
		assertEquals("preview.example.test", policy.requireAllowed("https://preview.example.test/app").getHost());
		assertThrows(IllegalArgumentException.class, () -> policy.requireAllowed("https://preview.example.test.evil/app"));
	}
	@Test void rejectsExternalSensitiveSchemesAndManagementPorts() {
		assertThrows(IllegalArgumentException.class, () -> policy.requireAllowed("https://example.com"));
		assertThrows(IllegalArgumentException.class, () -> policy.requireAllowed("file:///etc/passwd"));
		assertThrows(IllegalArgumentException.class, () -> policy.requireAllowed("http://127.0.0.1:18789"));
		assertThrows(IllegalArgumentException.class, () -> policy.requireAllowed("http://169.254.169.254/latest/meta-data"));
	}
}
