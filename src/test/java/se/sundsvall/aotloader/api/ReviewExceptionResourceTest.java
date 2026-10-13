package se.sundsvall.aotloader.api;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.sundsvall.aotloader.Application;
import se.sundsvall.aotloader.api.model.ReviewException;
import se.sundsvall.aotloader.api.model.ReviewExceptionRequest;
import se.sundsvall.aotloader.service.MigrationService;
import se.sundsvall.aotloader.service.model.ExceptionOutcome;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@ActiveProfiles("junit")
@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
class ReviewExceptionResourceTest {

	private static final String MUNICIPALITY_ID = "2281";

	@MockitoBean
	private MigrationService service;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void listPutAndDelete() {
		final var key = new ItemKey(Source.ALKT, Kind.TILLSTAND, "1");
		final var request = new ReviewExceptionRequest(ExceptionOutcome.MIGRERA_SOM_UTGANGET, "handlaggare", LocalDate.of(2026, 10, 1), "Utgånget men ska synas");
		final var exception = new ReviewException(Source.ALKT, Kind.TILLSTAND, "1", ExceptionOutcome.MIGRERA_SOM_UTGANGET, "handlaggare", LocalDate.of(2026, 10, 1), "Utgånget men ska synas");
		when(service.getExceptions()).thenReturn(List.of(exception));
		when(service.putException(key, request)).thenReturn(exception);

		webTestClient.get().uri("/{municipalityId}/migrations/exceptions", MUNICIPALITY_ID).exchange().expectStatus().isOk().expectBodyList(ReviewException.class).contains(exception);
		webTestClient.put().uri("/{municipalityId}/migrations/exceptions/ALKT/TILLSTAND/1", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue(request)
			.exchange()
			.expectStatus().isOk()
			.expectBody(ReviewException.class).isEqualTo(exception);
		webTestClient.delete().uri("/{municipalityId}/migrations/exceptions/ALKT/TILLSTAND/1", MUNICIPALITY_ID).exchange().expectStatus().isNoContent();

		verify(service).deleteException(key);
	}

	@Test
	void anInvalidRequest() {
		final var problem = webTestClient.put().uri("/{municipalityId}/migrations/exceptions/ALKT/TILLSTAND/1", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue(new ReviewExceptionRequest(null, " ", null, null))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.returnResult()
			.getResponseBody();

		assertThat(problem).isNotNull();
		assertThat(problem.getViolations()).extracting("field").containsExactlyInAnyOrder("outcome", "decidedBy");
		verifyNoInteractions(service);
	}
}
