package com.aidevos.orchestrator.openclaw.model;

import java.util.Map;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GatewayMessageSerializationTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	@Test
	void shouldSerializeGatewayRequest() throws Exception {
		GatewayRequest request = new GatewayRequest("request-1", "health", Map.of("detail", true));

		String json = objectMapper.writeValueAsString(request);

		assertEquals("req", objectMapper.readTree(json).get("type").asText());
		assertEquals("request-1", objectMapper.readTree(json).get("id").asText());
		assertEquals("health", objectMapper.readTree(json).get("method").asText());
		assertTrue(objectMapper.readTree(json).get("params").get("detail").asBoolean());
	}

	@Test
	void shouldDeserializeGatewayResponseAndEvent() throws Exception {
		GatewayResponse response = objectMapper.readValue(
				"""
				{"type":"res","id":"request-1","ok":true,"payload":{"status":"ready"}}
				""", GatewayResponse.class);
		GatewayEvent event = objectMapper.readValue(
				"""
				{"type":"event","event":"task.updated","payload":{"id":"task-1"},"seq":2,"stateVersion":{"presence":2,"health":3}}
				""", GatewayEvent.class);

		assertTrue(response.ok());
		assertEquals("ready", response.payload().get("status"));
		assertNull(response.error());
		assertEquals("task.updated", event.event());
		assertEquals("task-1", event.payload().get("id"));
		assertEquals(2L, event.seq());
		assertEquals(2L, event.stateVersion().presence());
		assertEquals(3L, event.stateVersion().health());
	}
}
