package se.sundsvall.aotloader.source;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.DisposableBean;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Database;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.support.MigrationException;
import se.sundsvall.aotloader.support.Texts;

/**
 * The connections to the source databases. They are opened on first use, so a run of one source does not need the
 * other. The migration only selects (K7), and a real run does not start when the account of a source may change
 * anything in its database (see {@link #writeAccess}). Neither Flyway nor JPA ever sees them.
 */
@Component
public class SourceDatabases implements DisposableBean {

	private static final int MAX_POOL_SIZE = 2;
	private static final long CONNECTION_TIMEOUT_MILLIS = 30_000;

	// The effective permissions, from roles and grants on the database, its schemas and objects alike
	private static final String SELECT_WRITE_ACCESS = """
		SELECT
		  (SELECT COUNT(*)
		   FROM sys.objects o
		   CROSS APPLY (SELECT QUOTENAME(SCHEMA_NAME(o.schema_id)) + '.' + QUOTENAME(o.name) AS ObjectName) n
		   WHERE o.type IN ('U', 'V')
		     AND o.is_ms_shipped = 0
		     AND (HAS_PERMS_BY_NAME(n.ObjectName, 'OBJECT', 'INSERT') = 1
		       OR HAS_PERMS_BY_NAME(n.ObjectName, 'OBJECT', 'UPDATE') = 1
		       OR HAS_PERMS_BY_NAME(n.ObjectName, 'OBJECT', 'DELETE') = 1
		       OR HAS_PERMS_BY_NAME(n.ObjectName, 'OBJECT', 'ALTER') = 1)) AS WritableObjects,
		  HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'CREATE TABLE') AS CreateTable,
		  HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'ALTER ANY SCHEMA') AS AlterAnySchema,
		  HAS_PERMS_BY_NAME(DB_NAME(), 'DATABASE', 'ALTER') AS AlterDatabase
		""";

	private final AotLoaderProperties properties;
	private final Map<Source, HikariDataSource> dataSources = new EnumMap<>(Source.class);

	SourceDatabases(final AotLoaderProperties properties) {
		this.properties = properties;
	}

	/**
	 * @return                    a template for queries against the source
	 * @throws MigrationException when the source has no database configured
	 */
	public synchronized JdbcTemplate jdbcTemplate(final Source source) {
		final var database = properties.database(source)
			.orElseThrow(() -> new MigrationException("Källdatabasen för %s är inte konfigurerad".formatted(source.getDisplayName())));
		final var template = new JdbcTemplate(dataSources.computeIfAbsent(source, key -> createDataSource(key, database)));
		template.setQueryTimeout((int) database.queryTimeout().toSeconds());
		return template;
	}

	/**
	 * @return                    what the account of the source may change in its database, empty when it may only read
	 * @throws MigrationException when the source has no database configured
	 */
	public Optional<String> writeAccess(final Source source) {
		return writeAccess(jdbcTemplate(source));
	}

	static Optional<String> writeAccess(final JdbcTemplate jdbc) {
		final var access = jdbc.queryForObject(SELECT_WRITE_ACCESS, (rs, _) -> {
			final var found = new ArrayList<String>();
			final var objects = rs.getInt("WritableObjects");
			if (objects > 0) {
				found.add(objects == 1 ? "ändra innehållet i en tabell eller vy" : "ändra innehållet i %d tabeller eller vyer".formatted(objects));
			}
			if (rs.getInt("CreateTable") == 1 || rs.getInt("AlterAnySchema") == 1 || rs.getInt("AlterDatabase") == 1) {
				found.add("ändra databasens struktur");
			}
			return found;
		});
		return access == null || access.isEmpty() ? Optional.empty() : Optional.of("kan " + String.join(" och ", access));
	}

	@Override
	public synchronized void destroy() {
		dataSources.values().forEach(HikariDataSource::close);
		dataSources.clear();
	}

	private static HikariDataSource createDataSource(final Source source, final Database database) {
		final var config = new HikariConfig();
		config.setPoolName(source.name().toLowerCase(Locale.ROOT) + "-source");
		config.setJdbcUrl(database.url());
		if (!Texts.isBlank(database.username())) {
			config.setUsername(database.username());
			config.setPassword(database.password());
		}
		// Only a hint: the driver of SQL Server does not stop a write on a read-only connection
		config.setReadOnly(true);
		config.setAutoCommit(true);
		config.setMaximumPoolSize(MAX_POOL_SIZE);
		config.setMinimumIdle(0);
		config.setConnectionTimeout(CONNECTION_TIMEOUT_MILLIS);
		// The pool opens no connection until the first query, so a source that cannot be reached fails that query, not the
		// start of the application
		config.setInitializationFailTimeout(-1);
		return new HikariDataSource(config);
	}
}
