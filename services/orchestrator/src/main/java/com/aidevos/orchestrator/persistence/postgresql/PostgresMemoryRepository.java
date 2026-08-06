package com.aidevos.orchestrator.persistence.postgresql;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import com.aidevos.orchestrator.memory.MemoryRecord;
import com.aidevos.orchestrator.memory.MemoryRepository;
import com.aidevos.orchestrator.memory.MemoryType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of the memory repository backed by the structured
 * memory_records table (V8 migration).
 */
@Repository
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresMemoryRepository implements MemoryRepository {

	private static final String COLUMNS = "id,project_id,type,key,content,created_at,updated_at";

	private final DataSource dataSource;

	public PostgresMemoryRepository(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void save(MemoryRecord record) {
		String sql = "INSERT INTO memory_records(" + COLUMNS + ") VALUES (?,?,?,?,?,?,?) "
			+ "ON CONFLICT(id) DO UPDATE SET project_id=EXCLUDED.project_id,type=EXCLUDED.type,"
			+ "key=EXCLUDED.key,content=EXCLUDED.content,updated_at=EXCLUDED.updated_at";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, record.getId());
			statement.setString(2, record.getProjectId());
			statement.setString(3, record.getType().name());
			statement.setString(4, record.getKey());
			statement.setString(5, record.getContent());
			statement.setTimestamp(6, Timestamp.from(record.getCreatedAt()));
			statement.setTimestamp(7, Timestamp.from(record.getUpdatedAt()));
			statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw failure("save memory record", exception);
		}
	}

	@Override
	public MemoryRecord get(String id) {
		return selectOne("SELECT " + COLUMNS + " FROM memory_records WHERE id=?", id);
	}

	@Override
	public List<MemoryRecord> list(String projectId, MemoryType type) {
		StringBuilder sql = new StringBuilder("SELECT " + COLUMNS + " FROM memory_records WHERE 1=1");
		List<Object> parameters = new ArrayList<>();
		if (projectId != null) {
			sql.append(" AND project_id=?");
			parameters.add(projectId);
		}
		if (type != null) {
			sql.append(" AND type=?");
			parameters.add(type.name());
		}
		sql.append(" ORDER BY created_at,id");
		return select(sql.toString(), parameters.toArray());
	}

	@Override
	public boolean delete(String id) {
		String sql = "DELETE FROM memory_records WHERE id=?";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, id);
			return statement.executeUpdate() == 1;
		}
		catch (SQLException exception) {
			throw failure("delete memory record", exception);
		}
	}

	private MemoryRecord selectOne(String sql, Object... parameters) {
		List<MemoryRecord> records = select(sql, parameters);
		return records.isEmpty() ? null : records.getFirst();
	}

	private List<MemoryRecord> select(String sql, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < parameters.length; index++) {
				statement.setObject(index + 1, parameters[index]);
			}
			List<MemoryRecord> records = new ArrayList<>();
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					records.add(read(result));
				}
			}
			return records;
		}
		catch (SQLException exception) {
			throw failure("list memory records", exception);
		}
	}

	private MemoryRecord read(ResultSet result) throws SQLException {
		MemoryRecord record = new MemoryRecord();
		record.setId(result.getString("id"));
		record.setProjectId(result.getString("project_id"));
		record.setType(MemoryType.valueOf(result.getString("type")));
		record.setKey(result.getString("key"));
		record.setContent(result.getString("content"));
		record.setCreatedAt(result.getTimestamp("created_at").toInstant());
		record.setUpdatedAt(result.getTimestamp("updated_at").toInstant());
		return record;
	}

	private IllegalStateException failure(String operation, Exception cause) {
		return new IllegalStateException("PostgreSQL memory repository failed to " + operation, cause);
	}
}
