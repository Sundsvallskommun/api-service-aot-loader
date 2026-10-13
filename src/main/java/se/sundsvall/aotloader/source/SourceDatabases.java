package se.sundsvall.aotloader.source;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import java.util.EnumMap;
import java.util.Locale;
import java.util.Map;
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
 * other, and they are read-only: the migration only selects (K7). Neither Flyway nor JPA ever sees them.
 */
@Component
public class SourceDatabases implements DisposableBean {

	private static final int MAX_POOL_SIZE = 2;
	private static final long CONNECTION_TIMEOUT_MILLIS = 30_000;

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
