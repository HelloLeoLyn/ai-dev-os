package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import com.aidevos.orchestrator.persistence.LeaseableJobRepository;
import com.aidevos.orchestrator.persistence.LeaseableJobRepositoryContractTest;
import org.junit.jupiter.api.BeforeEach;
import org.postgresql.ds.PGSimpleDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import tools.jackson.databind.ObjectMapper;

@Testcontainers(disabledWithoutDocker = true)
class PostgresLeaseableJobRepositoryContractTest extends LeaseableJobRepositoryContractTest {

	@Container
	static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:17-alpine");

	private PostgresLeaseableJobRepository repository;

	@BeforeEach
	void cleanJobs() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl(POSTGRES.getJdbcUrl());
		dataSource.setUser(POSTGRES.getUsername());
		dataSource.setPassword(POSTGRES.getPassword());
		new PostgresDocumentStore(dataSource, new ObjectMapper());
		try (Connection connection = dataSource.getConnection();
				Statement statement = connection.createStatement()) {
			statement.execute("DELETE FROM jobs");
		}
		catch (SQLException exception) {
			throw new IllegalStateException(exception);
		}
	}

	@Override
	protected LeaseableJobRepository repository() {
		if (repository == null) {
			PGSimpleDataSource dataSource = new PGSimpleDataSource();
			dataSource.setUrl(POSTGRES.getJdbcUrl());
			dataSource.setUser(POSTGRES.getUsername());
			dataSource.setPassword(POSTGRES.getPassword());
			new PostgresDocumentStore(dataSource, new ObjectMapper());
			repository = new PostgresLeaseableJobRepository(dataSource, new ObjectMapper());
		}
		return repository;
	}
}
