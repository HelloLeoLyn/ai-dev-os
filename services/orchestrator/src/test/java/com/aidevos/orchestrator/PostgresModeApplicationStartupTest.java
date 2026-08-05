package com.aidevos.orchestrator;

import com.aidevos.orchestrator.health.ReadinessGate;
import com.aidevos.orchestrator.persistence.postgresql.PostgresDocumentStore;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.WebApplicationType;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Phase 8-F full PostgreSQL-mode lifecycle validation: the complete Spring
 * context starts against a real database with V1..V7 migrations applied, the
 * readiness gate becomes ready, and a manually launched application context
 * shuts down cleanly (running every {@code @PreDestroy} hook).
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest
@AutoConfigureMockMvc
class PostgresModeApplicationStartupTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	@DynamicPropertySource
	static void postgresProperties(DynamicPropertyRegistry registry) {
		registry.add("aidevos.persistence.type", () -> "postgresql");
		registry.add("aidevos.persistence.postgresql.url", POSTGRES::getJdbcUrl);
		registry.add("aidevos.persistence.postgresql.username", POSTGRES::getUsername);
		registry.add("aidevos.persistence.postgresql.password", POSTGRES::getPassword);
	}

	@Autowired
	private PostgresDocumentStore documentStore;

	@Autowired
	private ReadinessGate readinessGate;

	@Autowired
	private MockMvc mockMvc;

	@Test
	void fullContextStartsWithMigrationsAndReadinessReady() throws Exception {
		assertTrue(documentStore.migrationsComplete());
		assertTrue(readinessGate.isReady());

		mockMvc.perform(get("/api/health"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("UP"));
		mockMvc.perform(get("/api/health/readiness"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.status").value("READY"));
	}

	@Test
	void applicationStartsAndShutsDownCleanlyInPostgresMode() {
		SpringApplication application = new SpringApplication(OrchestratorApplication.class);
		application.setWebApplicationType(WebApplicationType.NONE);

		try (ConfigurableApplicationContext context = application.run(
				"--aidevos.persistence.type=postgresql",
				"--aidevos.persistence.postgresql.url=" + POSTGRES.getJdbcUrl(),
				"--aidevos.persistence.postgresql.username=" + POSTGRES.getUsername(),
				"--aidevos.persistence.postgresql.password=" + POSTGRES.getPassword())) {
			assertTrue(context.getBean(ReadinessGate.class).isReady());
			assertTrue(context.getBean(PostgresDocumentStore.class).migrationsComplete());
		}
	}
}
