package se.sundsvall.aotloader.configuration;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import ch.qos.logback.core.spi.FilterReply;
import java.io.IOException;
import java.util.List;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.zalando.logbook.HttpRequest;
import org.zalando.logbook.HttpResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

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

	@Test
	void anErrorThatStopsARunIsLoggedByTheMigration() {
		final var logger = (ch.qos.logback.classic.Logger) LoggerFactory.getLogger(ExecutorConfiguration.class);
		final var appender = new ListAppender<ILoggingEvent>();
		appender.start();
		logger.addAppender(appender);
		final var executor = new ExecutorConfiguration().migrationExecutor();
		try {
			executor.execute(() -> {
				throw new StackOverflowError();
			});

			await().atMost(5, TimeUnit.SECONDS).until(() -> !appender.list.isEmpty());
			assertThat(appender.list).singleElement().satisfies(event -> {
				assertThat(event.getLevel()).isEqualTo(Level.ERROR);
				assertThat(event.getFormattedMessage()).isEqualTo("migration-run stopped: StackOverflowError");
				assertThat(event.getThrowableProxy()).isNotNull();
			});
		} finally {
			executor.shutdownNow();
			logger.detachAppender(appender);
		}
	}

	@Test
	void logbookNeverReadsARequestOrAResponseAlsoWhenItsLoggerIsOn() throws IOException {
		// The sink of the Logbook writes at TRACE; with it off, the Logbook would read nothing whatever its condition said.
		// The filter of a Spring test that ran before would keep it off, so it is set aside meanwhile.
		final var context = (LoggerContext) LoggerFactory.getILoggerFactory();
		final var filters = List.copyOf(context.getTurboFilterList());
		final var logbookLogger = context.getLogger("org.zalando.logbook");
		final var level = logbookLogger.getLevel();
		context.getTurboFilterList().clear();
		logbookLogger.setLevel(Level.TRACE);
		try {
			assertThat(context.getLogger("org.zalando.logbook.Logbook").isTraceEnabled()).isTrue();
			final var request = mock(HttpRequest.class);
			final var response = mock(HttpResponse.class);

			new PayloadLoggingConfiguration().logbook().process(request).write().process(response).write();

			verifyNoInteractions(request, response);
		} finally {
			logbookLogger.setLevel(level);
			context.getTurboFilterList().addAll(filters);
		}
	}

	@Test
	void thirdPartyDebugFilterLetsOnlyTheMigrationsOwnLoggersBelowInfo() {
		final var context = new LoggerContext();
		final var filter = ThirdPartyDebugFilter.register(context);
		final var tomcat = context.getLogger("org.apache.coyote.http11.Http11InputBuffer");
		final var own = context.getLogger("se.sundsvall.aotloader.service.ItemMigrator");

		assertThat(ThirdPartyDebugFilter.register(context)).isSameAs(filter);
		assertThat(context.getTurboFilterList()).containsExactly(filter);
		assertThat(filter.isStarted()).isTrue();
		assertThat(filter.decide(null, tomcat, Level.TRACE, null, null, null)).isEqualTo(FilterReply.DENY);
		assertThat(filter.decide(null, tomcat, Level.DEBUG, null, null, null)).isEqualTo(FilterReply.DENY);
		assertThat(filter.decide(null, tomcat, Level.INFO, null, null, null)).isEqualTo(FilterReply.NEUTRAL);
		assertThat(filter.decide(null, tomcat, Level.ERROR, null, null, null)).isEqualTo(FilterReply.NEUTRAL);
		assertThat(filter.decide(null, tomcat, null, null, null, null)).isEqualTo(FilterReply.NEUTRAL);
		assertThat(filter.decide(null, own, Level.DEBUG, null, null, null)).isEqualTo(FilterReply.NEUTRAL);
		// Only the migration's own package, not a package that starts the same way
		assertThat(filter.decide(null, context.getLogger("se.sundsvall.aotloaderx.Other"), Level.DEBUG, null, null, null)).isEqualTo(FilterReply.DENY);
	}
}
