package se.sundsvall.aotloader.configuration;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import se.sundsvall.aotloader.support.ErrorTexts;

@Configuration
public class ExecutorConfiguration {

	public static final String MIGRATION_EXECUTOR = "migrationExecutor";

	private static final Logger LOG = LoggerFactory.getLogger(ExecutorConfiguration.class);

	/**
	 * Runs the migration in the background, one run at a time. A run interrupted by a shutdown is marked ABORTED at the
	 * next start, and a new run continues where it stopped. An Error that stops a run, which the run passes on after
	 * saving it as failed, is logged here.
	 */
	@Bean(name = MIGRATION_EXECUTOR, destroyMethod = "shutdownNow")
	ExecutorService migrationExecutor() {
		return Executors.newSingleThreadExecutor(Thread.ofPlatform().name("migration-run").uncaughtExceptionHandler(ExecutorConfiguration::logUncaught).factory());
	}

	private static void logUncaught(final Thread thread, final Throwable error) {
		LOG.error("{} stopped: {}", thread.getName(), error.getClass().getSimpleName(), ErrorTexts.forLog(error));
	}
}
