package com.aidevos.orchestrator.persistence.postgresql;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.yaml.snakeyaml.Yaml;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Phase 17-D deployment smoke test: validates the docker-compose topology
 * (postgres + orchestrator + frontend on the shared network with a persistent
 * volume), the Dockerfiles and the production profile wiring. File-based so it
 * never requires a running Docker daemon.
 */
class DockerComposeSmokeTest {

	@Test
	void composeDefinesPostgresBackendFrontend() throws Exception {
		Path root = projectRoot();
		@SuppressWarnings("unchecked")
		Map<String, Object> compose = new Yaml().load(
			Files.readString(root.resolve("docker-compose.yml")));

		@SuppressWarnings("unchecked")
		Map<String, Object> services = (Map<String, Object>) compose.get("services");
		assertTrue(services.containsKey("postgres"), "postgres service missing");
		assertTrue(services.containsKey("orchestrator"), "orchestrator service missing");
		assertTrue(services.containsKey("frontend"), "frontend service missing");

		@SuppressWarnings("unchecked")
		Map<String, Object> postgres = (Map<String, Object>) services.get("postgres");
		assertTrue(String.valueOf(postgres.get("image")).startsWith("postgres:17"),
			"postgres image must be 17");

		@SuppressWarnings("unchecked")
		Map<String, Object> orchestrator = (Map<String, Object>) services.get("orchestrator");
		@SuppressWarnings("unchecked")
		Map<String, Object> environment = (Map<String, Object>) orchestrator.get("environment");
		assertTrue("prod".equals(environment.get("SPRING_PROFILES_ACTIVE")));
		assertTrue(String.valueOf(environment.get("POSTGRES_URL"))
			.contains("postgres:5432/ai_dev_os"));

		@SuppressWarnings("unchecked")
		Map<String, Object> networks = (Map<String, Object>) compose.get("networks");
		assertTrue(networks.containsKey("aidevos-network"), "network missing");
		@SuppressWarnings("unchecked")
		Map<String, Object> volumes = (Map<String, Object>) compose.get("volumes");
		assertTrue(volumes.containsKey("postgres-data"), "postgres volume missing");
	}

	@Test
	void dockerfilesAndProdProfileArePresent() throws Exception {
		Path root = projectRoot();
		assertTrue(Files.exists(root.resolve("services/orchestrator/Dockerfile")));
		assertTrue(Files.exists(root.resolve("services/orchestrator/frontend/Dockerfile")));
		assertTrue(Files.exists(root.resolve("services/orchestrator/frontend/nginx.conf")));
		assertTrue(Files.exists(root.resolve("docs/deployment/README.md")));

		String dockerfile = Files.readString(root.resolve("services/orchestrator/Dockerfile"));
		assertTrue(dockerfile.contains("maven") && dockerfile.contains("eclipse-temurin:21"),
			"orchestrator Dockerfile must use a multi-stage Maven/Java 21 build");

		String prod = Files.readString(root.resolve(
			"services/orchestrator/src/main/resources/application-prod.yml"));
		assertTrue(prod.contains("type: postgresql"),
			"prod profile must configure aidevos.persistence.type=postgresql");
		assertTrue(prod.contains("POSTGRES_URL"));
		assertTrue(prod.contains("POSTGRES_USER"));
		assertTrue(prod.contains("POSTGRES_PASSWORD"));
		assertTrue(prod.contains("management:"), "prod profile must configure actuator health");
	}

	private Path projectRoot() {
		Path current = Path.of("").toAbsolutePath();
		while (current != null && !Files.exists(current.resolve("docker-compose.yml"))) {
			current = current.getParent();
		}
		if (current == null) {
			throw new IllegalStateException("Cannot locate the project root");
		}
		return current;
	}
}
