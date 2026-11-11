package se.sundsvall.aotloader.source;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.configuration.AotLoaderProperties.Database;
import se.sundsvall.aotloader.service.model.Source;

import static org.assertj.core.api.Assertions.assertThat;

class SourceDatabasesTest {

	@Test
	void aReadWhoseConnectionIsLostWaitsNoLongerThanTheQueryTimeoutAndAMinute() {
		try (final var dataSource = SourceDatabases.createDataSource(Source.ALKT, new Database("jdbc:sqlserver://localhost:1433;databaseName=AlkT", null, null, Duration.ofMinutes(2)))) {
			assertThat(dataSource.getDataSourceProperties()).containsEntry("socketTimeout", "180000");
			assertThat(dataSource.isReadOnly()).isTrue();
			assertThat(dataSource.getPoolName()).isEqualTo("alkt-source");
		}
	}
}
