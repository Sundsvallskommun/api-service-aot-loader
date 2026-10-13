package se.sundsvall.aotloader.configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ExecutorConfiguration {

	public static final String MIGRATION_EXECUTOR = "migrationExecutor";

	/**
	 * Runs the migration in the background, one run at a time. A run interrupted by a shutdown is marked ABORTED at the
	 * next start, and a new run continues where it stopped.
	 */
	@Bean(name = MIGRATION_EXECUTOR, destroyMethod = "shutdownNow")
	ExecutorService migrationExecutor() {
		return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("migration-run").factory());
	}
}
