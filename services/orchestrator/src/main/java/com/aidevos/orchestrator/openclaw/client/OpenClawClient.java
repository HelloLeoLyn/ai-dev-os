package com.aidevos.orchestrator.openclaw.client;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.aidevos.orchestrator.openclaw.model.GatewayEvent;
import com.aidevos.orchestrator.openclaw.model.GatewayRequest;
import com.aidevos.orchestrator.openclaw.model.GatewayResponse;

public interface OpenClawClient extends AutoCloseable {

	CompletableFuture<Void> connect();

	default CompletableFuture<GatewayResponse> request(String method, Map<String, Object> params) {
		return send(new GatewayRequest(UUID.randomUUID().toString(), method, params));
	}

	CompletableFuture<GatewayResponse> send(GatewayRequest request);

	void addEventListener(Consumer<GatewayEvent> listener);

	boolean isConnected();

	@Override
	void close();
}
