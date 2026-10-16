package se.sundsvall.aotloader.configuration;

import ch.qos.logback.classic.LoggerContext;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.zalando.logbook.Logbook;

/**
 * Keeps requests and responses out of the log, whatever the log levels say: they hold names and personal identity
 * numbers (K6).
 */
@Configuration
public class PayloadLoggingConfiguration {

	/**
	 * The Logbook of the service and of its clients. It logs no request and no response.
	 */
	@Bean
	Logbook logbook() {
		// A log level would not do: dept44 sets its payload logger to TRACE in a file that outranks application.yml
		return Logbook.builder().condition(_ -> false).build();
	}

	/**
	 * The filter that keeps DEBUG and TRACE of the libraries out of the log, added to Logback.
	 */
	@Bean
	ThirdPartyDebugFilter thirdPartyDebugFilter() {
		// A logback-spring.xml of our own would replace the one of dept44, so the filter is added here instead
		return ThirdPartyDebugFilter.register((LoggerContext) LoggerFactory.getILoggerFactory());
	}
}
