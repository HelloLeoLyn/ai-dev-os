package com.aidevos.orchestrator.validation.browser;

import java.net.URI;
import java.util.Set;

import org.springframework.stereotype.Component;

@Component
public class BrowserUrlPolicy {
	private static final Set<Integer> FORBIDDEN_PORTS = Set.of(22, 2375, 2376, 5432, 6379, 18789);
	private final BrowserScenarioProperties properties;
	public BrowserUrlPolicy(BrowserScenarioProperties properties) { this.properties = properties; }

	public URI requireAllowed(String value) {
		try {
			URI uri = URI.create(value);
			if (!("http".equalsIgnoreCase(uri.getScheme()) || "https".equalsIgnoreCase(uri.getScheme())))
				throw new IllegalArgumentException("Browser target must use http or https");
			String host = uri.getHost();
			boolean local = "localhost".equalsIgnoreCase(host) || "127.0.0.1".equals(host) || "::1".equals(host);
			boolean configured = properties.getAllowedBaseUrls().stream().anyMatch(base -> sameOriginAndPath(uri, base));
			if (!local && !configured) throw new IllegalArgumentException("Browser target is outside the project allowlist");
			if (uri.getPort() > 0 && FORBIDDEN_PORTS.contains(uri.getPort()))
				throw new IllegalArgumentException("Browser target port is forbidden");
			return uri;
		}
		catch (IllegalArgumentException exception) { throw exception; }
		catch (RuntimeException exception) { throw new IllegalArgumentException("Invalid browser target URL", exception); }
	}

	private boolean sameOriginAndPath(URI target, String baseValue) {
		try {
			URI base = URI.create(baseValue);
			int targetPort = target.getPort() < 0 ? defaultPort(target.getScheme()) : target.getPort();
			int basePort = base.getPort() < 0 ? defaultPort(base.getScheme()) : base.getPort();
			String basePath = base.getPath() == null || base.getPath().isBlank() ? "/" : base.getPath();
			String targetPath = target.getPath() == null || target.getPath().isBlank() ? "/" : target.getPath();
			return base.getScheme() != null && base.getScheme().equalsIgnoreCase(target.getScheme())
				&& base.getHost() != null && base.getHost().equalsIgnoreCase(target.getHost())
				&& basePort == targetPort && targetPath.startsWith(basePath);
		}
		catch (RuntimeException ignored) { return false; }
	}
	private int defaultPort(String scheme) { return "https".equalsIgnoreCase(scheme) ? 443 : 80; }
}
