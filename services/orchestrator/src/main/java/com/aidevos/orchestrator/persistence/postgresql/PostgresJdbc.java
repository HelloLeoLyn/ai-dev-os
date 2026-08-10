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

/**
 * Small JDBC helper used by the structured PostgreSQL repositories. Every
 * repository maps rows itself; no ORM or query framework is introduced.
 */
final class PostgresJdbc {

	private final DataSource dataSource;

	PostgresJdbc(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	void update(String sql, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, parameters);
			statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw failure(exception);
		}
	}

	boolean updateReturnsRow(String sql, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, parameters);
			return statement.executeUpdate() == 1;
		}
		catch (SQLException exception) {
			throw failure(exception);
		}
	}

	<T> List<T> query(String sql, RowReader<T> reader, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			bind(statement, parameters);
			List<T> values = new ArrayList<>();
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					values.add(reader.read(result));
				}
			}
			return values;
		}
		catch (SQLException exception) {
			throw failure(exception);
		}
	}

	<T> T queryOne(String sql, RowReader<T> reader, Object... parameters) {
		List<T> values = query(sql, reader, parameters);
		return values.isEmpty() ? null : values.getFirst();
	}

	@FunctionalInterface
	interface RowReader<T> {
		T read(ResultSet result) throws SQLException;
	}

	private static void bind(PreparedStatement statement, Object... parameters)
			throws SQLException {
		for (int index = 0; index < parameters.length; index++) {
			Object value = parameters[index];
			if (value == null) {
				statement.setObject(index + 1, null);
			}
			else if (value instanceof Instant instant) {
				statement.setTimestamp(index + 1, Timestamp.from(instant));
			}
			else {
				statement.setObject(index + 1, value);
			}
		}
	}

	static Timestamp timestamp(Instant instant) {
		return instant == null ? null : Timestamp.from(instant);
	}

	static Instant instant(ResultSet result, String column) throws SQLException {
		Timestamp timestamp = result.getTimestamp(column);
		return timestamp == null ? null : timestamp.toInstant();
	}

	private IllegalStateException failure(SQLException exception) {
		return new IllegalStateException("PostgreSQL repository failed", exception);
	}
}
