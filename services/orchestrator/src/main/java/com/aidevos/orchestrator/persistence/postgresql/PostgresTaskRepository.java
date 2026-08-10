package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.aidevos.orchestrator.persistence.postgresql.PostgresJdbc.RowReader;
import com.aidevos.orchestrator.taskcenter.TaskRecord;
import com.aidevos.orchestrator.taskcenter.TaskRepository;
import com.aidevos.orchestrator.taskcenter.TaskStatus;

/**
 * PostgreSQL implementation of the task center task repository backed by the
 * tasks table (V14 migration).
 */
final class PostgresTaskRepository implements TaskRepository {

	private static final String COLUMNS = "task_id,name,description,project_id,workspace_id,"
		+ "status,approval_id,plan_run_id,error_message,created_at,updated_at";

	private final PostgresJdbc jdbc;

	PostgresTaskRepository(PostgresJdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(TaskRecord task) {
		jdbc.update("INSERT INTO tasks(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(task_id) DO UPDATE SET name=EXCLUDED.name,"
			+ "description=EXCLUDED.description,project_id=EXCLUDED.project_id,"
			+ "workspace_id=EXCLUDED.workspace_id,status=EXCLUDED.status,"
			+ "approval_id=EXCLUDED.approval_id,plan_run_id=EXCLUDED.plan_run_id,"
			+ "error_message=EXCLUDED.error_message,updated_at=EXCLUDED.updated_at",
			task.getTaskId(), task.getName(), task.getDescription(), task.getProjectId(),
			task.getWorkspaceId(), task.getStatus().name(), task.getApprovalId(),
			task.getPlanRunId(), task.getErrorMessage(),
			PostgresJdbc.timestamp(task.getCreatedAt()),
			PostgresJdbc.timestamp(task.getUpdatedAt()));
	}

	@Override
	public TaskRecord get(String taskId) {
		return jdbc.queryOne("SELECT " + COLUMNS + " FROM tasks WHERE task_id=?",
			PostgresTaskRepository::read, taskId);
	}

	@Override
	public List<TaskRecord> list() {
		return jdbc.query("SELECT " + COLUMNS + " FROM tasks ORDER BY created_at DESC,task_id",
			PostgresTaskRepository::read);
	}

	@Override
	public List<TaskRecord> listByProject(String projectId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM tasks WHERE project_id=?"
			+ " ORDER BY created_at DESC,task_id", PostgresTaskRepository::read, projectId);
	}

	private static TaskRecord read(ResultSet result) throws SQLException {
		return TaskRecord.restore(result.getString("task_id"), result.getString("name"),
			result.getString("description"), result.getString("project_id"),
			result.getString("workspace_id"), TaskStatus.valueOf(result.getString("status")),
			PostgresJdbc.instant(result, "created_at"),
			PostgresJdbc.instant(result, "updated_at"), result.getString("approval_id"),
			result.getString("plan_run_id"), result.getString("error_message"));
	}
}
