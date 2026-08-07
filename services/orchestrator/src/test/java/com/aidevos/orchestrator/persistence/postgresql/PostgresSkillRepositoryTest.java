package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import com.aidevos.orchestrator.skill.PostgresSkillRepository;
import com.aidevos.orchestrator.skill.Skill;
import com.aidevos.orchestrator.skill.SkillType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers(disabledWithoutDocker = true)
class PostgresSkillRepositoryTest {

	private static final Instant NOW = Instant.parse("2026-08-06T00:00:00Z");

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PostgresSkillRepository repository;

	@BeforeEach
	void setUp() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM skills");
		}
		catch (SQLException exception) {
			throw new IllegalStateException(exception);
		}
		repository = new PostgresSkillRepository(dataSource);
	}

	@Test
	void saveAndGetRoundTrip() {
		repository.save(skill("coding-skill", "Coding Skill", "1.0.0", true, NOW));

		Skill stored = repository.get("coding-skill");
		assertEquals("coding-skill", stored.getSkillId());
		assertEquals("Coding Skill", stored.getName());
		assertEquals("1.0.0", stored.getVersion());
		assertTrue(stored.isEnabled());
		assertEquals(NOW, stored.getCreatedAt());
		assertEquals(NOW, stored.getUpdatedAt());
		assertNull(repository.get("missing"));
	}

	@Test
	void saveUpdatesExistingSkill() {
		repository.save(skill("coding-skill", "Old", "1.0.0", true, NOW));
		repository.save(skill("coding-skill", "Coding Skill", "2.0.0", false, NOW.plusSeconds(60)));

		Skill stored = repository.get("coding-skill");
		assertEquals("Coding Skill", stored.getName());
		assertEquals("2.0.0", stored.getVersion());
		assertFalse(stored.isEnabled());
		assertEquals(NOW.plusSeconds(60), stored.getUpdatedAt());
	}

	@Test
	void listReturnsAllSkillsOrdered() {
		repository.save(skill("tester-skill", "Tester Skill", "1.0.0", true, NOW.plusSeconds(2)));
		repository.save(skill("coder-skill", "Coder Skill", "1.0.0", true, NOW));

		List<Skill> stored = repository.list();

		assertEquals(List.of("coder-skill", "tester-skill"),
			stored.stream().map(Skill::getSkillId).toList());
	}

	@Test
	void deleteRemovesSkill() {
		repository.save(skill("coding-skill", "Coding Skill", "1.0.0", true, NOW));

		assertTrue(repository.delete("coding-skill"));
		assertNull(repository.get("coding-skill"));
		assertFalse(repository.delete("coding-skill"));
	}

	private Skill skill(String skillId, String name, String version, boolean enabled,
			Instant updatedAt) {
		return new Skill(skillId, name, "描述", SkillType.CODING, version, enabled,
			List.of("read_code"), "instructions", NOW, updatedAt);
	}
}
