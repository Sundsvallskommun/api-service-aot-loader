package se.sundsvall.aotloader.apptest;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Arrays;
import org.testcontainers.mssqlserver.MSSQLServerContainer;
import org.testcontainers.utility.DockerImageName;

/**
 * A SQL Server with the two source databases, their tables and made up rows, at the compatibility levels of the real
 * ones: below 110 for AlkT and 110 for AlkOL. The tests log in with SQL Server authentication, since the
 * container has no Windows authentication; only the connection string differs from a real run.
 */
final class SourceDatabaseContainer {

	static final String ALKT_DATABASE = "AlkTSundsvall";
	static final String ALKOL_DATABASE = "AlkOL2Sundsvall";

	private static final MSSQLServerContainer CONTAINER = new MSSQLServerContainer(DockerImageName.parse("mcr.microsoft.com/mssql/server:2019-CU28-ubuntu-20.04")).acceptLicense();

	private static boolean created;

	private SourceDatabaseContainer() {}

	/**
	 * Starts the container and creates the databases, once for all the tests.
	 */
	static synchronized void start() {
		if (created) {
			return;
		}
		CONTAINER.start();
		try {
			execute("master", "CREATE DATABASE " + ALKT_DATABASE + " COLLATE Finnish_Swedish_CI_AS");
			execute("master", "ALTER DATABASE " + ALKT_DATABASE + " SET COMPATIBILITY_LEVEL = 100");
			execute("master", "CREATE DATABASE " + ALKOL_DATABASE + " COLLATE Finnish_Swedish_CI_AS");
			execute("master", "ALTER DATABASE " + ALKOL_DATABASE + " SET COMPATIBILITY_LEVEL = 110");
			executeScript(ALKT_DATABASE, "/db/alkt.sql");
			executeScript(ALKOL_DATABASE, "/db/alkol.sql");
		} catch (final SQLException | IOException e) {
			throw new IllegalStateException("The source databases could not be created", e);
		}
		created = true;
	}

	static String url(final String database) {
		return CONTAINER.getJdbcUrl() + ";databaseName=" + database + ";trustServerCertificate=true";
	}

	static String username() {
		return CONTAINER.getUsername();
	}

	static String password() {
		return CONTAINER.getPassword();
	}

	private static void executeScript(final String database, final String resource) throws IOException, SQLException {
		try (InputStream stream = SourceDatabaseContainer.class.getResourceAsStream(resource)) {
			final var script = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
			final var batches = Arrays.stream(script.split("(?m)^GO\\s*$"))
				.map(batch -> batch.lines().filter(line -> !line.stripLeading().startsWith("--")).reduce("", (a, b) -> a + "\n" + b).strip())
				.filter(batch -> !batch.isEmpty())
				.toList();
			for (final var batch : batches) {
				execute(database, batch);
			}
		}
	}

	private static void execute(final String database, final String sql) throws SQLException {
		try (var connection = DriverManager.getConnection(url(database), username(), password()); var statement = connection.createStatement()) {
			statement.execute(sql);
		}
	}
}
