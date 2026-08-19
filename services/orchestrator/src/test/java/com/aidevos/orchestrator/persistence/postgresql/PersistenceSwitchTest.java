package com.aidevos.orchestrator.persistence.postgresql;

import javax.sql.DataSource;

import com.aidevos.orchestrator.change.ChangeRepository;
import com.aidevos.orchestrator.ci.CiRepository;
import com.aidevos.orchestrator.commit.CommitRepository;
import com.aidevos.orchestrator.execution.ExecutionRecordRepository;
import com.aidevos.orchestrator.feedback.FeedbackRepository;
import com.aidevos.orchestrator.memory.InMemoryMemoryRepository;
import com.aidevos.orchestrator.memory.MemoryRepository;
import com.aidevos.orchestrator.observability.InMemoryTraceRepository;
import com.aidevos.orchestrator.observability.TraceRepository;
import com.aidevos.orchestrator.observability.usage.InMemoryUsageRepository;
import com.aidevos.orchestrator.observability.usage.UsageRepository;
import com.aidevos.orchestrator.project.InMemoryProjectRepository;
import com.aidevos.orchestrator.project.ProjectRepository;
import com.aidevos.orchestrator.repair.RepairRepository;
import com.aidevos.orchestrator.taskcenter.TaskRepository;
import com.aidevos.orchestrator.workspace.InMemoryWorkspaceRepository;
import com.aidevos.orchestrator.workspace.WorkspaceRepository;
import org.junit.jupiter.api.Test;
import org.postgresql.ds.PGSimpleDataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Import;
import tools.jackson.databind.ObjectMapper;

import static org.junit.jupiter.api.Assertions.assertInstanceOf;

/**
 * Phase 17-D: verifies that the persistence type switch selects the in-memory
 * repositories by default and the JDBC repositories under
 * {@code aidevos.persistence.type=postgresql}, without touching the service
 * layer.
 */
class PersistenceSwitchTest {

	@Test
	void inMemoryModeSelectsInMemoryRepositories() {
		ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(InMemoryRepositories.class)
			.withPropertyValues("aidevos.persistence.type=in-memory");

		runner.run(context -> {
			assertInstanceOf(InMemoryProjectRepository.class,
				context.getBean(ProjectRepository.class));
			assertInstanceOf(InMemoryWorkspaceRepository.class,
				context.getBean(WorkspaceRepository.class));
			assertInstanceOf(InMemoryMemoryRepository.class,
				context.getBean(MemoryRepository.class));
			assertInstanceOf(InMemoryTraceRepository.class,
				context.getBean(TraceRepository.class));
			assertInstanceOf(InMemoryUsageRepository.class,
				context.getBean(UsageRepository.class));
		});
	}

	@Test
	void postgresqlModeSelectsPostgresRepositories() {
		PGSimpleDataSource dataSource = new PGSimpleDataSource();
		dataSource.setUrl("jdbc:postgresql://localhost:5432/ai_dev_os");
		dataSource.setUser("ai_dev_os");

		ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(PostgresRepositories.class)
			.withBean(DataSource.class, () -> dataSource)
			.withBean(ObjectMapper.class, ObjectMapper::new)
			.withBean(PostgresDocumentStore.class,
				() -> org.mockito.Mockito.mock(PostgresDocumentStore.class))
			.withPropertyValues("aidevos.persistence.type=postgresql");

		runner.run(context -> {
			assertInstanceOf(PostgresProjectRepository.class,
				context.getBean(ProjectRepository.class));
			assertInstanceOf(PostgresWorkspaceRepository.class,
				context.getBean(WorkspaceRepository.class));
			assertInstanceOf(PostgresTaskRepository.class,
				context.getBean(TaskRepository.class));
			assertInstanceOf(PostgresExecutionRecordRepository.class,
				context.getBean(ExecutionRecordRepository.class));
			assertInstanceOf(PostgresChangeRepository.class,
				context.getBean(ChangeRepository.class));
			assertInstanceOf(PostgresCommitRepository.class,
				context.getBean(CommitRepository.class));
			assertInstanceOf(PostgresCiRepository.class, context.getBean(CiRepository.class));
			assertInstanceOf(PostgresRepairRepository.class,
				context.getBean(RepairRepository.class));
			assertInstanceOf(PostgresFeedbackRepository.class,
				context.getBean(FeedbackRepository.class));
			assertInstanceOf(PostgresTraceRepository.class,
				context.getBean(TraceRepository.class));
			assertInstanceOf(PostgresUsageRepository.class,
				context.getBean(UsageRepository.class));
		});
	}

	@Configuration
	@Import({InMemoryProjectRepository.class, InMemoryWorkspaceRepository.class,
		InMemoryMemoryRepository.class, InMemoryTraceRepository.class,
		InMemoryUsageRepository.class,
		com.aidevos.orchestrator.taskcenter.InMemoryTaskRepository.class,
		com.aidevos.orchestrator.repair.InMemoryRepairRepository.class,
		com.aidevos.orchestrator.change.InMemoryChangeRepository.class,
		com.aidevos.orchestrator.commit.InMemoryCommitRepository.class,
		com.aidevos.orchestrator.ci.InMemoryCiRepository.class,
		com.aidevos.orchestrator.feedback.InMemoryFeedbackRepository.class})
	static class InMemoryRepositories {
	}

	@Configuration
	@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type",
		havingValue = "postgresql")
	@Import(PostgresRepositoryAutoConfiguration.class)
	static class PostgresRepositories {
	}
}
