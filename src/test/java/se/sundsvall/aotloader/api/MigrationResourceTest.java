package se.sundsvall.aotloader.api;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webtestclient.autoconfigure.AutoConfigureWebTestClient;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.reactive.server.WebTestClient;
import se.sundsvall.aotloader.Application;
import se.sundsvall.aotloader.api.model.MigrationItem;
import se.sundsvall.aotloader.api.model.ReportLine;
import se.sundsvall.aotloader.api.model.Run;
import se.sundsvall.aotloader.api.model.RunRequest;
import se.sundsvall.aotloader.service.MigrationService;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.RunStatus;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.dept44.problem.Problem;
import se.sundsvall.dept44.problem.violations.ConstraintViolationProblem;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.boot.test.context.SpringBootTest.WebEnvironment.RANDOM_PORT;
import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.MediaType.APPLICATION_JSON;

@ActiveProfiles("junit")
@SpringBootTest(classes = Application.class, webEnvironment = RANDOM_PORT)
@AutoConfigureWebTestClient
class MigrationResourceTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String RUN_ID = "5f79a808-0ef3-4985-99b9-b12f23e202a7";

	@MockitoBean
	private MigrationService service;

	@Autowired
	private WebTestClient webTestClient;

	@Test
	void startRun() {
		final var request = new RunRequest(false, List.of(Source.ALKT), List.of(Kind.TILLSTAND), 5, List.of("ALKT/TILLSTAND/1"));
		when(service.startRun(MUNICIPALITY_ID, request)).thenReturn(run(RunStatus.RUNNING));

		final var response = webTestClient.post().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue(request)
			.exchange()
			.expectStatus().isAccepted()
			.expectHeader().location("/" + MUNICIPALITY_ID + "/migrations/runs/" + RUN_ID)
			.expectBody(Run.class)
			.returnResult()
			.getResponseBody();

		assertThat(response).isNotNull();
		assertThat(response.id()).isEqualTo(RUN_ID);
	}

	@Test
	void aRequestThatAnotherSiteSentIsRefused() {
		webTestClient.post().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID)
			.header("Origin", "https://example.com")
			.header("Sec-Fetch-Site", "cross-site")
			.contentType(APPLICATION_JSON)
			.bodyValue(new RunRequest(false, null, null, null, null))
			.exchange()
			.expectStatus().isForbidden();

		verifyNoInteractions(service);
	}

	@Test
	void aForwardedHostIsNotTakenForThisComputer() {
		// Through the whole chain of filters, where dept44 would otherwise honour the forwarded host
		webTestClient.get().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID)
			.header("Host", "rebound.example.com")
			.header("X-Forwarded-Host", "localhost")
			.header("Forwarded", "host=localhost")
			.exchange()
			.expectStatus().isForbidden();

		verifyNoInteractions(service);
	}

	@Test
	void theCleanupIsNotThereUnlessTheEnvironmentTurnsItOn() {
		webTestClient.post().uri("/{municipalityId}/migrations/cleanups", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue(new RunRequest(false, null, null, null, null))
			.exchange()
			.expectStatus().isNotFound();
		webTestClient.post().uri("/{municipalityId}/migrations/items/ALKT/TILLSTAND/1/cleanup?dryRun=false", MUNICIPALITY_ID)
			.exchange()
			.expectStatus().isNotFound();

		verifyNoInteractions(service);
	}

	@Test
	void startRunWithInvalidLimitAndMunicipality() {
		webTestClient.post().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue(new RunRequest(true, null, null, 0, null))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class);

		webTestClient.post().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue("{\"sources\": [null], \"kinds\": [null], \"items\": [\"ALKT/TILLSTAND/1' OR '1'='1\"]}")
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.value(problem -> assertThat(problem.getViolations()).hasSize(3));

		webTestClient.post().uri("/{municipalityId}/migrations/runs", "invalid")
			.contentType(APPLICATION_JSON)
			.bodyValue(new RunRequest(true, null, null, null, null))
			.exchange()
			.expectStatus().isBadRequest();

		verifyNoInteractions(service);
	}

	@Test
	void aRowOfTheReportWithoutAnIdInTheSourceCanBeSelected() {
		when(service.startRun(eq(MUNICIPALITY_ID), any())).thenReturn(run(RunStatus.RUNNING));

		webTestClient.post().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue(new RunRequest(true, null, null, null, List.of("ALKT/TILLSTAND/utan-id-3", " ALKOL/ANMALAN/12-FOLKOL ")))
			.exchange()
			.expectStatus().isAccepted();

		webTestClient.post().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue(new RunRequest(true, null, null, null, List.of("ALKT/TILLSTAND/utan-id-", "ALKT/TILLSTAND/utan-id-3-FOLKOL")))
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.value(problem -> assertThat(problem.getViolations()).hasSize(2));
	}

	@Test
	void startRunWithAFieldItDoesNotHaveOrAnEmptyList() {
		webTestClient.post().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue("{\"dryRun\": false, \"limt\": 10}")
			.exchange()
			.expectStatus().isBadRequest();

		webTestClient.post().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue("{\"dryRun\": false, \"sources\": [], \"kinds\": [], \"items\": []}")
			.exchange()
			.expectStatus().isBadRequest()
			.expectBody(ConstraintViolationProblem.class)
			.value(problem -> assertThat(problem.getViolations()).extracting("field").containsExactlyInAnyOrder("sources", "kinds", "items"));

		verifyNoInteractions(service);
	}

	@Test
	void startRunWhileAnotherRuns() {
		when(service.startRun(eq(MUNICIPALITY_ID), any())).thenThrow(Problem.valueOf(CONFLICT, "En körning pågår redan"));

		final var problem = webTestClient.post().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID)
			.contentType(APPLICATION_JSON)
			.bodyValue(new RunRequest(null, null, null, null, null))
			.exchange()
			.expectStatus().isEqualTo(CONFLICT)
			.expectBody(Problem.class)
			.returnResult()
			.getResponseBody();

		assertThat(problem).isNotNull();
		assertThat(problem.getDetail()).isEqualTo("En körning pågår redan");
	}

	@Test
	void runsAndStop() {
		when(service.getRuns(MUNICIPALITY_ID)).thenReturn(List.of(run(RunStatus.COMPLETED)));
		when(service.getRun(MUNICIPALITY_ID, RUN_ID)).thenReturn(run(RunStatus.COMPLETED));
		when(service.stopRun(MUNICIPALITY_ID, RUN_ID)).thenReturn(run(RunStatus.RUNNING));

		webTestClient.get().uri("/{municipalityId}/migrations/runs", MUNICIPALITY_ID).exchange().expectStatus().isOk().expectBodyList(Run.class).hasSize(1);
		webTestClient.get().uri("/{municipalityId}/migrations/runs/{runId}", MUNICIPALITY_ID, RUN_ID).exchange().expectStatus().isOk().expectBody(Run.class);
		webTestClient.post().uri("/{municipalityId}/migrations/runs/{runId}/stop", MUNICIPALITY_ID, RUN_ID).exchange().expectStatus().isAccepted().expectBody(Run.class);
		webTestClient.get().uri("/{municipalityId}/migrations/runs/{runId}", MUNICIPALITY_ID, "not-a-uuid").exchange().expectStatus().isBadRequest();
	}

	@Test
	void report() {
		final var line = new ReportLine(Source.ALKT, Kind.TILLSTAND, "1", "12345678", "Krogen", Outcome.FEL, "G14", "Steg Ärende: fel", null);
		when(service.getReport(MUNICIPALITY_ID, RUN_ID, List.of(Outcome.FEL, Outcome.GRANSKAS))).thenReturn(List.of(line));
		when(service.getReport(MUNICIPALITY_ID, RUN_ID, null)).thenReturn(List.of(line));

		webTestClient.get().uri("/{municipalityId}/migrations/runs/{runId}/report?outcome=FEL&outcome=GRANSKAS", MUNICIPALITY_ID, RUN_ID)
			.exchange()
			.expectStatus().isOk()
			.expectBodyList(ReportLine.class).contains(line);

		final var csv = webTestClient.get().uri("/{municipalityId}/migrations/runs/{runId}/report.csv", MUNICIPALITY_ID, RUN_ID)
			.accept(MediaType.valueOf("text/csv"))
			.exchange()
			.expectStatus().isOk()
			.expectHeader().contentTypeCompatibleWith("text/csv")
			.expectHeader().valueMatches("Content-Disposition", "attachment; filename=\"rapport-" + RUN_ID + ".csv\"")
			.expectBody(byte[].class)
			.returnResult()
			.getResponseBody();

		assertThat(new String(csv, StandardCharsets.UTF_8)).startsWith("﻿Källa;").contains("\"Steg Ärende: fel\"");
	}

	@Test
	void migrateItem() {
		final var line = new ReportLine(Source.ALKOL, Kind.ANMALAN, "12-FOLKOL", null, "Butik", Outcome.SKULLE_MIGRERAS, "", null, null);
		when(service.migrateItem(MUNICIPALITY_ID, new ItemKey(Source.ALKOL, Kind.ANMALAN, "12-FOLKOL"), true)).thenReturn(line);
		when(service.migrateItem(MUNICIPALITY_ID, new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), false)).thenReturn(line);

		webTestClient.post().uri("/{municipalityId}/migrations/items/ALKOL/ANMALAN/12-FOLKOL", MUNICIPALITY_ID).exchange().expectStatus().isOk().expectBody(ReportLine.class).isEqualTo(line);
		webTestClient.post().uri("/{municipalityId}/migrations/items/ALKT/TILLSTAND/1?dryRun=false", MUNICIPALITY_ID).exchange().expectStatus().isOk();
		webTestClient.post().uri("/{municipalityId}/migrations/items/ALKT/TILLSTAND/{sourceId}", MUNICIPALITY_ID, "1' OR '1'='1").exchange().expectStatus().isBadRequest();
		webTestClient.post().uri("/{municipalityId}/migrations/items/OKAND/TILLSTAND/1", MUNICIPALITY_ID).exchange().expectStatus().isEqualTo(BAD_REQUEST);

		verify(service).migrateItem(MUNICIPALITY_ID, new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), false);
	}

	@Test
	void items() {
		final var item = new MigrationItem(Source.ALKT, Kind.TILLSTAND, "1", null, ItemStatus.KLAR, null, null, null, null, "2281", null, null, null, null, null, null, null);
		when(service.getItems(MUNICIPALITY_ID, Source.ALKT, null, ItemStatus.KLAR)).thenReturn(List.of(item));
		when(service.getItem(MUNICIPALITY_ID, new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"))).thenReturn(item);

		webTestClient.get().uri("/{municipalityId}/migrations/items?source=ALKT&status=KLAR", MUNICIPALITY_ID).exchange().expectStatus().isOk().expectBodyList(MigrationItem.class).hasSize(1);
		webTestClient.get().uri("/{municipalityId}/migrations/items/ALKT/TILLSTAND/1", MUNICIPALITY_ID).exchange().expectStatus().isOk().expectBody(MigrationItem.class).isEqualTo(item);
	}

	private static Run run(final RunStatus status) {
		return new Run(RUN_ID, MUNICIPALITY_ID, true, false, List.of("ALKT"), List.of("TILLSTAND"), null, null, null, status, null, OffsetDateTime.parse("2026-10-12T10:00:00+02:00"), null, Map.of(Outcome.MIGRERAD, 1L));
	}
}
