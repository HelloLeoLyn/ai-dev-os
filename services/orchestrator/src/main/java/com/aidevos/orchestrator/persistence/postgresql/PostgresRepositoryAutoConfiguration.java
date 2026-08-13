package com.aidevos.orchestrator.persistence.postgresql;

import javax.sql.DataSource;

import com.aidevos.orchestrator.change.ChangeRepository;
import com.aidevos.orchestrator.ci.CiRepository;
import com.aidevos.orchestrator.commit.CommitRepository;
import com.aidevos.orchestrator.execution.ExecutionRecordRepository;
import com.aidevos.orchestrator.feedback.FeedbackRepository;
import com.aidevos.orchestrator.observability.TraceRepository;
import com.aidevos.orchestrator.observability.usage.UsageRepository;
import com.aidevos.orchestrator.project.ProjectRepository;
import com.aidevos.orchestrator.repair.RepairRepository;
import com.aidevos.orchestrator.taskcenter.TaskRepository;
import com.aidevos.orchestrator.workspace.WorkspaceRepository;
import com.aidevos.orchestrator.validation.ValidationArtifactRepository;
import com.aidevos.orchestrator.validation.ValidationRepository;
import com.aidevos.orchestrator.validation.security.SecurityReportRepository;
import com.aidevos.orchestrator.qualitygate.QualityGateRepository;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import tools.jackson.databind.ObjectMapper;

/**
 * Persistence mode switch for the domain repositories. When
 * {@code aidevos.persistence.type=postgresql} the JDBC-backed repositories are
 * registered; otherwise (in-memory, the default) the InMemory implementations
 * are active. Memory, audit, plan-run and approval repositories keep their own
 * conditional PostgreSQL implementations.
 */
@Configuration
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresRepositoryAutoConfiguration {

	@Bean
	PostgresJdbc postgresJdbc(DataSource dataSource) {
		return new PostgresJdbc(dataSource);
	}

	@Bean
	@Primary
	ProjectRepository projectRepository(PostgresJdbc jdbc) {
		return new PostgresProjectRepository(jdbc);
	}

	@Bean
	WorkspaceRepository workspaceRepository(PostgresJdbc jdbc) {
		return new PostgresWorkspaceRepository(jdbc);
	}

	@Bean
	TaskRepository taskRepository(PostgresJdbc jdbc) {
		return new PostgresTaskRepository(jdbc);
	}

	@Bean
	@Primary
	ExecutionRecordRepository executionRecordRepository(PostgresJdbc jdbc, ObjectMapper mapper) {
		return new PostgresExecutionRecordRepository(jdbc, mapper);
	}

	@Bean
	ChangeRepository changeRepository(PostgresJdbc jdbc) {
		return new PostgresChangeRepository(jdbc);
	}

	@Bean
	CommitRepository commitRepository(PostgresJdbc jdbc) {
		return new PostgresCommitRepository(jdbc);
	}

	@Bean
	CiRepository ciRepository(PostgresJdbc jdbc) {
		return new PostgresCiRepository(jdbc);
	}

	@Bean
	RepairRepository repairRepository(PostgresJdbc jdbc, ObjectMapper mapper) {
		return new PostgresRepairRepository(jdbc, mapper);
	}

	@Bean
	FeedbackRepository feedbackRepository(PostgresJdbc jdbc) {
		return new PostgresFeedbackRepository(jdbc);
	}

	@Bean
	TraceRepository traceRepository(PostgresJdbc jdbc) {
		return new PostgresTraceRepository(jdbc);
	}

	@Bean
	UsageRepository usageRepository(PostgresJdbc jdbc) {
		return new PostgresUsageRepository(jdbc);
	}

	@Bean
	ValidationRepository validationRepository(DataSource dataSource, ObjectMapper mapper) {
		return new PostgresValidationRepository(dataSource, mapper);
	}

	@Bean
	ValidationArtifactRepository validationArtifactRepository(DataSource dataSource, ObjectMapper mapper) {
		return new PostgresValidationArtifactRepository(dataSource, mapper);
	}

	@Bean
	SecurityReportRepository securityReportRepository(DataSource dataSource,ObjectMapper mapper){
		return new PostgresSecurityReportRepository(dataSource,mapper);
	}

	@Bean QualityGateRepository qualityGateRepository(DataSource dataSource,ObjectMapper mapper){
		return new PostgresQualityGateRepository(dataSource,mapper);
	}
}
