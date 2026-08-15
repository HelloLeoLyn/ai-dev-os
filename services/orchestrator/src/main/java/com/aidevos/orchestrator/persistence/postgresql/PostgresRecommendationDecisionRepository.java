package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Timestamp;
import java.time.Instant;
import javax.sql.DataSource;
import com.aidevos.orchestrator.analysis.RecommendationDecision;
import com.aidevos.orchestrator.analysis.RecommendationDecisionRepository;
import com.aidevos.orchestrator.analysis.RecommendationStatus;
import com.aidevos.orchestrator.outbox.JdbcConnectionContext;

final class PostgresRecommendationDecisionRepository implements RecommendationDecisionRepository {
	private final DataSource dataSource;
	PostgresRecommendationDecisionRepository(DataSource dataSource) { this.dataSource=dataSource; }
	@Override public RecommendationDecision get(String id) { return readOne(false,id); }
	@Override public RecommendationDecision createIfAbsent(RecommendationDecision value) {
		Connection connection=JdbcConnectionContext.current(dataSource);
		try (PreparedStatement statement=connection.prepareStatement("INSERT INTO recommendation_decisions("
			+"recommendation_id,analysis_id,source_task_id,project_id,status,version,created_at,updated_at) "
			+"VALUES (?,?,?,?,?,?,?,?) ON CONFLICT(recommendation_id) DO NOTHING")) {
			statement.setString(1,value.recommendationId()); statement.setString(2,value.analysisId());
			statement.setString(3,value.sourceTaskId()); statement.setString(4,value.projectId());
			statement.setString(5,value.status().name()); statement.setLong(6,value.version());
			statement.setTimestamp(7,Timestamp.from(value.createdAt())); statement.setTimestamp(8,Timestamp.from(value.updatedAt()));
			statement.executeUpdate(); return readOne(false,value.recommendationId());
		} catch(Exception e){ throw failure(e); } finally { JdbcConnectionContext.release(connection,dataSource); }
	}
	@Override public RecommendationDecision lock(String id) { return readOne(true,id); }
	@Override public boolean saveIfVersion(RecommendationDecision value,long expected) {
		Connection connection=JdbcConnectionContext.current(dataSource);
		try (PreparedStatement statement=connection.prepareStatement("UPDATE recommendation_decisions SET status=?,"
			+"defer_until=?,defer_reason=?,ignore_reason=?,converted_backlog_item_id=?,version=?,updated_at=? "
			+"WHERE recommendation_id=? AND version=?")) {
			statement.setString(1,value.status().name()); statement.setTimestamp(2,timestamp(value.deferUntil()));
			statement.setString(3,value.deferReason()); statement.setString(4,value.ignoreReason());
			statement.setString(5,value.convertedBacklogItemId()); statement.setLong(6,value.version());
			statement.setTimestamp(7,Timestamp.from(value.updatedAt())); statement.setString(8,value.recommendationId());
			statement.setLong(9,expected); return statement.executeUpdate()==1;
		} catch(Exception e){ throw failure(e); } finally { JdbcConnectionContext.release(connection,dataSource); }
	}
	private RecommendationDecision readOne(boolean lock,String id) {
		Connection connection=JdbcConnectionContext.current(dataSource);
		try (PreparedStatement statement=connection.prepareStatement("SELECT * FROM recommendation_decisions "
			+"WHERE recommendation_id=?"+(lock?" FOR UPDATE":""))) {
			statement.setString(1,id); try(ResultSet r=statement.executeQuery()){ return r.next()?read(r):null; }
		} catch(Exception e){ throw failure(e); } finally { JdbcConnectionContext.release(connection,dataSource); }
	}
	private RecommendationDecision read(ResultSet r)throws Exception{return new RecommendationDecision(
		r.getString("recommendation_id"),r.getString("analysis_id"),r.getString("source_task_id"),
		r.getString("project_id"),RecommendationStatus.valueOf(r.getString("status")),
		instant(r,"defer_until"),r.getString("defer_reason"),r.getString("ignore_reason"),
		r.getString("converted_backlog_item_id"),r.getLong("version"),instant(r,"created_at"),instant(r,"updated_at"));}
	private Instant instant(ResultSet r,String name)throws Exception{Timestamp t=r.getTimestamp(name);return t==null?null:t.toInstant();}
	private Timestamp timestamp(java.time.Instant value){return value==null?null:Timestamp.from(value);}
	private IllegalStateException failure(Exception e){return new IllegalStateException("PostgreSQL recommendation decision repository failed",e);}
}
