package se.sundsvall.aotloader.source;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import se.sundsvall.aotloader.apptest.SourceDatabaseContainer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Runs the check of what the account of a source may change against SQL Server itself.
 */
class SourceDatabasesIT {

	@BeforeAll
	static void startDatabases() {
		SourceDatabaseContainer.start();
	}

	@ParameterizedTest
	@ValueSource(strings = {
		SourceDatabaseContainer.ALKT_DATABASE, SourceDatabaseContainer.ALKOL_DATABASE
	})
	void anAccountThatMayOnlyReadPassesAndTheAdministratorDoesNot(final String database) {
		assertThat(SourceDatabases.writeAccess(jdbc(database, SourceDatabaseContainer.username(), SourceDatabaseContainer.password()))).isEmpty();
		assertThat(SourceDatabases.writeAccess(jdbc(database, SourceDatabaseContainer.adminUsername(), SourceDatabaseContainer.adminPassword())))
			.hasValueSatisfying(access -> assertThat(access).matches("kan ändra innehållet i \\d+ tabeller eller vyer (och köra en lagrad procedur )?och ändra databasens struktur"));
	}

	@Test
	void theReadsRunInASnapshotWhenTheDatabaseAllowsIt() throws SQLException {
		// 5 is snapshot isolation in sys.dm_exec_sessions, 2 read committed
		final var isolation = "SELECT transaction_isolation_level FROM sys.dm_exec_sessions WHERE session_id = @@SPID";
		try (var alkol = DriverManager.getConnection(SourceDatabaseContainer.url(SourceDatabaseContainer.ALKOL_DATABASE), SourceDatabaseContainer.username(), SourceDatabaseContainer.password());
			var alkt = DriverManager.getConnection(SourceDatabaseContainer.url(SourceDatabaseContainer.ALKT_DATABASE), SourceDatabaseContainer.username(), SourceDatabaseContainer.password())) {

			assertThat(SourceDatabases.<Integer>readOn(alkol, 5, jdbc -> jdbc.queryForObject(isolation, Integer.class))).isEqualTo(5);
			assertThat(SourceDatabases.<Integer>readOn(alkt, 5, jdbc -> jdbc.queryForObject(isolation, Integer.class))).isEqualTo(2);
			// The connection is as it was, for the pool to give out again
			assertThat(alkol.getAutoCommit()).isTrue();
			assertThat(alkol.getTransactionIsolation()).isEqualTo(Connection.TRANSACTION_READ_COMMITTED);
		}
	}

	@Test
	void anAccountThatMayRunAProcedureMayChangeTheDatabase() {
		assertThat(SourceDatabases.writeAccess(jdbc(SourceDatabaseContainer.ALKOL_DATABASE, SourceDatabaseContainer.adminUsername(), SourceDatabaseContainer.adminPassword())))
			.hasValueSatisfying(access -> assertThat(access).contains("köra en lagrad procedur"));
	}

	private static JdbcTemplate jdbc(final String database, final String username, final String password) {
		return new JdbcTemplate(new DriverManagerDataSource(SourceDatabaseContainer.url(database), username, password));
	}
}
