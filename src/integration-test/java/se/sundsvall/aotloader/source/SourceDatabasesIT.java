package se.sundsvall.aotloader.source;

import org.junit.jupiter.api.BeforeAll;
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
			.hasValueSatisfying(access -> assertThat(access).matches("kan ändra innehållet i \\d+ tabeller eller vyer och ändra databasens struktur"));
	}

	private static JdbcTemplate jdbc(final String database, final String username, final String password) {
		return new JdbcTemplate(new DriverManagerDataSource(SourceDatabaseContainer.url(database), username, password));
	}
}
