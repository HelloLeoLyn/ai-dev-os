package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.aidevos.orchestrator.observability.usage.UsageRecord;
import com.aidevos.orchestrator.observability.usage.UsageRepository;

/**
 * PostgreSQL implementation of the usage record repository backed by the
 * usage_records table (V22 migration).
 */
final class PostgresUsageRepository implements UsageRepository {

	private static final String COLUMNS = "usage_id,task_id,project_id,agent_type,model,"
		+ "input_tokens,output_tokens,total_tokens,estimated_cost,created_at";

	private final PostgresJdbc jdbc;

	PostgresUsageRepository(PostgresJdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(UsageRecord record) {
		jdbc.update("INSERT INTO usage_records(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?)",
			record.usageId(), record.taskId(), record.projectId(), record.agentType(),
			record.model(), record.inputTokens(), record.outputTokens(),
			record.totalTokens(), record.estimatedCost(),
			PostgresJdbc.timestamp(record.createdAt()));
	}

	@Override
	public List<UsageRecord> list() {
		return jdbc.query("SELECT " + COLUMNS + " FROM usage_records ORDER BY created_at,usage_id",
			PostgresUsageRepository::read);
	}

	private static UsageRecord read(ResultSet result) throws SQLException {
		return new UsageRecord(result.getString("usage_id"), result.getString("task_id"),
			result.getString("project_id"), result.getString("agent_type"),
			result.getString("model"), result.getLong("input_tokens"),
			result.getLong("output_tokens"), result.getLong("total_tokens"),
			result.getDouble("estimated_cost"), PostgresJdbc.instant(result, "created_at"));
	}
}
