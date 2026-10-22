package se.sundsvall.aotloader.api;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.sundsvall.aotloader.Application;
import se.sundsvall.aotloader.api.model.CleanupRequest;
import se.sundsvall.aotloader.api.model.ReportLine;
import se.sundsvall.aotloader.api.model.Run;
import se.sundsvall.aotloader.service.MigrationService;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.RunStatus;
import se.sundsvall.aotloader.service.model.Source;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@ActiveProfiles("junit")
@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT, properties = "aot-loader.cleanup.enabled=true")
@AutoConfigureWebTestClient
class CleanupResourceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String RUN_ID = "5f79a808-0ef3-4985-99b9-b12f23e202a7";

	@MockitoBean
	private MigrationService service;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void startCleanup() {
		final var request = new CleanupRequest(false, List.of(Source.ALKT), List.of(Kind.TILLSTAND), 5);
		when(service.startCleanup(MUNICIPALITY_ID, request))
			.thenReturn(new Run(RUN_ID, MUNICIPALITY_ID, false, true, List.of("ALKT"), List.of("TILLSTAND"), null, 5, RunStatus.RUNNING, null, OffsetDateTime.parse("2026-10-22T10:00:00+02:00"), null, Map.of()));

		final var response = webTestClient.post().uri("/{municipalityId}/migrations/cleanups", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue(request)
			.exchange()
			.expectStatus().isAccepted()
			.expectHeader().location("/" + MUNICIPALITY_ID + "/migrations/runs/" + RUN_ID)
			.expectBody(Run.class)
			.returnResult()
			.getResponseBody();

		assertThat(response).isNotNull();
		assertThat(response.cleanup()).isTrue();
	}

	@Test
	void aCleanupRequestWithAnUnknownFieldIsRefused() {
		webTestClient.post().uri("/{municipalityId}/migrations/cleanups", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue(Map.of("dryrun", false))
			.exchange()
			.expectStatus().isBadRequest();

		verifyNoInteractions(service);
	}

	@Test
	void cleanupItemIsADryRunUnlessAskedOtherwise() {
		final var key = new ItemKey(Source.ALKOL, Kind.ANMALAN, "1234-FOLKOL");
		when(service.cleanupItem(MUNICIPALITY_ID, key, true))
			.thenReturn(new ReportLine(Source.ALKOL, Kind.ANMALAN, "1234-FOLKOL", null, "Kiosken", Outcome.SKULLE_RENSAS, "", "Tas bort: ärendet i SupportManagement och raden i statustabellen", "ALKOL-1"));

		final var response = webTestClient.post().uri("/{municipalityId}/migrations/items/ALKOL/ANMALAN/1234-FOLKOL/cleanup", MUNICIPALITY_ID)
			.exchange()
			.expectStatus().isOk()
			.expectBody(ReportLine.class)
			.returnResult()
			.getResponseBody();

		assertThat(response).isNotNull();
		assertThat(response.outcome()).isEqualTo(Outcome.SKULLE_RENSAS);
	}

	@Test
	void cleanupItemWithAnIdThatIsNoIdIsRefused() {
		webTestClient.post().uri("/{municipalityId}/migrations/items/ALKT/TILLSTAND/abc/cleanup?dryRun=false", MUNICIPALITY_ID)
			.exchange()
			.expectStatus().isBadRequest();

		verifyNoInteractions(service);
	}
}
