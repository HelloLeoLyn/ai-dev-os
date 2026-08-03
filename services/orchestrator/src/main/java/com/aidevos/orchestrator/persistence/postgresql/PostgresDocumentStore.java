package com.aidevos.orchestrator.persistence.postgresql;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;
import tools.jackson.databind.ObjectMapper;

@Component
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresDocumentStore {
	private final DataSource dataSource;
	private final ObjectMapper mapper;

	public PostgresDocumentStore(DataSource dataSource, ObjectMapper mapper) {
		this.dataSource=dataSource; this.mapper=mapper; migrate();
	}

	public void put(String type, String id, Object payload, String secondaryKey) {
		String sql = "INSERT INTO repository_documents(repository_type,entity_id,payload,secondary_key) "
			+ "VALUES (?,?,?::jsonb,?) ON CONFLICT(repository_type,entity_id) DO UPDATE SET "
			+ "payload=EXCLUDED.payload,secondary_key=EXCLUDED.secondary_key,updated_at=CURRENT_TIMESTAMP";
		try (Connection connection=dataSource.getConnection(); PreparedStatement statement=connection.prepareStatement(sql)) {
			statement.setString(1,type); statement.setString(2,id);
			statement.setString(3,mapper.writeValueAsString(payload)); statement.setString(4,secondaryKey);
			statement.executeUpdate();
		}
		catch (Exception exception) { throw failure("save", type, exception); }
	}

	public <T> T get(String type, String id, Class<T> valueType) {
		String sql="SELECT payload::text FROM repository_documents WHERE repository_type=? AND entity_id=?";
		try (Connection connection=dataSource.getConnection(); PreparedStatement statement=connection.prepareStatement(sql)) {
			statement.setString(1,type); statement.setString(2,id);
			try (ResultSet result=statement.executeQuery()) {
				return result.next() ? mapper.readValue(result.getString(1), valueType) : null;
			}
		}
		catch (Exception exception) { throw failure("read", type, exception); }
	}

	public <T> List<T> all(String type, Class<T> valueType) {
		String sql="SELECT payload::text FROM repository_documents WHERE repository_type=? ORDER BY created_at,entity_id";
		try (Connection connection=dataSource.getConnection(); PreparedStatement statement=connection.prepareStatement(sql)) {
			statement.setString(1,type); List<T> values=new ArrayList<>();
			try (ResultSet result=statement.executeQuery()) {
				while(result.next()) values.add(mapper.readValue(result.getString(1),valueType));
			}
			return values;
		}
		catch (Exception exception) { throw failure("list", type, exception); }
	}

	public void delete(String type,String id) {
		try(Connection connection=dataSource.getConnection(); PreparedStatement statement=connection.prepareStatement(
				"DELETE FROM repository_documents WHERE repository_type=? AND entity_id=?")) {
			statement.setString(1,type); statement.setString(2,id); statement.executeUpdate();
		}
		catch(SQLException exception) { throw failure("delete",type,exception); }
	}

	private void migrate() {
		try (var input=getClass().getResourceAsStream("/db/migration/V1__repository_documents.sql")) {
			if(input==null) throw new IllegalStateException("Persistence migration is missing");
			String sql=new String(input.readAllBytes(), StandardCharsets.UTF_8);
			try(Connection connection=dataSource.getConnection(); var statement=connection.createStatement()) {
				for(String command:sql.split(";")) if(!command.isBlank()) statement.execute(command);
			}
		}
		catch(IOException|SQLException exception) { throw failure("migrate","schema",exception); }
	}

	private IllegalStateException failure(String operation,String type,Exception cause) {
		return new IllegalStateException("PostgreSQL repository failed to "+operation+" "+type,cause);
	}
}
