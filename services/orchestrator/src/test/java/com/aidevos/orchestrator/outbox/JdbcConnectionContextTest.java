package com.aidevos.orchestrator.outbox;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * JdbcConnectionContext contract: transaction-bound connections are reused,
 * standalone connections are caller-owned, and acquisition fails fast on an
 * interrupted thread so a shutting-down relay never opens new connections to a
 * database that is already gone (e.g. a stopped Testcontainers PostgreSQL).
 */
class JdbcConnectionContextTest {

	@Test
	void currentReturnsBoundConnectionWithoutTouchingDataSource() throws Exception {
		Connection bound = mock(Connection.class);
		DataSource dataSource = mock(DataSource.class);
		JdbcConnectionContext.bind(bound);
		try {
			assertSame(bound, JdbcConnectionContext.current(dataSource));
		}
		finally {
			JdbcConnectionContext.unbind();
		}
		verifyNoInteractions(dataSource);
	}

	@Test
	void currentOpensConnectionFromDataSourceWhenNoneBound() throws Exception {
		Connection connection = mock(Connection.class);
		DataSource dataSource = mock(DataSource.class);
		when(dataSource.getConnection()).thenReturn(connection);

		assertSame(connection, JdbcConnectionContext.current(dataSource));
		verify(dataSource).getConnection();
	}

	@Test
	void currentFailsFastWhenCallingThreadIsInterrupted() {
		DataSource dataSource = mock(DataSource.class);
		Thread.currentThread().interrupt();
		try {
			assertThrows(IllegalStateException.class,
				() -> JdbcConnectionContext.current(dataSource));
		}
		finally {
			Thread.interrupted();
		}
		verifyNoInteractions(dataSource);
	}

	@Test
	void releaseKeepsTransactionBoundConnectionOpen() throws Exception {
		Connection bound = mock(Connection.class);
		DataSource dataSource = mock(DataSource.class);
		JdbcConnectionContext.bind(bound);
		try {
			JdbcConnectionContext.release(bound, dataSource);
		}
		finally {
			JdbcConnectionContext.unbind();
		}
		verify(bound, never()).close();
	}

	@Test
	void releaseClosesStandaloneConnection() throws Exception {
		Connection standalone = mock(Connection.class);
		DataSource dataSource = mock(DataSource.class);
		JdbcConnectionContext.release(standalone, dataSource);
		verify(standalone).close();
	}

	@Test
	void rollbackQuietlyIgnoresFailures() throws Exception {
		Connection connection = mock(Connection.class);
		doThrow(new SQLException("rollback failed")).when(connection).rollback();
		assertDoesNotThrow(() -> JdbcConnectionContext.rollbackQuietly(connection));
	}
}
