package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.aidevos.orchestrator.repair.FailureContext;
import com.aidevos.orchestrator.repair.RepairRepository;
import com.aidevos.orchestrator.repair.RepairStatus;
import com.aidevos.orchestrator.repair.RepairTask;
import tools.jackson.databind.ObjectMapper;

/**
 * PostgreSQL implementation of the repair task repository backed by the
 * repair_tasks table (V19 migration). The failure context is stored as JSON.
 */
final class PostgresRepairRepository implements RepairRepository {

	private static final String COLUMNS = "repair_id,task_id,workspace_id,failure_context,"
		+ "status,retry_count,last_result,created_at,updated_at";

	private final PostgresJdbc jdbc;
	private final ObjectMapper mapper;

	PostgresRepairRepository(PostgresJdbc jdbc, ObjectMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	@Override
	public void save(RepairTask task) {
		jdbc.update("INSERT INTO repair_tasks(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(repair_id) DO UPDATE SET task_id=EXCLUDED.task_id,"
			+ "workspace_id=EXCLUDED.workspace_id,failure_context=EXCLUDED.failure_context,"
			+ "status=EXCLUDED.status,retry_count=EXCLUDED.retry_count,"
			+ "last_result=EXCLUDED.last_result,updated_at=EXCLUDED.updated_at",
			task.getRepairId(), task.getTaskId(), task.getWorkspaceId(),
			json(task.getFailureContext()), task.getStatus().name(), task.getRetryCount(),
			task.getLastResult(), PostgresJdbc.timestamp(task.getCreatedAt()),
			PostgresJdbc.timestamp(task.getUpdatedAt()));
	}

	@Override
	public RepairTask get(String repairId) {
		return jdbc.queryOne("SELECT " + COLUMNS + " FROM repair_tasks WHERE repair_id=?",
			this::read, repairId);
	}

	@Override
	public List<RepairTask> getByTaskId(String taskId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM repair_tasks WHERE task_id=?"
			+ " ORDER BY created_at,repair_id", this::read, taskId);
	}

	@Override
	public List<RepairTask> list() {
		return jdbc.query("SELECT " + COLUMNS + " FROM repair_tasks ORDER BY created_at,repair_id",
			this::read);
	}

	private String json(FailureContext context) {
		try {
			return mapper.writeValueAsString(context);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Failed to serialize failure context", exception);
		}
	}

	private RepairTask read(ResultSet result) throws SQLException {
		String json = result.getString("failure_context");
		FailureContext context;
		try {
			context = mapper.readValue(json, FailureContext.class);
		}
		catch (Exception exception) {
			throw new SQLException("Failed to read failure context", exception);
		}
		return RepairTask.restore(result.getString("repair_id"), result.getString("task_id"),
			result.getString("workspace_id"), context,
			RepairStatus.valueOf(result.getString("status")), result.getInt("retry_count"),
			result.getString("last_result"), PostgresJdbc.instant(result, "created_at"),
			PostgresJdbc.instant(result, "updated_at"));
	}
}
