package com.aidevos.orchestrator.openclaw.client;

import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import com.aidevos.orchestrator.openclaw.model.GatewayEvent;
import com.aidevos.orchestrator.openclaw.model.GatewayRequest;
import com.aidevos.orchestrator.openclaw.model.GatewayResponse;

public interface OpenClawClient extends AutoCloseable {

	CompletableFuture<Void> connect();

	CompletableFuture<GatewayResponse> send(GatewayRequest request);

	void addEventListener(Consumer<GatewayEvent> listener);

	boolean isConnected();

	@Override
	void close();
}
