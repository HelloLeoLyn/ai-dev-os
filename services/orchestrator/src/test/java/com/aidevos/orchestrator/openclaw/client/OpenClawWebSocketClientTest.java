package com.aidevos.orchestrator.openclaw.client;

import java.util.Map;
import java.util.concurrent.TimeUnit;

import com.aidevos.orchestrator.openclaw.config.OpenClawProperties;
import com.aidevos.orchestrator.openclaw.model.GatewayRequest;
import com.aidevos.orchestrator.openclaw.model.GatewayResponse;
import okhttp3.Response;
import okhttp3.WebSocket;
import okhttp3.WebSocketListener;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class OpenClawWebSocketClientTest {

	private final ObjectMapper objectMapper = new ObjectMapper();

	private MockWebServer server;

	private OpenClawWebSocketClient client;

	private WebSocket serverWebSocket;

	@BeforeEach
	void setUp() throws Exception {
		server = new MockWebServer();
		server.start();
	}

	@AfterEach
	void tearDown() throws Exception {
		if (serverWebSocket != null) {
			serverWebSocket.close(1000, "test complete");
		}
		if (client != null) {
			client.close();
		}
		server.shutdown();
	}

	@Test
	void shouldCompleteChallengeConnectHelloOkHandshake() throws Exception {
		server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
			@Override
			public void onOpen(WebSocket webSocket, Response response) {
				serverWebSocket = webSocket;
				webSocket.send(
						"""
							{"type":"event","event":"connect.challenge","payload":{"nonce":"nonce-1","ts":1}}
							""");
			}

			@Override
			public void onMessage(WebSocket webSocket, String text) {
				try {
					JsonNode request = objectMapper.readTree(text);
					assertEquals("connect", request.get("method").asText());
					assertEquals(4, request.get("params").get("minProtocol").asInt());
					assertEquals(4, request.get("params").get("maxProtocol").asInt());
					assertEquals("operator", request.get("params").get("role").asText());
					assertEquals("test-token", request.get("params").get("auth").get("token").asText());
					assertTrue(request.get("params").get("scopes").toString().contains("operator.read"));
					webSocket.send("""
						{"type":"res","id":"%s","ok":true,"payload":{"type":"hello-ok","protocol":4}}
						""".formatted(request.get("id").asText()));
				}
				catch (Exception error) {
					throw new AssertionError(error);
				}
			}
		}));

		client = createClient();

		client.connect().get(5, TimeUnit.SECONDS);

		assertTrue(client.isConnected());
		assertEquals("/",
				server.takeRequest(5, TimeUnit.SECONDS).getRequestUrl().encodedPath());
	}

	@Test
	void shouldAssociateRequestWithResponseById() throws Exception {
		server.enqueue(new MockResponse().withWebSocketUpgrade(new WebSocketListener() {
			@Override
			public void onOpen(WebSocket webSocket, Response response) {
				serverWebSocket = webSocket;
				webSocket.send(
						"""
							{"type":"event","event":"connect.challenge","payload":{"nonce":"nonce-1","ts":1}}
							""");
			}

			@Override
			public void onMessage(WebSocket webSocket, String text) {
				try {
					JsonNode request = objectMapper.readTree(text);
					if ("connect".equals(request.get("method").asText())) {
						webSocket.send("""
							{"type":"res","id":"%s","ok":true,"payload":{"type":"hello-ok","protocol":4}}
							""".formatted(request.get("id").asText()));
					}
					else {
						webSocket.send("""
							{"type":"res","id":"%s","ok":true,"payload":{"status":"ready"}}
							""".formatted(request.get("id").asText()));
					}
				}
				catch (Exception error) {
					throw new AssertionError(error);
				}
			}
		}));
		client = createClient();
		client.connect().get(5, TimeUnit.SECONDS);

		GatewayResponse response = client.send(
				new GatewayRequest("request-42", "test.read", Map.of()))
			.get(5, TimeUnit.SECONDS);

		assertEquals("request-42", response.id());
		assertEquals("ready", response.payload().get("status"));
	}

	private OpenClawWebSocketClient createClient() {
		OpenClawProperties properties = new OpenClawProperties();
		properties.setGatewayUrl(server.url("/").toString().replaceFirst("^http", "ws"));
		properties.setToken("test-token");
		return new OpenClawWebSocketClient(properties, objectMapper);
	}
}
