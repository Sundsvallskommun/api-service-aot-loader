package se.sundsvall.aotloader.configuration;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;

class LocalCallerFilterTest {

	private final LocalCallerFilter filter = new LocalCallerFilter();

	@ParameterizedTest
	@CsvSource(nullValues = "-", value = {
		// Host, method, Origin, Sec-Fetch-Site, let through
		"localhost:8080, GET, -, -, true",
		"127.0.0.1:8080, POST, -, -, true",
		"[::1]:8080, POST, http://[::1]:8080, same-origin, true",
		"LOCALHOST, PATCH, http://Localhost:8080, -, true",
		"localhost, PUT, https://127.0.0.1, none, true",
		// A page that has its own name resolve to the loopback address
		"rebound.example.com, GET, -, -, false",
		// A request without a Host
		"-, GET, -, -, false",
		"rebound.example.com:8080, POST, http://rebound.example.com:8080, same-origin, false",
		// A page of another site that posts a form or makes a request
		"localhost:8080, POST, https://example.com, cross-site, false",
		"localhost:8080, DELETE, http://localhost.example.com, -, false",
		"localhost:8080, POST, null, -, false",
		"localhost:8080, POST, ftp://localhost, -, false",
		"localhost:8080, POST, http://a b, -, false",
		"localhost:8080, POST, -, cross-site, false",
		"localhost:8080, POST, http://localhost:3000, same-site, false",
		// A page of another site cannot read what it gets
		"localhost:8080, GET, https://example.com, cross-site, true",
		"localhost:8080, OPTIONS, https://example.com, cross-site, true"
	})
	void onlyThisComputerMayCall(final String host, final String method, final String origin, final String fetchSite, final boolean letThrough) throws Exception {
		final var request = new MockHttpServletRequest(method, "/2281/migrations/runs");
		if (host != null) {
			request.addHeader("Host", host);
		}
		if (origin != null) {
			request.addHeader("Origin", origin);
		}
		if (fetchSite != null) {
			request.addHeader("Sec-Fetch-Site", fetchSite);
		}
		final var response = new MockHttpServletResponse();
		final var chain = new MockFilterChain();

		filter.doFilter(request, response, chain);

		if (letThrough) {
			assertThat(chain.getRequest()).isSameAs(request);
			assertThat(response.getStatus()).isEqualTo(200);
		} else {
			assertThat(chain.getRequest()).isNull();
			assertThat(response.getStatus()).isEqualTo(403);
			assertThat(response.getContentType()).startsWith("application/problem+json");
			assertThat(response.getContentAsString()).startsWith("{\"title\":\"Forbidden\",\"status\":403,\"detail\":\"Anropet ");
		}
	}

	@Test
	void aForwardedHostDoesNotMakeAPageThisComputer() throws Exception {
		final var request = new MockHttpServletRequest("GET", "/2281/migrations/runs");
		request.addHeader("Host", "rebound.example.com");
		request.addHeader("X-Forwarded-Host", "localhost");
		request.addHeader("Forwarded", "host=localhost");
		// What a filter that honours the headers would make of the request
		request.setServerName("localhost");
		final var response = new MockHttpServletResponse();

		filter.doFilter(request, response, new MockFilterChain());

		assertThat(response.getStatus()).isEqualTo(403);
	}

	@Test
	void theHostOfAHostHeader() {
		assertThat(LocalCallerFilter.hostOf("localhost:8080")).isEqualTo("localhost");
		assertThat(LocalCallerFilter.hostOf(" 127.0.0.1 ")).isEqualTo("127.0.0.1");
		assertThat(LocalCallerFilter.hostOf("[::1]:8080")).isEqualTo("[::1]");
		assertThat(LocalCallerFilter.hostOf("[::1")).isEqualTo("[::1");
		assertThat(LocalCallerFilter.hostOf(null)).isNull();
	}

	@Test
	void loopbackNames() {
		assertThat(LocalCallerFilter.isLoopback("localhost")).isTrue();
		assertThat(LocalCallerFilter.isLoopback("::1")).isTrue();
		assertThat(LocalCallerFilter.isLoopback("127.0.0.2")).isFalse();
		assertThat(LocalCallerFilter.isLoopback(null)).isFalse();
		assertThat(LocalCallerFilter.isFromThisComputer(null, "SAME-ORIGIN")).isTrue();
		assertThat(LocalCallerFilter.isFromThisComputer("http://", null)).isFalse();
	}
}
