package se.sundsvall.aotloader.integration;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.http.Fault;
import feign.Client;
import feign.Feign;
import feign.RequestLine;
import feign.Response;
import feign.RetryableException;
import java.net.SocketTimeoutException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import se.sundsvall.dept44.configuration.feign.FeignMultiCustomizer;
import se.sundsvall.dept44.configuration.feign.retryer.ActionRetryer;
import se.sundsvall.dept44.security.Truststore;
import se.sundsvall.dept44.support.Identifier;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.any;
import static com.github.tomakehurst.wiremock.client.WireMock.anyUrl;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.options;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Runs the protections the clients add through Feign itself, with the retryer dept44 gives a client with OAuth2.
 */
class ClientSafetyTest {

	private static final String SENT_BY = "alkt-migration; type=migration";

	private final AtomicInteger calls = new AtomicInteger();
	private final AtomicInteger evictedTokens = new AtomicInteger();
	private final List<Collection<String>> sentBy = new ArrayList<>();
	private WireMockServer server;

	interface TestApi {

		@RequestLine("POST /errands")
		void create(String body);

		@RequestLine("PATCH /errands/1")
		void update(String body);

		@RequestLine("GET /errands")
		String find();
	}

	@AfterEach
	void tearDown() {
		Identifier.remove();
		if (server != null) {
			server.stop();
		}
	}

	@Test
	void aWriteIsSentOnceWhenTheConnectionBreaksAndAReadIsSentAgainOnce() {
		server = new WireMockServer(options().dynamicPort());
		server.start();
		server.stubFor(any(anyUrl()).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));
		final var capability = new NoResendCapability(new StaticListableBeanFactory().getBeanProvider(Truststore.class));
		// localhost has two addresses, and OkHttp would on its own try them both
		final var api = Feign.builder()
			.retryer(new ActionRetryer(_ -> evictedTokens.incrementAndGet(), 1))
			.addCapability(capability)
			.target(TestApi.class, "http://localhost:" + server.port());

		assertThatThrownBy(() -> api.create("{}")).isInstanceOf(RetryableException.class);
		assertThatThrownBy(api::find).isInstanceOf(RetryableException.class);

		assertThat(server.findAll(postRequestedFor(urlEqualTo("/errands")))).hasSize(1);
		assertThat(server.findAll(getRequestedFor(urlEqualTo("/errands")))).hasSize(2);
		assertThat(evictedTokens).hasValue(1);
	}

	@Test
	void aWriteIsNeverSentAgainAfterAnIoError() {
		final var api = api(timingOut());

		assertThatThrownBy(() -> api.create("{}")).isInstanceOf(RetryableException.class).hasCauseInstanceOf(SocketTimeoutException.class);
		assertThatThrownBy(() -> api.update("{}")).isInstanceOf(RetryableException.class);

		assertThat(calls).hasValue(2);
	}

	@Test
	void aReadIsSentAgainOnceAfterAnIoError() {
		final var api = api(timingOut());

		assertThatThrownBy(api::find).isInstanceOf(RetryableException.class);

		assertThat(calls).hasValue(2);
	}

	@Test
	void aWriteRefusedForItsTokenIsSentAgainWithANewOne() {
		final Client unauthorized = (request, _) -> {
			calls.incrementAndGet();
			return Response.builder().status(401).reason("Unauthorized").request(request).headers(Map.of()).build();
		};
		final var api = Feign.builder()
			.client(unauthorized)
			.retryer(new NoResendCapability.NoResendRetryer(new ActionRetryer(_ -> evictedTokens.incrementAndGet(), 1)))
			.errorDecoder((_, response) -> new RetryableException(response.status(), "Unauthorized", response.request().httpMethod(), (Long) null, response.request()))
			.target(TestApi.class, "http://localhost");

		assertThatThrownBy(() -> api.create("{}")).isInstanceOf(RetryableException.class);

		assertThat(calls).hasValue(2);
		assertThat(evictedTokens).hasValue(1);
	}

	@Test
	void theMigrationIsTheOnlySenderAlsoWhenACallerSentTheRequest() {
		Identifier.set(Identifier.parse("joe01doe; type=adAccount"));
		final var builder = Feign.builder()
			.client((request, _) -> {
				sentBy.add(request.headers().get(Identifier.HEADER_NAME));
				return Response.builder().status(200).reason("OK").request(request).headers(Map.of()).build();
			});
		FeignMultiCustomizer.create()
			.withRequestInterceptor(new SentByInterceptor(SENT_BY))
			.composeCustomizersToOne()
			.customize(builder);

		builder.target(TestApi.class, "http://localhost").create("{}");

		assertThat(sentBy).singleElement().satisfies(values -> assertThat(values).containsExactly(SENT_BY));
	}

	private Client timingOut() {
		return (_, _) -> {
			calls.incrementAndGet();
			throw new SocketTimeoutException("Read timed out");
		};
	}

	/**
	 * The API on the given client, with the retryer of the capability in front of the one of dept44.
	 */
	private TestApi api(final Client client) {
		return Feign.builder()
			.client(client)
			.retryer(new NoResendCapability.NoResendRetryer(new ActionRetryer(_ -> evictedTokens.incrementAndGet(), 1)))
			.target(TestApi.class, "http://localhost");
	}
}
