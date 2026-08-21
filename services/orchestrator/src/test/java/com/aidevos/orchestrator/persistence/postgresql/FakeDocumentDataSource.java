package com.aidevos.orchestrator.persistence.postgresql;

import java.io.PrintWriter;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.logging.Logger;
import javax.sql.DataSource;

/**
 * V1-PR-PERSISTENCE-CLOSEOUT 测试辅助：内存版 repository_documents 数据源。
 *
 * 用动态代理实现 DataSource/Connection/Statement/PreparedStatement/ResultSet，
 * 只支持 PostgresDocumentStore 实际使用的 SQL（put/get/all/allBySecondary +
 * schema_migrations 迁移簿记）。跨实例共享同一 FakeDocumentDataSource 即等价于
 * "同一数据库重启后重建 repository/service 实例"。
 */
public final class FakeDocumentDataSource implements DataSource {

	final Map<String, Map<String, String[]>> rows = new LinkedHashMap<>();
	final TreeSet<Integer> appliedVersions = new TreeSet<>();
	private int queryRow = -1;
	private List<String[]> queryRows = List.of();

	@Override
	public Connection getConnection() {
		return (Connection) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{Connection.class}, (proxy, method, args) -> {
				String name = method.getName();
				switch (name) {
					case "prepareStatement":
						return preparedStatement((String) args[0]);
					case "createStatement":
						return statement();
					case "setAutoCommit":
					case "commit":
					case "rollback":
					case "close":
						return null;
					case "getAutoCommit":
						return false;
					default:
						return defaultValue(method.getReturnType());
				}
			});
	}

	private Statement statement() {
		return (Statement) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{Statement.class}, (proxy, method, args) -> {
				String name = method.getName();
				switch (name) {
					case "execute":
					case "executeUpdate":
						return false;
					case "executeQuery":
						return emptyResultSet();
					case "close":
						return null;
					default:
						return defaultValue(method.getReturnType());
				}
			});
	}

	private PreparedStatement preparedStatement(String sql) {
		List<Object> params = new ArrayList<>();
		return (PreparedStatement) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{PreparedStatement.class}, (proxy, method, args) -> {
				String name = method.getName();
				switch (name) {
					case "setString":
					case "setInt":
					case "setLong":
					case "setObject":
						int idx = (Integer) args[0];
						while (params.size() < idx) {
							params.add(null);
						}
						params.set(idx - 1, args[1]);
						return null;
					case "executeUpdate":
						return executeUpdate(sql, params);
					case "executeQuery":
						return executeQuery(sql, params);
					case "execute":
						return false;
					case "close":
						return null;
					default:
						return defaultValue(method.getReturnType());
				}
			});
	}

	private int executeUpdate(String sql, List<Object> params) {
		if (sql.contains("INSERT INTO repository_documents")) {
			String type = str(params, 0);
			String id = str(params, 1);
			String payload = str(params, 2);
			String secondary = str(params, 3);
			rows.computeIfAbsent(type, k -> new LinkedHashMap<>()).put(id,
				new String[]{payload, secondary});
			return 1;
		}
		if (sql.contains("INSERT INTO schema_migrations")) {
			appliedVersions.add((Integer) params.get(0));
			return 1;
		}
		if (sql.contains("DELETE FROM repository_documents")) {
			String type = str(params, 0);
			String id = str(params, 1);
			if (rows.get(type) != null) {
				rows.get(type).remove(id);
			}
			return 1;
		}
		// migration 文件内的建表/插入：视为成功（无副作用）
		return 1;
	}

	private ResultSet executeQuery(String sql, List<Object> params) {
		List<String[]> result = new ArrayList<>();
		if (sql.contains("SELECT payload::text FROM repository_documents")) {
			String type = str(params, 0);
			Map<String, String[]> byType = rows.getOrDefault(type, Map.of());
			if (sql.contains("AND entity_id=?")) {
				String id = str(params, 1);
				String[] row = byType.get(id);
				if (row != null) {
					result.add(new String[]{row[0]});
				}
			}
			else if (sql.contains("AND secondary_key=?")) {
				String secondary = str(params, 1);
				for (String[] row : byType.values()) {
					if (secondary != null && secondary.equals(row[1])) {
						result.add(new String[]{row[0]});
					}
				}
			}
			else {
				for (String[] row : byType.values()) {
					result.add(new String[]{row[0]});
				}
			}
		}
		else if (sql.contains("SELECT version FROM schema_migrations")) {
			for (Integer version : appliedVersions) {
				result.add(new String[]{String.valueOf(version)});
			}
		}
		// SELECT 1 FROM schema_migrations 及其他：空（migrate 视为全部待应用，execute 被忽略）
		return resultSet(result);
	}

	private ResultSet resultSet(List<String[]> rowsToServe) {
		this.queryRows = rowsToServe;
		this.queryRow = -1;
		return (ResultSet) Proxy.newProxyInstance(getClass().getClassLoader(),
			new Class<?>[]{ResultSet.class}, (proxy, method, args) -> {
				String name = method.getName();
				switch (name) {
					case "next":
						queryRow++;
						return queryRow < queryRows.size();
					case "getString": {
						int col = args.length > 0 && args[0] instanceof Integer i ? i : 1;
						String[] row = queryRows.get(queryRow);
						return row[col - 1];
					}
					case "getInt": {
						int col = args.length > 0 && args[0] instanceof Integer i ? i : 1;
						return Integer.parseInt(queryRows.get(queryRow)[col - 1]);
					}
					case "close":
						return null;
					default:
						return defaultValue(method.getReturnType());
				}
			});
	}

	private ResultSet emptyResultSet() {
		return resultSet(List.of());
	}

	private static String str(List<Object> params, int idx) {
		if (idx >= params.size()) {
			return null;
		}
		Object value = params.get(idx);
		return value == null ? null : String.valueOf(value);
	}

	private static Object defaultValue(Class<?> type) {
		if (!type.isPrimitive()) {
			return null;
		}
		if (type == boolean.class) {
			return false;
		}
		if (type == int.class) {
			return 0;
		}
		if (type == long.class) {
			return 0L;
		}
		return null;
	}

	@Override public Connection getConnection(String username, String password) { return getConnection(); }
	@Override public PrintWriter getLogWriter() { return null; }
	@Override public void setLogWriter(PrintWriter out) { }
	@Override public void setLoginTimeout(int seconds) { }
	@Override public int getLoginTimeout() { return 0; }
	@Override public Logger getParentLogger() { return Logger.getLogger("fake"); }
	@Override public <T> T unwrap(Class<T> iface) throws SQLException { return null; }
	@Override public boolean isWrapperFor(Class<?> iface) { return false; }
}
