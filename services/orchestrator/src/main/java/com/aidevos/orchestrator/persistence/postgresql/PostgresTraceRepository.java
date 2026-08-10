package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.aidevos.orchestrator.observability.TraceRecord;
import com.aidevos.orchestrator.observability.TraceRepository;
import com.aidevos.orchestrator.observability.TraceStatus;

/**
 * PostgreSQL implementation of the observability trace repository backed by
 * the traces table (V21 migration).
 */
final class PostgresTraceRepository implements TraceRepository {

	private static final String COLUMNS = "trace_id,task_id,project_id,graph_id,node_id,"
		+ "agent_type,tool_id,status,start_time,end_time,duration,error_message";

	private final PostgresJdbc jdbc;

	PostgresTraceRepository(PostgresJdbc jdbc) {
		this.jdbc = jdbc;
	}

	@Override
	public void save(TraceRecord trace) {
		jdbc.update("INSERT INTO traces(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(trace_id) DO UPDATE SET task_id=EXCLUDED.task_id,"
			+ "project_id=EXCLUDED.project_id,graph_id=EXCLUDED.graph_id,"
			+ "node_id=EXCLUDED.node_id,agent_type=EXCLUDED.agent_type,"
			+ "tool_id=EXCLUDED.tool_id,status=EXCLUDED.status,end_time=EXCLUDED.end_time,"
			+ "duration=EXCLUDED.duration,error_message=EXCLUDED.error_message",
			trace.getTraceId(), trace.getTaskId(), trace.getProjectId(), trace.getGraphId(),
			trace.getNodeId(), trace.getAgentType(), trace.getToolId(),
			trace.getStatus().name(), PostgresJdbc.timestamp(trace.getStartTime()),
			PostgresJdbc.timestamp(trace.getEndTime()), trace.getDuration(),
			trace.getErrorMessage());
	}

	@Override
	public TraceRecord get(String traceId) {
		return jdbc.queryOne("SELECT " + COLUMNS + " FROM traces WHERE trace_id=?",
			PostgresTraceRepository::read, traceId);
	}

	@Override
	public List<TraceRecord> listByTask(String taskId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM traces WHERE task_id=?"
			+ " ORDER BY start_time DESC,trace_id", PostgresTraceRepository::read, taskId);
	}

	@Override
	public List<TraceRecord> listByProject(String projectId) {
		return jdbc.query("SELECT " + COLUMNS + " FROM traces WHERE project_id=?"
			+ " ORDER BY start_time DESC,trace_id", PostgresTraceRepository::read, projectId);
	}

	@Override
	public List<TraceRecord> listByAgent(String agentType) {
		return jdbc.query("SELECT " + COLUMNS + " FROM traces WHERE agent_type=?"
			+ " ORDER BY start_time DESC,trace_id", PostgresTraceRepository::read, agentType);
	}

	private static TraceRecord read(ResultSet result) throws SQLException {
		return TraceRecord.restore(result.getString("trace_id"), result.getString("task_id"),
			result.getString("project_id"), result.getString("graph_id"),
			result.getString("node_id"), result.getString("agent_type"),
			result.getString("tool_id"), TraceStatus.valueOf(result.getString("status")),
			PostgresJdbc.instant(result, "start_time"),
			PostgresJdbc.instant(result, "end_time"), result.getLong("duration"),
			result.getString("error_message"));
	}
}
