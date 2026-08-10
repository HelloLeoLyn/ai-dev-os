package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;

import com.aidevos.orchestrator.execution.ExecutionArtifact;
import com.aidevos.orchestrator.execution.ExecutionRecordRepository;
import com.aidevos.orchestrator.execution.ExecutionReport;
import com.aidevos.orchestrator.model.ExecutionRecord;
import tools.jackson.databind.ObjectMapper;

/**
 * PostgreSQL implementation of the execution record repository backed by the
 * execution_records table (V15 migration). The report and artifact collections
 * are stored as JSON text columns so no extra tables are needed.
 */
final class PostgresExecutionRecordRepository implements ExecutionRecordRepository {

	private static final String COLUMNS = "id,task_id,agent_name,status,message,output,report,"
		+ "artifacts,execution_id,job_id,plan_run_id,step_run_id,attempt_id,workspace,sandbox,"
		+ "approval_id,branch,before_head,after_head,exit_code,codex_thread_id,git_status,"
		+ "git_diff_stat,started_at,completed_at";

	private final PostgresJdbc jdbc;
	private final ObjectMapper mapper;

	PostgresExecutionRecordRepository(PostgresJdbc jdbc, ObjectMapper mapper) {
		this.jdbc = jdbc;
		this.mapper = mapper;
	}

	@Override
	public void save(ExecutionRecord record) {
		jdbc.update("INSERT INTO execution_records(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?,?,?,?,"
			+ "?,?,?,?,?,?,?,?,?,?,?,?,?,?,?) "
			+ "ON CONFLICT(id) DO UPDATE SET task_id=EXCLUDED.task_id,"
			+ "agent_name=EXCLUDED.agent_name,status=EXCLUDED.status,message=EXCLUDED.message,"
			+ "output=EXCLUDED.output,report=EXCLUDED.report,artifacts=EXCLUDED.artifacts,"
			+ "execution_id=EXCLUDED.execution_id,job_id=EXCLUDED.job_id,"
			+ "plan_run_id=EXCLUDED.plan_run_id,step_run_id=EXCLUDED.step_run_id,"
			+ "attempt_id=EXCLUDED.attempt_id,workspace=EXCLUDED.workspace,"
			+ "sandbox=EXCLUDED.sandbox,approval_id=EXCLUDED.approval_id,"
			+ "branch=EXCLUDED.branch,before_head=EXCLUDED.before_head,"
			+ "after_head=EXCLUDED.after_head,exit_code=EXCLUDED.exit_code,"
			+ "codex_thread_id=EXCLUDED.codex_thread_id,git_status=EXCLUDED.git_status,"
			+ "git_diff_stat=EXCLUDED.git_diff_stat,started_at=EXCLUDED.started_at,"
			+ "completed_at=EXCLUDED.completed_at",
			record.getId(), record.getTaskId(), record.getAgentName(), record.getStatus(),
			record.getMessage(), record.getOutput(), json(record.getReport()),
			json(record.getArtifacts()), record.getExecutionId(), record.getJobId(),
			record.getPlanRunId(), record.getStepRunId(), record.getAttemptId(),
			record.getWorkspace(), record.getSandbox(), record.getApprovalId(),
			record.getBranch(), record.getBeforeHead(), record.getAfterHead(),
			record.getExitCode(), record.getCodexThreadId(), record.getGitStatus(),
			record.getGitDiffStat(), PostgresJdbc.timestamp(record.getStartedAt()),
			PostgresJdbc.timestamp(record.getCompletedAt()));
	}

	@Override
	public ExecutionRecord get(String id) {
		return jdbc.queryOne("SELECT " + COLUMNS + " FROM execution_records WHERE id=?",
			this::read, id);
	}

	@Override
	public List<ExecutionRecord> getAll() {
		return jdbc.query("SELECT " + COLUMNS + " FROM execution_records ORDER BY started_at,id",
			this::read);
	}

	@Override
	public void remove(String id) {
		jdbc.update("DELETE FROM execution_records WHERE id=?", id);
	}

	private String json(Object value) {
		if (value == null) {
			return null;
		}
		try {
			return mapper.writeValueAsString(value);
		}
		catch (Exception exception) {
			throw new IllegalStateException("Failed to serialize execution record field", exception);
		}
	}

	private ExecutionRecord read(ResultSet result) throws SQLException {
		ExecutionRecord record = new ExecutionRecord();
		record.setId(result.getString("id"));
		record.setTaskId(result.getString("task_id"));
		record.setAgentName(result.getString("agent_name"));
		record.setStatus(result.getString("status"));
		record.setMessage(result.getString("message"));
		record.setOutput(result.getString("output"));
		record.setExecutionId(result.getString("execution_id"));
		record.setJobId(result.getString("job_id"));
		record.setPlanRunId(result.getString("plan_run_id"));
		record.setStepRunId(result.getString("step_run_id"));
		record.setAttemptId(result.getString("attempt_id"));
		record.setWorkspace(result.getString("workspace"));
		record.setSandbox(result.getString("sandbox"));
		record.setApprovalId(result.getString("approval_id"));
		record.setBranch(result.getString("branch"));
		record.setBeforeHead(result.getString("before_head"));
		record.setAfterHead(result.getString("after_head"));
		Integer exitCode = result.getInt("exit_code");
		record.setExitCode(result.wasNull() ? null : exitCode);
		record.setCodexThreadId(result.getString("codex_thread_id"));
		record.setGitStatus(result.getString("git_status"));
		record.setGitDiffStat(result.getString("git_diff_stat"));
		record.setStartedAt(PostgresJdbc.instant(result, "started_at"));
		record.setCompletedAt(PostgresJdbc.instant(result, "completed_at"));
		record.setReport(fromJson(result.getString("report"), ExecutionReport.class));
		record.setArtifacts(fromJsonList(result.getString("artifacts")));
		return record;
	}

	private ExecutionReport fromJson(String json, Class<ExecutionReport> type) {
		if (json == null || json.isBlank()) {
			return null;
		}
		try {
			return mapper.readValue(json, type);
		}
		catch (Exception exception) {
			return null;
		}
	}

	private List<ExecutionArtifact> fromJsonList(String json) {
		if (json == null || json.isBlank()) {
			return new java.util.ArrayList<>();
		}
		try {
			return mapper.readValue(json,
				mapper.getTypeFactory().constructCollectionType(List.class,
					ExecutionArtifact.class));
		}
		catch (Exception exception) {
			return new java.util.ArrayList<>();
		}
	}
}
