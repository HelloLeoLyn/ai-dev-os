package com.aidevos.orchestrator.openclaw.config;

import java.time.Duration;
import java.nio.file.Path;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Component
@ConfigurationProperties(prefix = "openclaw")
public class OpenClawProperties {

	private String gatewayUrl = "ws://127.0.0.1:18789";

	private String token = "";

	private Duration connectTimeout = Duration.ofSeconds(5);

	private Duration requestTimeout = Duration.ofSeconds(30);

	private Duration agentWaitTimeout = Duration.ofMinutes(2);

	private Path deviceIdentityPath = Path.of(System.getProperty("user.home"),
			".ai-dev-os", "openclaw", "device-identity.json");

	public String getGatewayUrl() {
		return gatewayUrl;
	}

	public void setGatewayUrl(String gatewayUrl) {
		this.gatewayUrl = gatewayUrl;
	}

	public String getToken() {
		return token;
	}

	public void setToken(String token) {
		this.token = token;
	}

	public Duration getConnectTimeout() {
		return connectTimeout;
	}

	public void setConnectTimeout(Duration connectTimeout) {
		this.connectTimeout = connectTimeout;
	}

	public Duration getRequestTimeout() {
		return requestTimeout;
	}

	public void setRequestTimeout(Duration requestTimeout) {
		this.requestTimeout = requestTimeout;
	}

	public Duration getAgentWaitTimeout() {
		return agentWaitTimeout;
	}

	public void setAgentWaitTimeout(Duration agentWaitTimeout) {
		this.agentWaitTimeout = agentWaitTimeout;
	}

	public Path getDeviceIdentityPath() {
		return deviceIdentityPath;
	}

	public void setDeviceIdentityPath(Path deviceIdentityPath) {
		this.deviceIdentityPath = deviceIdentityPath;
	}
}
