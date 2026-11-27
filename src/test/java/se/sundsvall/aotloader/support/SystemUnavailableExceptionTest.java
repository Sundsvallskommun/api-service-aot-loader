package se.sundsvall.aotloader.support;

import feign.Request;
import feign.RetryableException;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.SocketTimeoutException;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2AuthorizationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import se.sundsvall.dept44.problem.Problem;

import static java.nio.charset.StandardCharsets.UTF_8;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpStatus.BAD_GATEWAY;

class SystemUnavailableExceptionTest {

	@Test
	void aSystemThatCouldNotBeReachedIsToldByTheErrorOrItsCauses() {
		final var request = Request.create(Request.HttpMethod.GET, "/errands", Map.of(), null, UTF_8, null);

		assertThat(SystemUnavailableException.isCauseOf(new SystemUnavailableException("Filarean för AlkT gick inte att läsa"))).isTrue();
		assertThat(SystemUnavailableException.isCauseOf(new RetryableException(-1, "Read timed out", Request.HttpMethod.GET, new SocketTimeoutException(), (Long) null, request))).isTrue();
		assertThat(SystemUnavailableException.isCauseOf(new IllegalStateException("Fel", new UncheckedIOException(new IOException("unexpected end of stream"))))).isTrue();
		assertThat(SystemUnavailableException.isCauseOf(new OAuth2AuthorizationException(new OAuth2Error("server_error")))).isTrue();
	}

	@Test
	void anAnswerOrAFailureOfTheMigrationIsNot() {
		final var selfCaused = new IllegalStateException("Fel") {

			@Override
			public synchronized Throwable getCause() {
				return this;
			}
		};

		assertThat(SystemUnavailableException.isCauseOf(Problem.valueOf(BAD_GATEWAY, "Fel"))).isFalse();
		assertThat(SystemUnavailableException.isCauseOf(new MigrationException("Två ärenden har migreringsid ALKT-TILLSTAND-1"))).isFalse();
		assertThat(SystemUnavailableException.isCauseOf(selfCaused)).isFalse();
		assertThat(SystemUnavailableException.isCauseOf(null)).isFalse();
	}
}
