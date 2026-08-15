package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import com.aidevos.orchestrator.analysis.AnalysisEnums;
import com.aidevos.orchestrator.analysis.AnalysisInsightRepository;
import com.aidevos.orchestrator.analysis.AnalysisInsightSet;
import tools.jackson.databind.ObjectMapper;

final class PostgresAnalysisInsightRepository implements AnalysisInsightRepository {
	private final PostgresJdbc jdbc;
	private final ObjectMapper mapper;
	PostgresAnalysisInsightRepository(PostgresJdbc jdbc, ObjectMapper mapper) {
		this.jdbc = jdbc; this.mapper = mapper;
	}
	@Override public AnalysisInsightSet save(AnalysisInsightSet value) {
		jdbc.update("INSERT INTO analysis_insight_sets(analysis_id,source_task_id,source_execution_record_id,"
			+ "project_id,workspace_id,schema_version,extractor_type,extractor_version,extraction_status,"
			+ "content_fingerprint,payload,error_code,error_message,created_at,updated_at) "
			+ "VALUES (?,?,?,?,?,?,?,?,?,?,?::jsonb,?,?,?,?) ON CONFLICT(analysis_id) DO UPDATE SET "
			+ "extraction_status=EXCLUDED.extraction_status,content_fingerprint=EXCLUDED.content_fingerprint,"
			+ "payload=EXCLUDED.payload,error_code=EXCLUDED.error_code,error_message=EXCLUDED.error_message,"
			+ "updated_at=EXCLUDED.updated_at", value.analysisId(), value.sourceTaskId(),
			value.sourceExecutionRecordId(), value.projectId(), value.workspaceId(), value.schemaVersion(),
			value.extractorType().name(), value.extractorVersion(), value.status().name(),
			value.contentFingerprint(), json(value), value.errorCode(), value.errorMessage(),
			PostgresJdbc.timestamp(value.createdAt()), PostgresJdbc.timestamp(value.updatedAt()));
		return findBySource(value.sourceTaskId(), value.sourceExecutionRecordId(), value.extractorVersion());
	}
	@Override public AnalysisInsightSet get(String id) { return one("analysis_id=?", id); }
	@Override public AnalysisInsightSet findByTaskId(String id) {
		List<AnalysisInsightSet> values = query("source_task_id=? ORDER BY updated_at DESC", id);
		return values.isEmpty() ? null : values.getFirst();
	}
	@Override public AnalysisInsightSet findBySource(String task, String execution, String version) {
		return one("source_task_id=? AND source_execution_record_id=? AND extractor_version=?",
			task, execution, version);
	}
	@Override public List<AnalysisInsightSet> findByProjectId(String id) {
		return query("project_id=? ORDER BY created_at DESC", id);
	}
	@Override public List<AnalysisInsightSet> findByStatus(AnalysisEnums.Status status) {
		return query("extraction_status=? ORDER BY updated_at", status.name());
	}
	private AnalysisInsightSet one(String where, Object... args) {
		List<AnalysisInsightSet> values = query(where, args); return values.isEmpty() ? null : values.getFirst();
	}
	private List<AnalysisInsightSet> query(String where, Object... args) {
		return jdbc.query("SELECT payload FROM analysis_insight_sets WHERE " + where, this::read, args);
	}
	private AnalysisInsightSet read(ResultSet result) throws SQLException {
		try { return mapper.readValue(result.getString("payload"), AnalysisInsightSet.class); }
		catch (Exception exception) { throw new SQLException("Invalid analysis payload", exception); }
	}
	private String json(Object value) {
		try { return mapper.writeValueAsString(value); }
		catch (Exception exception) { throw new IllegalStateException("Cannot serialize analysis insight", exception); }
	}
}
