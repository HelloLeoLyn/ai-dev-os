package com.aidevos.orchestrator.skill;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.DependsOn;
import org.springframework.stereotype.Repository;

/**
 * PostgreSQL implementation of the skill repository backed by the structured
 * skills table (V10 migration).
 */
@Repository
@DependsOn("postgresDocumentStore")
@ConditionalOnProperty(prefix = "aidevos.persistence", name = "type", havingValue = "postgresql")
public class PostgresSkillRepository implements SkillRepository {

	private static final String COLUMNS = "skill_id,name,version,enabled,created_at,updated_at";

	private final DataSource dataSource;

	public PostgresSkillRepository(DataSource dataSource) {
		this.dataSource = dataSource;
	}

	@Override
	public void save(Skill skill) {
		String sql = "INSERT INTO skills(" + COLUMNS + ") VALUES (?,?,?,?,?,?) "
			+ "ON CONFLICT(skill_id) DO UPDATE SET name=EXCLUDED.name,"
			+ "version=EXCLUDED.version,enabled=EXCLUDED.enabled,"
			+ "updated_at=EXCLUDED.updated_at";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, skill.getSkillId());
			statement.setString(2, skill.getName());
			statement.setString(3, skill.getVersion());
			statement.setBoolean(4, skill.isEnabled());
			statement.setTimestamp(5, Timestamp.from(skill.getCreatedAt()));
			statement.setTimestamp(6, Timestamp.from(skill.getUpdatedAt()));
			statement.executeUpdate();
		}
		catch (SQLException exception) {
			throw failure("save skill", exception);
		}
	}

	@Override
	public Skill get(String skillId) {
		return selectOne("SELECT " + COLUMNS + " FROM skills WHERE skill_id=?", skillId);
	}

	@Override
	public List<Skill> list() {
		return select("SELECT " + COLUMNS + " FROM skills ORDER BY created_at,skill_id");
	}

	@Override
	public boolean delete(String skillId) {
		String sql = "DELETE FROM skills WHERE skill_id=?";
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			statement.setString(1, skillId);
			return statement.executeUpdate() == 1;
		}
		catch (SQLException exception) {
			throw failure("delete skill", exception);
		}
	}

	private Skill selectOne(String sql, Object... parameters) {
		List<Skill> skills = select(sql, parameters);
		return skills.isEmpty() ? null : skills.getFirst();
	}

	private List<Skill> select(String sql, Object... parameters) {
		try (Connection connection = dataSource.getConnection();
				PreparedStatement statement = connection.prepareStatement(sql)) {
			for (int index = 0; index < parameters.length; index++) {
				statement.setObject(index + 1, parameters[index]);
			}
			List<Skill> skills = new ArrayList<>();
			try (ResultSet result = statement.executeQuery()) {
				while (result.next()) {
					skills.add(read(result));
				}
			}
			return skills;
		}
		catch (SQLException exception) {
			throw failure("list skills", exception);
		}
	}

	private Skill read(ResultSet result) throws SQLException {
		SkillType type = SkillType.ANALYSIS;
		return new Skill(result.getString("skill_id"), result.getString("name"),
			null, type, result.getString("version"), result.getBoolean("enabled"),
			List.of(), null, result.getTimestamp("created_at").toInstant(),
			result.getTimestamp("updated_at").toInstant());
	}

	private IllegalStateException failure(String operation, Exception cause) {
		return new IllegalStateException("PostgreSQL skill repository failed to " + operation, cause);
	}
}
