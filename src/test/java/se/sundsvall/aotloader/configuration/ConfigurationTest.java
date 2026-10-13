package se.sundsvall.aotloader.configuration;

import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class ConfigurationTest {

	@Test
	void clockIsSwedish() {
		assertThat(new ClockConfiguration().clock().getZone()).isEqualTo(ClockConfiguration.SWEDISH_TIME);
	}

	@Test
	void migrationExecutorRunsOneRunAtATimeOnItsOwnThread() throws Exception {
		final var executor = new ExecutorConfiguration().migrationExecutor();
		try {
			final var threadName = executor.submit(() -> Thread.currentThread().getName()).get(5, TimeUnit.SECONDS);

			assertThat(threadName).isEqualTo("migration-run");
		} finally {
			executor.shutdownNow();
		}
	}
}
