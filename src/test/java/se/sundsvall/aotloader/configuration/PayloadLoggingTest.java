package se.sundsvall.aotloader.configuration;

import feign.Logger;
import generated.se.sundsvall.partyassets.AssetCreateRequest;
import java.util.LinkedHashMap;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.logging.LogLevel;
import org.springframework.boot.logging.LoggerConfiguration;
import org.springframework.boot.logging.LoggingSystem;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.context.ApplicationContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import org.zalando.logbook.Logbook;
import org.zalando.logbook.openfeign.FeignLogbookLogger;
import se.sundsvall.aotloader.Application;
import se.sundsvall.aotloader.integration.partyassets.PartyAssetsClient;
import se.sundsvall.aotloader.service.MigrationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON;

/**
 * Runs the service with every logger on TRACE, the root logger too, and checks that no body
 * reaches the log (K6).
 */
@ActiveProfiles("junit")
@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
@ExtendWith(OutputCaptureExtension.class)
class PayloadLoggingTest {

	private static final String MARKER = "191212121212-payload-marker";
	private static final String[] PAYLOAD_LOGGERS = {
		LoggingSystem.ROOT_LOGGER_NAME, "se.sundsvall", "se.sundsvall.dept44.payload", "org.zalando.logbook", "org.springframework.web", "org.springframework.cloud.openfeign", "org.apache.hc.client5"
	};

	@MockitoBean
	private MigrationService service;

	@Autowired
	private WebTestClient webTestClient;

	@Autowired
	private LoggingSystem loggingSystem;

	@Autowired
	private ApplicationContext context;

	@Autowired
	private PartyAssetsClient partyAssetsClient;

	@Test
	void noRequestToTheServiceIsLogged(final CapturedOutput output) {
		final var restore = traceOn();
		try {
			// TRACE is on, as the migration's own logger shows, and the filter keeps the libraries quiet
			assertThat(LoggerFactory.getLogger("se.sundsvall.aotloader.service.ItemMigrator").isTraceEnabled()).isTrue();
			assertThat(LoggerFactory.getLogger("org.apache.coyote.http11.Http11InputBuffer").isDebugEnabled()).isFalse();
			assertThat(LoggerFactory.getLogger("se.sundsvall.dept44.payload").isTraceEnabled()).isFalse();

			webTestClient.put().uri("/{municipalityId}/migrations/exceptions/ALKT/TILLSTAND/1", "2281")
				.header("X-Marker", MARKER)
				.contentType(APPLICATION_JSON)
				.bodyValue("{\"outcome\": \"MIGRERA\", \"decidedBy\": \"handlaggare\", \"comment\": \"" + MARKER + "\"}")
				.exchange()
				.expectStatus().isOk();

			assertThat(output).doesNotContain(MARKER);
		} finally {
			restore.run();
		}
	}

	@Test
	void noRequestOfTheClientsIsLogged(final CapturedOutput output) {
		final var restore = traceOn();
		try {
			// The body is written before the token is fetched, which fails, as no API runs in the test
			final var failure = catchThrowable(() -> partyAssetsClient.createAsset("2281", "reference", new AssetCreateRequest().title(MARKER)));

			assertThat(failure).isNotNull();
			assertThat(output).doesNotContain(MARKER);
		} finally {
			restore.run();
		}
	}

	@Test
	void theClientsUseTheSameLogbook() {
		assertThat(context.getBeansOfType(Logbook.class)).containsOnlyKeys("logbook");
		assertThat(context.getBean(Logger.class)).isInstanceOf(FeignLogbookLogger.class);
	}

	/**
	 * Sets the loggers to TRACE.
	 *
	 * @return what sets them back
	 */
	private Runnable traceOn() {
		final var configured = new LinkedHashMap<String, LogLevel>();
		for (final var name : PAYLOAD_LOGGERS) {
			configured.put(name, Optional.ofNullable(loggingSystem.getLoggerConfiguration(name)).map(LoggerConfiguration::getConfiguredLevel).orElse(null));
			loggingSystem.setLogLevel(name, LogLevel.TRACE);
		}
		return () -> configured.forEach(loggingSystem::setLogLevel);
	}
}
