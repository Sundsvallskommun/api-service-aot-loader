package se.sundsvall.aotloader.apptest;

import com.github.tomakehurst.wiremock.common.Json;
import com.github.tomakehurst.wiremock.matching.RequestPatternBuilder;
import com.jayway.jsonpath.JsonPath;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import se.sundsvall.aotloader.Application;
import se.sundsvall.aotloader.api.model.Run;
import se.sundsvall.aotloader.service.model.RunStatus;
import se.sundsvall.dept44.test.AbstractAppTest;
import se.sundsvall.dept44.test.annotation.wiremock.WireMockAppTestSuite;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.anyRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.havingExactly;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.urlMatching;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static org.springframework.http.HttpHeaders.LOCATION;
import static org.springframework.http.HttpMethod.GET;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.http.HttpMethod.PUT;
import static org.springframework.http.HttpStatus.ACCEPTED;
import static org.springframework.http.HttpStatus.OK;
import static org.springframework.http.HttpStatus.UNPROCESSABLE_CONTENT;

/**
 * Runs the migration against a SQL Server with made up rows of both source databases, a file area of made up files and
 * the target systems in WireMock. Each test finds the calls it allows to the target systems in its own mappings, and a
 * call that matches none of them fails the test; the mappings require the headers that keep processes and notifications
 * away. The tests build on each other, in the order of their names.
 */
@WireMockAppTestSuite(files = "classpath:/MigrationIT/", classes = Application.class, sharedContext = false)
@ExtendWith(OutputCaptureExtension.class)
class MigrationIT extends AbstractAppTest {

	private static final String PATH = "/2281/migrations";
	private static final String SENT_BY = "alkt-migration; type=migration";
	private static final String PERMIT_ERRAND_ID = "11111111-aaaa-bbbb-cccc-000000000001";
	private static final String INTERRUPTED_ERRAND_ID = "11111111-aaaa-bbbb-cccc-000000000006";
	private static final String VISIT_ERRAND_ID = "11111111-aaaa-bbbb-cccc-000000000010";
	private static final Pattern LOG_EVENT = Pattern.compile("(?m)^(?=\\d{4}-\\d{2}-\\d{2} \\d{2}:\\d{2}:\\d{2},\\d{3} \\[)");
	private static final Path FILE_AREA = createFileArea();
	private static final Map<String, String> FILE_AREA_BEFORE = snapshot(FILE_AREA);
	private static String dryRunId;
	private static String assetOfTheInterruptedPermit;

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
	}

	@AfterEach
	void noPersonalDataReachesTheLog(final CapturedOutput output) {
		final var events = LOG_EVENT.split(output.getAll());
		assertThat(events).as("The log is split into its events").hasSizeGreaterThan(1);
		// WireMock logs the calls the way the target systems receive them, which is not the log of the migration
		final var log = Stream.of(events)
			.filter(event -> !event.lines().findFirst().orElse("").contains(" WireMock."))
			.collect(Collectors.joining());
		assertThat(log).doesNotContain("191212121212", "1212121212", "121212-1212", "5566778899", "556677-8899", "Tolvan", "Testbolaget", "Förra ägaren", "Kontakt Person", "Avgången");
		// The free text of the visits may name persons as well
		assertThat(log).doesNotContain("Mycket folk", "Ungdomar i baren", "Köpet nekades");
	}

	/**
	 * Checks that every mapping of the test was called with all it requires — method, headers and body — and then what
	 * {@link AbstractAppTest#verifyStubs()} checks: that no call went unmatched.
	 */
	@Override
	public AbstractAppTest verifyStubs() {
		// AbstractAppTest compares the URL only, so a PATCH of an errand would pass for the GET of the same URL
		wiremock.getStubMappings().forEach(stub -> assertThat(wiremock.findAll(RequestPatternBuilder.like(stub.getRequest())))
			.as("Calls that match the whole mapping %s", stub.getName())
			.isNotEmpty());
		return super.verifyStubs();
	}

	@Test
	void test01_aDryRunOfEverythingReportsAndWritesNothing() {
		final var location = setupCall()
			.withServicePath(PATH + "/runs")
			.withHttpMethod(POST)
			.withRequest("request.json")
			.withExpectedResponseStatus(ACCEPTED)
			.withExpectedResponseHeader(LOCATION, List.of("^" + PATH + "/runs/[0-9a-f-]{36}$"))
			.withExpectedResponse("response.json")
			.sendRequest()
			.getResponseHeaders().getLocation();
		dryRunId = lastSegmentOf(location);
		andVerifyThat(() -> isDone(dryRunId));
		// Only reads have mappings, so a write would be an unmatched request
		verifyStubs();

		setupCall()
			.withServicePath(location.getPath())
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("run.json")
			.sendRequest();
		setupCall()
			.withServicePath(location.getPath() + "/report")
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("report.json")
			.sendRequest();
	}

	@Test
	void test02_aPermitIsMigrated() {
		migrateItem("ALKT/TILLSTAND/1", false);

		final var uploads = wiremock.findAll(postRequestedFor(urlPathEqualTo("/support-management/2281/AOT/errands/" + PERMIT_ERRAND_ID + "/attachments")));
		assertThat(uploads).extracting(request -> request.getPart("errandAttachment").getFileName())
			// skylt.jpg is not uploaded, since another place also refers to that name (decision 26)
			.containsExactlyInAnyOrder("11111111.rtf", "22222222.rtf", "ansokan.pdf", "55555555.rtf", "serveringsansvarig.pdf");
		assertThat(uploads).filteredOn(request -> "55555555.rtf".equals(request.getPart("errandAttachment").getFileName()))
			.singleElement()
			.extracting(request -> request.queryParameter("received").firstValue())
			.as("The file came in on the day of its event, the start of which is the evening before in UTC (decision 40)")
			.isEqualTo("2019-05-01T22:00:00Z");
		assertThat(wiremock.findAll(postRequestedFor(urlEqualTo("/party-assets/2281/assets/asset-internal-1/attachments"))))
			.as("The certificate and the decision of the issuing errand also go on the permit (decision 34)")
			.extracting(request -> request.getPart("attachment").getFileName(), request -> request.getPart("category").getBody().asString())
			.containsExactlyInAnyOrder(tuple("55555555.rtf", "Tillståndsbevis"), tuple("22222222.rtf", "Beslut"));
		final var created = wiremock.findAll(postRequestedFor(urlEqualTo("/support-management/2281/AOT/errands"))).getFirst().getBodyAsString();
		final List<Map<String, Object>> personParameters = JsonPath.read(created, "$.stakeholders[?(@.externalId == '22222222-aaaa-bbbb-cccc-000000000002')].parameters[*]");
		assertThat(personParameters).extracting(parameter -> parameter.get("key")).containsExactly("PBI", "befattning");
		// The holder's contact person is a contact of its own, without the parameter PBI
		final List<Map<String, Object>> contacts = JsonPath.read(created, "$.stakeholders[?(@.firstName == 'Kontakt Person')]");
		assertThat(contacts).singleElement().satisfies(contact -> {
			assertThat(contact).containsEntry("role", "CONTACT");
			assertThat(contact.get("externalId")).isNull();
			assertThat((List<?>) contact.get("parameters")).isNullOrEmpty();
		});
		verifyStubs();

		itemIs("ALKT/TILLSTAND/1");
	}

	@Test
	void test03_migratingThePermitAgainChangesNothing() {
		migrateItem("ALKT/TILLSTAND/1", false);

		verifyStubs();
	}

	@Test
	void test04_anInspectionErrandIsMigratedWithItsRelation() {
		migrateItem("ALKT/TILLSYN/700", false);

		assertThat(wiremock.findAll(anyRequestedFor(urlMatching("/party-assets/.*")))).as("An inspection errand has no permit").isEmpty();
		assertThat(investigationTimes("11111111-aaaa-bbbb-cccc-000000000002"))
			.as("The inspection started when the errand was opened and was completed when it was closed, in Swedish time (decision 41)")
			.containsExactly(Instant.parse("2020-12-31T23:00:00Z"), Instant.parse("2021-02-28T23:00:00Z"));
		verifyStubs();

		itemIs("ALKT/TILLSYN/700");
	}

	@Test
	void test05_aRelationThatExistsIsNotCreatedAgain() {
		migrateItem("ALKT/TILLSYN/701", false);

		assertThat(wiremock.findAll(postRequestedFor(urlMatching("/relation/.*")))).isEmpty();
		verifyStubs();

		itemIs("ALKT/TILLSYN/701");
	}

	@Test
	void test06_severalRelationsAreReportedAndNoneIsCreated() {
		migrateItem("ALKT/TILLSYN/702", false);

		assertThat(wiremock.findAll(postRequestedFor(urlMatching("/relation/.*")))).isEmpty();
		verifyStubs();

		itemIs("ALKT/TILLSYN/702");
	}

	@Test
	void test07_theExceptionListLetsAnItemThrough() {
		putException("ALKT/TILLSTAND/2");

		migrateItem("ALKT/TILLSTAND/2", true);

		verifyStubs();
	}

	@Test
	void test08_theReportAsCsv() {
		final var headers = new HttpHeaders();
		headers.setAccept(List.of(MediaType.valueOf("text/csv")));

		final var csv = restTemplate.exchange(PATH + "/runs/{runId}/report.csv?outcome=GRANSKAS", GET, new HttpEntity<>(headers), byte[].class, dryRunId);

		assertThat(csv.getStatusCode()).isEqualTo(OK);
		assertThat(new String(csv.getBody(), StandardCharsets.UTF_8)).isEqualToNormalizingNewlines(fromTestFile("test08_theReportAsCsv", "report.csv"));
	}

	@Test
	void test09_thePermitOfAnInterruptedRunGetsTheStatusTheExceptionListGives() {
		putException("ALKT/TILLSTAND/2");

		migrateItem("ALKT/TILLSTAND/2", false);

		// The mapping of the change takes nothing but the status, since the content of the post is what the source gives
		verifyStubs();

		itemIs("ALKT/TILLSTAND/2");
	}

	@Test
	void test10_theDraftOfAnInterruptedRunIsUpdatedAndAFileThatIsRefusedIsListed() {
		migrateItem("ALKOL/TILLSTAND/1", false);

		verifyStubs();
	}

	@Test
	void test11_aRealRunIsRefusedWhileAnActionIsActive() {
		final var call = setupCall()
			.withServicePath(PATH + "/items/ALKOL/ANMALAN/1-FOLKOL?dryRun=false")
			.withHttpMethod(POST)
			.withExpectedResponseStatus(UNPROCESSABLE_CONTENT)
			.withExpectedResponse("response.json");
		// The action configuration of the test, with an action that is active, takes the place of the common one
		wiremock.getStubMappings().stream()
			.filter(stub -> "support-management-action-config-2281".equals(stub.getName()))
			.toList()
			.forEach(wiremock::removeStubMapping);
		call.sendRequest();
		verifyStubs();
	}

	@Test
	void test12_aCreateWhoseAnswerIsLostIsNotSentAgain() {
		migrateItem("ALKOL/ANMALAN/1-FOLKOL", false);

		assertThat(wiremock.findAll(postRequestedFor(urlEqualTo("/support-management/2281/AOT/errands")))).hasSize(1);
		verifyStubs();

		itemIs("ALKOL/ANMALAN/1-FOLKOL");
	}

	@Test
	void test13_theRerunFindsTheDraftOfTheLostAnswer() {
		migrateItem("ALKOL/ANMALAN/1-FOLKOL", false);

		assertThat(wiremock.findAll(postRequestedFor(urlEqualTo("/support-management/2281/AOT/errands")))).isEmpty();
		verifyStubs();

		itemIs("ALKOL/ANMALAN/1-FOLKOL");
	}

	@Test
	void test14_aPermitInterruptedAtTheActivation() {
		migrateItem("ALKT/TILLSTAND/3", false);

		final var uploads = wiremock.findAll(postRequestedFor(urlPathEqualTo("/support-management/2281/AOT/errands/" + INTERRUPTED_ERRAND_ID + "/attachments")));
		assertThat(uploads).extracting(request -> request.getPart("errandAttachment").getFileName())
			.containsExactlyInAnyOrder("44444444.rtf", "ansökan räkenskaper.pdf", "ritning våning 2.pdf", "fasad.jpg", "Översiktskarta.pdf");
		assertThat(wiremock.findAll(postRequestedFor(urlEqualTo("/party-assets/2281/assets/asset-internal-6/attachments"))))
			.as("The drawing and the decision of the issuing errand also go on the permit (decisions 34 and 35), the drawing with the name shown of its purpose (decision 40)")
			.extracting(request -> request.getPart("attachment").getFileName(), request -> request.getPart("category").getBody().asString())
			.containsExactlyInAnyOrder(tuple("ritning våning 2.pdf", "Ritning på lokalen med serveringsytan markerad"), tuple("44444444.rtf", "Beslut"));
		assetOfTheInterruptedPermit = wiremock.findAll(postRequestedFor(urlPathEqualTo("/party-assets/2281/assets"))).getFirst().getBodyAsString();
		verifyStubs();

		itemIs("ALKT/TILLSTAND/3");
	}

	@Test
	void test15_theRerunContinuesFromTheIdsInTheStatusTable() {
		// The post as the interrupted run created it, which therefore needs no change
		final Map<String, Object> asset = JsonPath.read(assetOfTheInterruptedPermit, "$");
		asset.put("id", "asset-internal-6");
		wiremock.stubFor(get(urlEqualTo("/party-assets/2281/assets/asset-internal-6"))
			.withHeader("X-Sent-By", havingExactly(SENT_BY))
			.willReturn(aResponse().withHeader("Content-Type", "application/json").withBody(Json.write(asset))));

		migrateItem("ALKT/TILLSTAND/3", false);

		assertThat(wiremock.findAll(anyRequestedFor(urlMatching("/(support-management|party-assets|relation)/.*"))))
			.as("Nothing is created again: the errand, the files and the permit are found by the ids in the status table")
			.extracting(request -> request.getMethod().getName())
			.doesNotContain("POST");
		verifyStubs();

		itemIs("ALKT/TILLSTAND/3");
	}

	@Test
	void test16_aPlaceInTimråGetsItsMunicipalityAsParameter() {
		migrateItem("ALKOL/ANMALAN/2-FOLKOL", false);

		assertThat(wiremock.findAll(anyRequestedFor(urlMatching("/(support-management|party-assets|relation|party)/2262/.*"))))
			.as("The errand of the place in Timrå is created in the municipality of the run, with 2262 as a parameter (decision 19)")
			.isEmpty();
		verifyStubs();

		itemIs("ALKOL/ANMALAN/2-FOLKOL");
	}

	@Test
	void test17_aHolderUnknownToPartyIsReviewed() {
		migrateItem("ALKT/TILLSTAND/4", true);

		verifyStubs();
	}

	@Test
	void test18_anInspectionVisitIsMigrated() {
		migrateItem("ALKT/TILLSYNSBESOK/900", false);

		final var uploads = wiremock.findAll(postRequestedFor(urlPathEqualTo("/support-management/2281/AOT/errands/" + VISIT_ERRAND_ID + "/attachments")));
		assertThat(uploads).extracting(request -> request.getPart("errandAttachment").getFileName()).containsExactlyInAnyOrder("besöksrapport.pdf", "foto.jpg");
		assertThat(wiremock.findAll(anyRequestedFor(urlMatching("/(party|party-assets|relation)/.*"))))
			.as("A visit has no holder to look up, no permit and no relation")
			.isEmpty();
		assertThat(investigationTimes(VISIT_ERRAND_ID)).as("The visit from 21.30 to 23:00 in winter time")
			.containsExactly(Instant.parse("2023-11-04T20:30:00Z"), Instant.parse("2023-11-04T22:00:00Z"));
		verifyStubs();

		itemIs("ALKT/TILLSYNSBESOK/900");
	}

	@Test
	void test19_aVisitOfAlkolIsMigrated() {
		// AlkOL keeps the times of a visit as datetime, and its remarks have no note
		migrateItem("ALKOL/TILLSYNSBESOK/950", false);

		assertThat(wiremock.findAll(anyRequestedFor(urlMatching("/(party|party-assets|relation)/.*")))).isEmpty();
		assertThat(investigationTimes("11111111-aaaa-bbbb-cccc-000000000011")).as("The visit from 14:00 to 14:20 in summer time")
			.containsExactly(Instant.parse("2024-05-03T12:00:00Z"), Instant.parse("2024-05-03T12:20:00Z"));
		verifyStubs();

		itemIs("ALKOL/TILLSYNSBESOK/950");
	}

	@Test
	void test20_theFileAreaIsUnchanged() {
		assertThat(snapshot(FILE_AREA)).isEqualTo(FILE_AREA_BEFORE);
	}

	/**
	 * Migrates an item the way a person calling the API does, with the person as sender: the migration must still be the
	 * only sender the target systems see. The answer is compared with response.json of the test.
	 */
	private void migrateItem(final String item, final boolean dryRun) {
		setupCall()
			.withServicePath(PATH + "/items/" + item + "?dryRun=" + dryRun)
			.withHttpMethod(POST)
			.withHeader("X-Sent-By", "joe01doe; type=adAccount")
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("response.json")
			.sendRequest();
	}

	/**
	 * Compares the item in the status table with item.json of the test.
	 */
	private void itemIs(final String item) {
		setupCall()
			.withServicePath(PATH + "/items/" + item)
			.withHttpMethod(GET)
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("item.json")
			.sendRequest();
	}

	/**
	 * Puts exception.json of the test in the exception list and compares the answer with exception-response.json.
	 */
	private void putException(final String item) {
		setupCall()
			.withServicePath(PATH + "/exceptions/" + item)
			.withHttpMethod(PUT)
			.withRequest("exception.json")
			.withExpectedResponseStatus(OK)
			.withExpectedResponse("exception-response.json")
			.sendRequest();
	}

	private boolean isDone(final String runId) {
		return restTemplate.getForObject(PATH + "/runs/{runId}", Run.class, runId).status() != RunStatus.RUNNING;
	}

	private static String fromTestFile(final String test, final String name) {
		try (var stream = MigrationIT.class.getResourceAsStream("/MigrationIT/__files/" + test + "/" + name)) {
			return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
		} catch (final IOException e) {
			throw new UncheckedIOException(e);
		}
	}

	private static String lastSegmentOf(final URI location) {
		final var path = location.getPath();
		return path.substring(path.lastIndexOf('/') + 1);
	}

	private static Path createFileArea() {
		try {
			final var root = Files.createTempDirectory("aot-loader-filarea");
			Files.createDirectories(root.resolve("alkt-dokument"));
			Files.createDirectories(root.resolve("alkt-bilder"));
			Files.createDirectories(root.resolve("alkol"));
			Files.writeString(root.resolve("alkt-dokument").resolve("11111111.rtf"), "{\\rtf1 Dokument}");
			Files.writeString(root.resolve("alkt-dokument").resolve("22222222.rtf"), "{\\rtf1 Beslut}");
			Files.writeString(root.resolve("alkt-dokument").resolve("44444444.rtf"), "{\\rtf1 Beslut om Krogen Tre}");
			Files.writeString(root.resolve("alkt-dokument").resolve("55555555.rtf"), "{\\rtf1 Tillståndsbevis}");
			Files.writeString(root.resolve("alkt-bilder").resolve("ansokan.pdf"), "%PDF-1.4 ansökan");
			Files.writeString(root.resolve("alkt-bilder").resolve("skylt.jpg"), "bild");
			Files.writeString(root.resolve("alkt-bilder").resolve("ansökan räkenskaper.pdf"), "%PDF-1.4 räkenskaper");
			Files.writeString(root.resolve("alkt-bilder").resolve("ritning våning 2.pdf"), "%PDF-1.4 ritning");
			Files.writeString(root.resolve("alkt-bilder").resolve("fasad.jpg"), "fasad");
			Files.writeString(root.resolve("alkt-bilder").resolve("Översiktskarta.pdf"), "%PDF-1.4 karta");
			Files.writeString(root.resolve("alkt-bilder").resolve("besöksrapport.pdf"), "%PDF-1.4 besök");
			Files.writeString(root.resolve("alkt-bilder").resolve("foto.jpg"), "foto");
			Files.writeString(root.resolve("alkt-bilder").resolve("serveringsansvarig.pdf"), "%PDF-1.4 serveringsansvarig");
			Files.writeString(root.resolve("alkol").resolve("33333333.rtf"), "{\\rtf1 Tobak}");
			Files.writeString(root.resolve("alkol").resolve("folkol-anmalan.pdf"), "%PDF-1.4 anmälan folköl");
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

	/**
	 * @return the start and the end of the investigation created for the errand
	 */
	private List<Instant> investigationTimes(final String errandId) {
		final var body = wiremock.findAll(postRequestedFor(urlEqualTo("/support-management/2281/AOT/errands/" + errandId + "/investigations"))).getFirst().getBodyAsString();
		return Stream.of("$.startedAt", "$.completedAt").map(path -> OffsetDateTime.parse(JsonPath.<String>read(body, path)).toInstant()).toList();
	}
}
