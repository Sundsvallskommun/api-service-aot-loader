package se.sundsvall.aotloader.apptest;

import com.github.tomakehurst.wiremock.client.MappingBuilder;
import com.github.tomakehurst.wiremock.http.Fault;
import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Stream;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import se.sundsvall.aotloader.Application;
import se.sundsvall.aotloader.api.model.MigrationItem;
import se.sundsvall.aotloader.api.model.ReportLine;
import se.sundsvall.aotloader.api.model.ReviewExceptionRequest;
import se.sundsvall.aotloader.api.model.Run;
import se.sundsvall.aotloader.api.model.RunRequest;
import se.sundsvall.aotloader.service.model.ExceptionOutcome;
import se.sundsvall.aotloader.service.model.ItemStatus;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.RunStatus;
import se.sundsvall.aotloader.service.model.Step;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.getRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.matchingJsonPath;
import static com.github.tomakehurst.wiremock.client.WireMock.okJson;
import static com.github.tomakehurst.wiremock.client.WireMock.patch;
import static com.github.tomakehurst.wiremock.client.WireMock.patchRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.awaitility.Awaitility.await;

/**
 * Runs the migration against a SQL Server with made up rows of both source databases, a file area of made up files and
 * the target systems in WireMock. The tests build on each other, in the order of their names.
 */
@WireMockAppTestSuite(files = "classpath:/MigrationIT/", classes = Application.class, sharedContext = false)
class MigrationIT extends AbstractAppTest {

	private static final String MUNICIPALITY_ID = "2281";
	private static final String SM = "/support-management/2281/ALKT";
	private static final String SM_TIMRA = "/support-management/2262/ALKT";
	private static final String TIMRA_ERRAND_ID = "11111111-aaaa-bbbb-cccc-000000000004";
	private static final String PERMIT_ERRAND_ID = "11111111-aaaa-bbbb-cccc-000000000001";
	private static final String INSPECTION_ERRAND_ID = "11111111-aaaa-bbbb-cccc-000000000002";
	private static final String DRAFT_ERRAND_ID = "11111111-aaaa-bbbb-cccc-000000000003";
	private static final String HOLDER_PARTY_ID = "22222222-aaaa-bbbb-cccc-000000000001";
	private static final String PERSON_PARTY_ID = "22222222-aaaa-bbbb-cccc-000000000002";
	private static final Path FILE_AREA = createFileArea();
	private static final Map<String, String> FILE_AREA_BEFORE = snapshot(FILE_AREA);
	private static String dryRunId;

	static {
		SourceDatabaseContainer.start();
	}

	@DynamicPropertySource
	static void properties(final DynamicPropertyRegistry registry) {
		registry.add("aot-loader.databases.alkt.url", () -> SourceDatabaseContainer.url(SourceDatabaseContainer.ALKT_DATABASE));
		registry.add("aot-loader.databases.alkt.username", SourceDatabaseContainer::username);
		registry.add("aot-loader.databases.alkt.password", SourceDatabaseContainer::password);
		registry.add("aot-loader.databases.alkol.url", () -> SourceDatabaseContainer.url(SourceDatabaseContainer.ALKOL_DATABASE));
		registry.add("aot-loader.databases.alkol.username", SourceDatabaseContainer::username);
		registry.add("aot-loader.databases.alkol.password", SourceDatabaseContainer::password);
		registry.add("aot-loader.file-area.alkt.documents", () -> FILE_AREA.resolve("alkt-dokument").toString());
		registry.add("aot-loader.file-area.alkt.attachments", () -> FILE_AREA.resolve("alkt-bilder").toString());
		registry.add("aot-loader.file-area.alkol.documents", () -> FILE_AREA.resolve("alkol").toString());
		registry.add("aot-loader.file-area.alkol.attachments", () -> FILE_AREA.resolve("alkol").toString());
	}

	@BeforeEach
	void resetWireMock() {
		wiremock.resetAll();
		wiremock.stubFor(post(urlEqualTo("/api-gateway/token"))
			.willReturn(okJson("{\"access_token\":\"token\",\"token_type\":\"Bearer\",\"expires_in\":3600}")));
		wiremock.stubFor(get(urlEqualTo(SM + "/metadata/labels")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBodyFile("common/responses/labels.json")));
		wiremock.stubFor(get(urlEqualTo(SM_TIMRA + "/metadata/labels")).willReturn(aResponse().withHeader("Content-Type", "application/json").withBodyFile("common/responses/labels.json")));
	}

	@Test
	void test01_aDryRunOfEverythingReportsAndWritesNothing() {
		stubParty();

		final var started = restTemplate.postForEntity("/{municipalityId}/migrations/runs", new HttpEntity<>(new RunRequest(null, null, null, null), jsonHeaders()), Run.class, MUNICIPALITY_ID);

		assertThat(started.getStatusCode()).isEqualTo(HttpStatus.ACCEPTED);
		assertThat(started.getHeaders().getLocation()).hasPath("/2281/migrations/runs/" + started.getBody().id());
		assertThat(started.getBody().dryRun()).isTrue();
		dryRunId = started.getBody().id();
		final var run = awaitRun(dryRunId);
		assertThat(run.status()).isEqualTo(RunStatus.COMPLETED);
		assertThat(run.message()).isNull();

		final var report = report(dryRunId);
		assertThat(report).extracting(line -> line.source() + "/" + line.kind() + "/" + line.sourceId() + "=" + line.outcome()).containsExactlyInAnyOrder(
			"ALKT/TILLSTAND/1=SKULLE_MIGRERAS",
			"ALKT/TILLSTAND/2=GRANSKAS",
			"ALKT/TILLSYN/700=SKULLE_MIGRERAS",
			"ALKOL/TILLSTAND/1=SKULLE_MIGRERAS",
			"ALKOL/ANMALAN/1-FOLKOL=SKULLE_MIGRERAS",
			"ALKOL/ANMALAN/2-FOLKOL=SKULLE_MIGRERAS");
		assertThat(line(report, Source.ALKOL, Kind.ANMALAN, "2-FOLKOL").detail()).startsWith("Kommun 2262 (område Timrå)");
		assertThat(line(report, Source.ALKT, Kind.TILLSTAND, "1")).satisfies(line -> {
			assertThat(line.rules()).isEqualTo("G14");
			assertThat(line.detail()).isEqualTo("G14 1 fil saknas på filarean (saknas.pdf)");
			assertThat(line.placeName()).isEqualTo("Krogen Ett");
			assertThat(line.placeNumber()).isEqualTo("12345678");
		});
		assertThat(line(report, Source.ALKT, Kind.TILLSTAND, "2")).satisfies(line -> {
			assertThat(line.rules()).isEqualTo("G2,G13");
			assertThat(line.detail()).startsWith("G2 Tillståndets ägare skiljer sig från serveringsställets nuvarande ägare: AgarID i tillståndet 11, på serveringsstället 10");
		});
		assertThat(line(report, Source.ALKT, Kind.TILLSYN, "700").detail()).isEqualTo("Relation till tillståndsärendet ALKT/TILLSTAND/1");
		assertThat(report).allSatisfy(line -> assertThat(line.toString()).doesNotContain("556677", "191212", "Tolvan", "Testbolaget"));

		assertNothingWritten();
		assertNoUnmatchedRequests();
	}

	@Test
	void test02_aPermitIsMigrated() {
		stubParty();
		stubFindErrand("ALKT-TILLSTAND-1");
		stubCreateErrand(PERMIT_ERRAND_ID);
		wiremock.stubFor(get(urlEqualTo(SM + "/errands/" + PERMIT_ERRAND_ID + "/attachments")).willReturn(okJson("[]")));
		wiremock.stubFor(post(urlEqualTo(SM + "/errands/" + PERMIT_ERRAND_ID + "/attachments")).willReturn(aResponse().withStatus(201).withHeader("Location", "/2281/ALKT/errands/" + PERMIT_ERRAND_ID + "/attachments/x")));
		wiremock.stubFor(get(urlPathEqualTo("/party-assets/2281/assets")).withQueryParam("partyId", equalTo(HOLDER_PARTY_ID)).withQueryParam("assetId", equalTo(PERMIT_ERRAND_ID)).willReturn(okJson("[]")));
		wiremock.stubFor(post(urlPathEqualTo("/party-assets/2281/assets"))
			.withQueryParam("sourceReference", equalTo("LINK|" + PERMIT_ERRAND_ID + ";case;supportmanagement;ALKT|"))
			.willReturn(aResponse().withStatus(201).withHeader("Location", "/2281/assets/asset-internal-1")));
		stubActivation(PERMIT_ERRAND_ID);

		final var line = migrateItem("ALKT/TILLSTAND/1", false);

		assertThat(line.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(line.errandNumber()).isEqualTo("ALKT-2026-" + PERMIT_ERRAND_ID.substring(PERMIT_ERRAND_ID.length() - 1));
		assertThat(line.detail()).isEqualTo("G14 1 fil saknas på filarean (saknas.pdf)");

		wiremock.verify(1, postRequestedFor(urlEqualTo(SM + "/errands"))
			.withRequestBody(matchingJsonPath("$.lifecycle", equalTo("DRAFT")))
			.withRequestBody(matchingJsonPath("$.status", equalTo("SOLVED")))
			.withRequestBody(matchingJsonPath("$.classification.category", equalTo("PERMIT")))
			.withRequestBody(matchingJsonPath("$.classification.type", equalTo("SERVING_PERMIT")))
			.withRequestBody(matchingJsonPath("$.channel", equalTo("MIGRATION")))
			.withRequestBody(matchingJsonPath("$.reporterUserId", equalTo("alkt-migration")))
			.withRequestBody(matchingJsonPath("$.labels[?(@.id == 'label-migrated')]"))
			.withRequestBody(matchingJsonPath("$.labels[?(@.id == 'label-permanent-serving')]"))
			.withRequestBody(matchingJsonPath("$.externalTags[?(@.key == 'migreringsid' && @.value == 'ALKT-TILLSTAND-1')]"))
			.withRequestBody(matchingJsonPath("$.externalTags[?(@.key == 'diarienummer' && @.value == 'IAN-2019-1')]"))
			.withRequestBody(matchingJsonPath("$.stakeholders[?(@.role == 'PRIMARY' && @.externalId == '" + HOLDER_PARTY_ID + "' && @.externalIdType == 'ENTERPRISE')]"))
			// The place is parameters of the errand under the keys pw-alkt reads
			.withRequestBody(matchingJsonPath("$.parameters[?(@.key == 'premisesName')].values[0]", equalTo("Krogen Ett")))
			.withRequestBody(matchingJsonPath("$.parameters[?(@.key == 'premisesStreetAddress')].values[0]", equalTo("Storgatan 1")))
			.withRequestBody(matchingJsonPath("$.parameters[?(@.key == 'premisesPostalCode')].values[0]", equalTo("85230")))
			.withRequestBody(matchingJsonPath("$.parameters[?(@.key == 'premisesPostalArea')].values[0]", equalTo("Sundsvall")))
			.withRequestBody(matchingJsonPath("$.parameters[?(@.key == 'restaurantNumber')].values[0]", equalTo("12345678")))
			.withRequestBody(matchingJsonPath("$.stakeholders.length()", equalTo("2")))
			.withRequestBody(matchingJsonPath("$.stakeholders[?(@.role == 'CONTACT' && @.externalId == '" + PERSON_PARTY_ID + "' && @.firstName == 'Tolvan Tolvansson')]"))
			.withRequestBody(matchingJsonPath("$.parameters[?(@.key == 'villkor')].values[0]", equalTo("Krav på bordsservering: Mat ska serveras"))));
		final var created = wiremock.findAll(postRequestedFor(urlEqualTo(SM + "/errands"))).getFirst().getBodyAsString();
		final List<Map<String, Object>> personParameters = JsonPath.read(created, "$.stakeholders[?(@.externalId == '" + PERSON_PARTY_ID + "')].parameters[*]");
		assertThat(personParameters).extracting(parameter -> parameter.get("key"), parameter -> parameter.get("values")).containsExactly(
			tuple("PBI", List.of("true")),
			tuple("befattning", List.of("Verkställande direktör")));
		final var uploads = wiremock.findAll(postRequestedFor(urlEqualTo(SM + "/errands/" + PERMIT_ERRAND_ID + "/attachments")));
		assertThat(uploads).extracting(request -> request.getPart("errandAttachment").getFileName())
			.containsExactlyInAnyOrder("11111111.rtf", "22222222.rtf", "ansokan.pdf", "skylt.jpg");
		wiremock.verify(1, postRequestedFor(urlPathEqualTo("/party-assets/2281/assets"))
			.withHeader("X-Sent-By", equalTo("alkt-migration; type=migration"))
			.withRequestBody(matchingJsonPath("$.assetId", equalTo(PERMIT_ERRAND_ID)))
			.withRequestBody(matchingJsonPath("$.partyId", equalTo(HOLDER_PARTY_ID)))
			.withRequestBody(matchingJsonPath("$.type", equalTo("AlcoholServingPermit")))
			.withRequestBody(matchingJsonPath("$.status", equalTo("ACTIVE")))
			.withRequestBody(matchingJsonPath("$.origin", equalTo("SUPPORTMANAGEMENT")))
			.withRequestBody(matchingJsonPath("$.issued", equalTo("2019-05-01")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.errandId", equalTo(PERMIT_ERRAND_ID)))
			// The keys of the AoT solution, so that migrated and new permits read alike
			.withRequestBody(matchingJsonPath("$.additionalParameters.premisesRestaurantNumber", equalTo("12345678")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.premisesName", equalTo("Krogen Ett")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.premisesPostalAddress", equalTo("85230 Sundsvall")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.permitHolderName", equalTo("Testbolaget AB")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.permitHolderOrgNumber", equalTo("556677-8899")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.servingToPublic", equalTo("true")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.servingToClosedCompany", equalTo("false")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.scopeAllYear", equalTo("true")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.beverageBeer", equalTo("true")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.beverageSpirits", equalTo("false")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.servingHours", equalTo("11:00–01:00")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.conditions", equalTo("Krav på bordsservering: Mat ska serveras")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.caseNumber", equalTo("IAN-2019-1")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.decisionDateAndCaseNumber", equalTo("2019-05-01, IAN-2019-1")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.migreradFran", equalTo("AlkT"))));
		wiremock.verify(1, patchRequestedFor(urlEqualTo(SM + "/errands/" + PERMIT_ERRAND_ID)).withRequestBody(equalToJson("{\"lifecycle\":\"ACTIVE\"}")));
		assertEverySupportManagementCallKeepsProcessesAndNotificationsAway();
		assertNoUnmatchedRequests();

		final var item = restTemplate.getForObject("/{municipalityId}/migrations/items/ALKT/TILLSTAND/1", MigrationItem.class, MUNICIPALITY_ID);
		assertThat(item.status()).isEqualTo(ItemStatus.KLAR);
		assertThat(item.errandId()).isEqualTo(PERMIT_ERRAND_ID);
		assertThat(item.assetId()).isEqualTo(PERMIT_ERRAND_ID);
		assertThat(item.partyAssetsId()).isEqualTo("asset-internal-1");
	}

	@Test
	void test03_migratingThePermitAgainChangesNothing() {
		final var line = migrateItem("ALKT/TILLSTAND/1", false);

		assertThat(line.outcome()).isEqualTo(Outcome.REDAN_MIGRERAD);
		assertNothingWritten();
		assertNoUnmatchedRequests();
	}

	@Test
	void test04_anInspectionErrandIsMigratedWithItsRelation() {
		stubParty();
		stubFindErrand("ALKT-TILLSYN-700");
		stubCreateErrand(INSPECTION_ERRAND_ID);
		wiremock.stubFor(get(urlPathEqualTo("/relation/2281/relations"))
			.withQueryParam("filter", equalTo("source.resourceId:'" + PERMIT_ERRAND_ID + "' and target.resourceId:'" + INSPECTION_ERRAND_ID + "'"))
			.willReturn(okJson("{\"relations\":[]}")));
		wiremock.stubFor(post(urlEqualTo("/relation/2281/relations")).willReturn(aResponse().withStatus(201).withHeader("Location", "/2281/relations/relation-1")));
		stubActivation(INSPECTION_ERRAND_ID);

		final var line = migrateItem("ALKT/TILLSYN/700", false);

		assertThat(line.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(line.detail()).isEqualTo("Relation till tillståndsärendet ALKT/TILLSTAND/1");
		// Created without referredFrom: the migration writes the relation itself (section 7.7.1)
		wiremock.verify(1, postRequestedFor(urlEqualTo(SM + "/errands"))
			.withRequestBody(matchingJsonPath("$.labels[?(@.id == 'label-alcohol-inspection')]"))
			.withRequestBody(matchingJsonPath("$.labels[?(@.id == 'label-internal-inspection')]"))
			.withRequestBody(matchingJsonPath("$.stakeholders[?(@.role == 'PRIMARY' && @.externalId == '" + HOLDER_PARTY_ID + "')]")));
		wiremock.verify(1, postRequestedFor(urlEqualTo("/relation/2281/relations"))
			.withRequestBody(equalToJson("""
				{
				  "type": "LINK",
				  "source": {"resourceId": "%s", "type": "case", "service": "supportmanagement", "namespace": "ALKT"},
				  "target": {"resourceId": "%s", "type": "case", "service": "supportmanagement", "namespace": "ALKT"}
				}""".formatted(PERMIT_ERRAND_ID, INSPECTION_ERRAND_ID))));
		wiremock.verify(0, anyRequestedFor(urlMatching("/party-assets/.*")));
		assertEverySupportManagementCallKeepsProcessesAndNotificationsAway();
		assertNoUnmatchedRequests();

		final var item = restTemplate.getForObject("/{municipalityId}/migrations/items/ALKT/TILLSYN/700", MigrationItem.class, MUNICIPALITY_ID);
		assertThat(item.status()).isEqualTo(ItemStatus.KLAR);
		assertThat(item.relationId()).isEqualTo("relation-1");
	}

	@Test
	void test05_theExceptionListLetsAnItemThrough() {
		stubParty();
		final var exception = new HttpEntity<>(new ReviewExceptionRequest(ExceptionOutcome.MIGRERA, "handlaggare", null, "Ägarbytet är utrett"), jsonHeaders());
		final var put = restTemplate.exchange("/{municipalityId}/migrations/exceptions/ALKT/TILLSTAND/2", HttpMethod.PUT, exception, String.class, MUNICIPALITY_ID);
		assertThat(put.getStatusCode()).isEqualTo(HttpStatus.OK);

		final var line = migrateItem("ALKT/TILLSTAND/2", true);

		assertThat(line.outcome()).isEqualTo(Outcome.SKULLE_MIGRERAS);
		assertThat(line.detail()).contains("Undantagslistan: MIGRERA");
		assertNothingWritten();
	}

	@Test
	void test06_theReportAsCsv() {
		final var headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.valueOf("text/csv")));

		final var csv = restTemplate.exchange("/{municipalityId}/migrations/runs/{runId}/report.csv", HttpMethod.GET, new HttpEntity<>(headers), byte[].class, MUNICIPALITY_ID, dryRunId);

		assertThat(csv.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(new String(csv.getBody(), StandardCharsets.UTF_8))
			.startsWith("﻿Källa;Sort;Käll-id;Serveringsställenummer;Ställe;Utfall;Regler;Detalj;Ärendenummer")
			.contains("\"ALKT\";\"TILLSTAND\";\"2\";\"12345679\";\"Krogen Två\";\"GRANSKAS\";\"G2,G13\"");
		final var runs = restTemplate.exchange("/{municipalityId}/migrations/runs", HttpMethod.GET, null, new ParameterizedTypeReference<List<Run>>() {}, MUNICIPALITY_ID).getBody();
		assertThat(runs).hasSize(5).allSatisfy(run -> assertThat(run.status()).isEqualTo(RunStatus.COMPLETED));
	}

	@Test
	void test08_theDraftOfAnInterruptedRunIsUpdatedAndAFileThatIsRefusedIsListed() {
		stubParty();
		wiremock.stubFor(get(urlPathEqualTo(SM + "/errands"))
			.withQueryParam("filter", equalTo("externalTags.key:'migreringsid' and externalTags.value:'ALKOL-TILLSTAND-1' and (lifecycle:'DRAFT' or lifecycle:'ACTIVE')"))
			.willReturn(okJson("""
				{"content": [{"id": "%s", "errandNumber": "ALKT-2026-3", "lifecycle": "DRAFT", "externalTags": [{"key": "migreringsid", "value": "ALKOL-TILLSTAND-1"}]}], "last": true}""".formatted(DRAFT_ERRAND_ID))));
		wiremock.stubFor(errand(patch(urlEqualTo(SM + "/errands/" + DRAFT_ERRAND_ID)), DRAFT_ERRAND_ID, "DRAFT"));
		wiremock.stubFor(errand(patch(urlEqualTo(SM + "/errands/" + DRAFT_ERRAND_ID)).withRequestBody(equalToJson("{\"lifecycle\":\"ACTIVE\"}")).atPriority(1), DRAFT_ERRAND_ID, "ACTIVE"));
		wiremock.stubFor(get(urlEqualTo(SM + "/errands/" + DRAFT_ERRAND_ID + "/attachments")).willReturn(okJson("[]")));
		wiremock.stubFor(post(urlEqualTo(SM + "/errands/" + DRAFT_ERRAND_ID + "/attachments"))
			.willReturn(aResponse().withStatus(400).withHeader("Content-Type", "application/problem+json").withBody("{\"title\":\"Bad Request\",\"status\":400,\"detail\":\"Filen tas inte emot\"}")));
		wiremock.stubFor(get(urlPathEqualTo("/party-assets/2281/assets")).withQueryParam("partyId", equalTo(HOLDER_PARTY_ID)).withQueryParam("assetId", equalTo(DRAFT_ERRAND_ID)).willReturn(okJson("[]")));
		wiremock.stubFor(post(urlPathEqualTo("/party-assets/2281/assets")).willReturn(aResponse().withStatus(201).withHeader("Location", "/2281/assets/asset-internal-3")));

		final var line = migrateItem("ALKOL/TILLSTAND/1", false);

		assertThat(line.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(line.detail()).isEqualTo("G14 1 fil togs inte emot av SupportManagement (33333333.rtf)");
		wiremock.verify(0, postRequestedFor(urlEqualTo(SM + "/errands")));
		wiremock.verify(1, postRequestedFor(urlEqualTo(SM + "/errands/" + DRAFT_ERRAND_ID + "/attachments")));
		final var draftUpdates = wiremock.findAll(patchRequestedFor(urlEqualTo(SM + "/errands/" + DRAFT_ERRAND_ID)).withRequestBody(matchingJsonPath("$.title")));
		// SupportManagement refuses a patch with a reporter
		assertThat(draftUpdates).singleElement().satisfies(request -> assertThat(request.getBodyAsString()).doesNotContain("\"reporterUserId\":\"").doesNotContain("\"lifecycle\":\""));
		wiremock.verify(1, patchRequestedFor(urlEqualTo(SM + "/errands/" + DRAFT_ERRAND_ID)).withRequestBody(equalToJson("{\"lifecycle\":\"ACTIVE\"}")));
		wiremock.verify(1, postRequestedFor(urlPathEqualTo("/party-assets/2281/assets"))
			.withRequestBody(matchingJsonPath("$.type", equalTo("TobaccoSalesPermit")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.premisesName", equalTo("Kiosken")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.salesRetail", equalTo("true")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.salesWholesale", equalTo("false")))
			.withRequestBody(matchingJsonPath("$.additionalParameters.caseNumber", equalTo("IAN-2019-100"))));
		assertEverySupportManagementCallKeepsProcessesAndNotificationsAway();
		assertNoUnmatchedRequests();
	}

	@Test
	void test09_aCreateWhoseAnswerIsLostIsNotSentAgain() {
		stubParty();
		stubFindErrand("ALKOL-ANMALAN-1-FOLKOL");
		wiremock.stubFor(post(urlEqualTo(SM + "/errands")).willReturn(aResponse().withFault(Fault.CONNECTION_RESET_BY_PEER)));

		final var line = migrateItem("ALKOL/ANMALAN/1-FOLKOL", false);

		assertThat(line.outcome()).isEqualTo(Outcome.FEL);
		assertThat(line.detail()).startsWith("Steg Ärende: ");
		wiremock.verify(1, postRequestedFor(urlEqualTo(SM + "/errands")));
		final var item = restTemplate.getForObject("/{municipalityId}/migrations/items/ALKOL/ANMALAN/1-FOLKOL", MigrationItem.class, MUNICIPALITY_ID);
		assertThat(item.status()).isEqualTo(ItemStatus.FEL);
		assertThat(item.failedStep()).isEqualTo(Step.ARENDE);
	}

	@Test
	void test10_aPlaceInTimråIsMigratedToItsMunicipality() {
		stubParty();
		wiremock.stubFor(get(urlPathEqualTo(SM_TIMRA + "/errands"))
			.withQueryParam("filter", equalTo("externalTags.key:'migreringsid' and externalTags.value:'ALKOL-ANMALAN-2-FOLKOL' and (lifecycle:'DRAFT' or lifecycle:'ACTIVE')"))
			.willReturn(okJson("{\"content\":[],\"last\":true}")));
		wiremock.stubFor(post(urlEqualTo(SM_TIMRA + "/errands")).willReturn(aResponse().withStatus(201).withHeader("Location", "/2262/ALKT/errands/" + TIMRA_ERRAND_ID)));
		wiremock.stubFor(errand(get(urlEqualTo(SM_TIMRA + "/errands/" + TIMRA_ERRAND_ID)), TIMRA_ERRAND_ID, "DRAFT"));
		wiremock.stubFor(errand(patch(urlEqualTo(SM_TIMRA + "/errands/" + TIMRA_ERRAND_ID)), TIMRA_ERRAND_ID, "ACTIVE"));
		wiremock.stubFor(get(urlPathEqualTo("/party-assets/2262/assets")).withQueryParam("partyId", equalTo(HOLDER_PARTY_ID)).withQueryParam("assetId", equalTo(TIMRA_ERRAND_ID)).willReturn(okJson("[]")));
		wiremock.stubFor(post(urlPathEqualTo("/party-assets/2262/assets")).willReturn(aResponse().withStatus(201).withHeader("Location", "/2262/assets/asset-internal-4")));

		final var line = migrateItem("ALKOL/ANMALAN/2-FOLKOL", false);

		assertThat(line.outcome()).isEqualTo(Outcome.MIGRERAD);
		assertThat(line.detail()).startsWith("Kommun 2262 (område Timrå)");
		wiremock.verify(1, postRequestedFor(urlEqualTo(SM_TIMRA + "/errands"))
			.withRequestBody(matchingJsonPath("$.labels[?(@.id == 'label-migrated')]"))
			.withRequestBody(matchingJsonPath("$.parameters[?(@.key == 'premisesName')].values[0]", equalTo("Timråkiosken")))
			.withRequestBody(matchingJsonPath("$.parameters[?(@.key == 'omrade')].values[0]", equalTo("Timrå"))));
		wiremock.verify(1, postRequestedFor(urlPathEqualTo("/party-assets/2262/assets")).withRequestBody(matchingJsonPath("$.type", equalTo("LowAlcoholBeerSalesPermit"))));
		wiremock.verify(1, patchRequestedFor(urlEqualTo(SM_TIMRA + "/errands/" + TIMRA_ERRAND_ID)).withRequestBody(equalToJson("{\"lifecycle\":\"ACTIVE\"}")));
		assertThat(wiremock.findAll(anyRequestedFor(urlMatching("/(support-management|party-assets|relation|party)/2281/.*"))))
			.as("Nothing of the place in Timrå may reach the municipality of the run")
			.allSatisfy(request -> assertThat(request.getUrl()).isEqualTo(SM + "/metadata/labels"));
		assertNoUnmatchedRequests();

		final var item = restTemplate.getForObject("/{municipalityId}/migrations/items/ALKOL/ANMALAN/2-FOLKOL", MigrationItem.class, MUNICIPALITY_ID);
		assertThat(item.status()).isEqualTo(ItemStatus.KLAR);
		assertThat(item.municipalityId()).isEqualTo("2262");
		assertThat(item.errandId()).isEqualTo(TIMRA_ERRAND_ID);
	}

	@Test
	void test11_theFileAreaIsUnchanged() {
		assertThat(snapshot(FILE_AREA)).isEqualTo(FILE_AREA_BEFORE);
	}

	/**
	 * Migrates an item the way a person calling the API does, with the person as sender: the migration must still be the
	 * only sender the target systems see.
	 */
	private ReportLine migrateItem(final String item, final boolean dryRun) {
		final var headers = new HttpHeaders();
		headers.add("X-Sent-By", "joe01doe; type=adAccount");
		final var response = restTemplate.exchange("/{municipalityId}/migrations/items/" + item + "?dryRun={dryRun}", HttpMethod.POST, new HttpEntity<>(headers), ReportLine.class, MUNICIPALITY_ID, dryRun);
		assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
		assertThat(wiremock.findAll(anyRequestedFor(urlMatching("/(support-management|party-assets|relation)/.*"))))
			.allSatisfy(request -> assertThat(request.getHeaders().getHeader("X-Sent-By").values()).containsExactly("alkt-migration; type=migration"));
		return response.getBody();
	}

	private Run awaitRun(final String runId) {
		await().atMost(Duration.ofSeconds(60)).pollInterval(Duration.ofMillis(200))
			.until(() -> restTemplate.getForObject("/{municipalityId}/migrations/runs/{runId}", Run.class, MUNICIPALITY_ID, runId).status() != RunStatus.RUNNING);
		return restTemplate.getForObject("/{municipalityId}/migrations/runs/{runId}", Run.class, MUNICIPALITY_ID, runId);
	}

	private List<ReportLine> report(final String runId) {
		return restTemplate.exchange("/{municipalityId}/migrations/runs/{runId}/report", HttpMethod.GET, null, new ParameterizedTypeReference<List<ReportLine>>() {}, MUNICIPALITY_ID, runId).getBody();
	}

	private static ReportLine line(final List<ReportLine> report, final Source source, final Kind kind, final String sourceId) {
		return report.stream().filter(line -> line.source() == source && line.kind() == kind && line.sourceId().equals(sourceId)).findFirst().orElseThrow();
	}

	private void stubParty() {
		wiremock.stubFor(get(urlMatching("/party/(2281|2262)/ENTERPRISE/5566778899/partyId")).willReturn(aResponse().withHeader("Content-Type", "text/plain").withBody(HOLDER_PARTY_ID)));
		wiremock.stubFor(get(urlMatching("/party/(2281|2262)/PRIVATE/191212121212/partyId")).willReturn(aResponse().withHeader("Content-Type", "text/plain").withBody(PERSON_PARTY_ID)));
	}

	private void stubFindErrand(final String migrationId) {
		wiremock.stubFor(get(urlPathEqualTo(SM + "/errands"))
			.withQueryParam("filter", equalTo("externalTags.key:'migreringsid' and externalTags.value:'" + migrationId + "' and (lifecycle:'DRAFT' or lifecycle:'ACTIVE')"))
			.withQueryParam("page", equalTo("0"))
			.withQueryParam("size", equalTo("100"))
			.willReturn(okJson("{\"content\":[],\"last\":true}")));
	}

	private void stubCreateErrand(final String errandId) {
		wiremock.stubFor(post(urlEqualTo(SM + "/errands")).willReturn(aResponse().withStatus(201).withHeader("Location", "/2281/ALKT/errands/" + errandId)));
		wiremock.stubFor(errand(get(urlEqualTo(SM + "/errands/" + errandId)), errandId, "DRAFT"));
	}

	private void stubActivation(final String errandId) {
		wiremock.stubFor(errand(patch(urlEqualTo(SM + "/errands/" + errandId)), errandId, "ACTIVE"));
	}

	private static MappingBuilder errand(final MappingBuilder request, final String errandId, final String lifecycle) {
		return request.willReturn(okJson("{\"id\":\"%s\",\"errandNumber\":\"ALKT-2026-%s\",\"lifecycle\":\"%s\"}".formatted(errandId, errandId.substring(errandId.length() - 1), lifecycle)));
	}

	private void assertNothingWritten() {
		assertThat(wiremock.findAll(anyRequestedFor(urlMatching("/(support-management|party-assets|relation)/.*"))))
			.as("Only reads may reach the target systems")
			.allSatisfy(request -> assertThat(request.getMethod().getName()).isEqualTo("GET"));
		wiremock.verify(1, getRequestedFor(urlEqualTo(SM + "/metadata/labels")));
	}

	private void assertEverySupportManagementCallKeepsProcessesAndNotificationsAway() {
		final List<LoggedRequest> requests = wiremock.findAll(anyRequestedFor(urlMatching("/support-management/.*")));
		assertThat(requests).isNotEmpty().allSatisfy(request -> {
			assertThat(request.getHeader("X-Trigger-Process")).isEqualTo("false");
			assertThat(request.getHeader("X-notify")).isEqualTo("false");
			assertThat(request.getHeader("X-Sent-By")).isEqualTo("alkt-migration; type=migration");
			assertThat(request.getUrl()).doesNotContain("communication", "conversations", "decisions", "referredFrom");
		});
	}

	private void assertNoUnmatchedRequests() {
		assertThat(wiremock.findAllUnmatchedRequests()).extracting(LoggedRequest::getUrl).isEmpty();
	}

	private static HttpHeaders jsonHeaders() {
		final var headers = new HttpHeaders();
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}

	private static Path createFileArea() {
		try {
			final var root = Files.createTempDirectory("aot-loader-filarea");
			Files.createDirectories(root.resolve("alkt-dokument"));
			Files.createDirectories(root.resolve("alkt-bilder"));
			Files.createDirectories(root.resolve("alkol"));
			Files.writeString(root.resolve("alkt-dokument").resolve("11111111.rtf"), "{\\rtf1 Dokument}");
			Files.writeString(root.resolve("alkt-dokument").resolve("22222222.rtf"), "{\\rtf1 Beslut}");
			Files.writeString(root.resolve("alkt-bilder").resolve("ansokan.pdf"), "%PDF-1.4 ansökan");
			Files.writeString(root.resolve("alkt-bilder").resolve("skylt.jpg"), "bild");
			Files.writeString(root.resolve("alkol").resolve("33333333.rtf"), "{\\rtf1 Tobak}");
			root.toFile().deleteOnExit();
			return root;
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static Map<String, String> snapshot(final Path root) {
		try (Stream<Path> files = Files.walk(root)) {
			final var snapshot = new TreeMap<String, String>();
			files.forEach(path -> {
				try {
					snapshot.put(root.relativize(path).toString(), Files.isDirectory(path) ? "dir" : Files.size(path) + "/" + Files.getLastModifiedTime(path).toMillis());
				} catch (final IOException e) {
					throw new UncheckedIOException(e);
				}
			});
			return snapshot;
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}
}
