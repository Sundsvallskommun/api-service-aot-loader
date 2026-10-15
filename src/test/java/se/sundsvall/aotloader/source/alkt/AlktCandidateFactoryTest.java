package se.sundsvall.aotloader.source.alkt;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.AssetFile;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReviewRule;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ConditionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DocumentRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.FileRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYNSBESOK;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_OLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.DECISION_EXPIRED;
import static se.sundsvall.aotloader.service.model.ReviewRule.DECISION_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_ON_SEVERAL;
import static se.sundsvall.aotloader.service.model.ReviewRule.ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.ISSUE_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_INVALID;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_ON_SEVERAL_OWNERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.NO_ISSUING_ERRAND;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERIOD_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERIOD_ON_ALL_YEAR;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERIOD_PASSED;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERMIT_ROWS_ON_PLACE;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.RESTAURANT_NUMBER_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.RESTAURANT_NUMBER_INVALID;
import static se.sundsvall.aotloader.service.model.ReviewRule.SUBTYPE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNDERLYING_ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNPARSEABLE;
import static se.sundsvall.aotloader.service.model.Source.ALKT;
import static se.sundsvall.aotloader.source.alkt.AlktTestData.owner;
import static se.sundsvall.aotloader.source.alkt.AlktTestData.permit;
import static se.sundsvall.aotloader.source.alkt.AlktTestData.place;
import static se.sundsvall.aotloader.source.alkt.AlktTestData.snapshot;

class AlktCandidateFactoryTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
	private static final AotLoaderProperties PROPERTIES = TestData.properties(ErrandType.ALCOHOL_INSPECTION, true, Map.of("07", TestData.INTERNAL_INSPECTION, "09", ""));

	@Test
	void permitWithoutAnyRuleHit() {
		final var result = permitsWithoutAnyRuleHit();

		assertThat(result.notices()).isEmpty();
		assertThat(result.candidates()).hasSize(1);
		final var candidate = result.candidates().getFirst();
		assertThat(candidate.key()).isEqualTo(new ItemKey(ALKT, TILLSTAND, "1"));
		assertThat(candidate.type()).isEqualTo(ErrandType.SERVING_PERMIT);
		assertThat(candidate.ruleHits()).isEmpty();
		assertThat(candidate.placeName()).isEqualTo("Restaurang 1");
		assertThat(candidate.placeNumber()).isEqualTo("12345678");
		assertThat(candidate.title()).isEqualTo("Serveringstillstånd – Restaurang 1");
		assertThat(candidate.labelPaths()).containsExactly("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/PERMANENT_SERVING");
		assertThat(candidate.externalTags()).containsExactly(Map.entry("diarienummer", "IAN-2019-1"));
		assertThat(candidate.description()).contains("Serveringstillstånd för Restaurang 1, migrerat från AlkT.", "Drycker: Starköl, Vin", "Villkor:", "- Krav på bordsservering: Bord");
	}

	@Test
	void holderPlaceAndPersonsOfAPermit() {
		final var candidate = permitsWithoutAnyRuleHit().candidates().getFirst();

		assertThat(candidate.holder().legalId()).isEqualTo(new LegalId("5566778899", ENTERPRISE));
		assertThat(candidate.holder().name()).isEqualTo("Bolaget 10 AB");
		assertThat(candidate.holder().address()).isEqualTo("Box 1 Våning 2");
		assertThat(candidate.holder().contactPerson()).isEqualTo("Kontakt Person");
		assertThat(candidate.place().parameters()).extracting(Parameter::key, Parameter::values).containsExactly(
			tuple("restaurantNumber", List.of("12345678")),
			tuple("typAvServeringsstalle", List.of("Kvartersrestaurang")),
			tuple("omrade", List.of("Centrum")));
		assertThat(candidate.persons()).hasSize(2);
		assertThat(candidate.persons().getFirst().legalId()).isEqualTo(new LegalId("198001011234", PRIVATE));
		assertThat(candidate.persons().getFirst().positions()).containsExactly("Verkställande direktör");
		assertThat(candidate.persons().get(1).legalId()).isNull();
	}

	@Test
	void parametersOfAPermit() {
		final var candidate = permitsWithoutAnyRuleHit().candidates().getFirst();

		assertThat(parameters(candidate)).containsAllEntriesOf(Map.of(
			"alktObjektId", List.of("1"),
			"utfardat", List.of("2019-05-01"),
			"diarienummer", List.of("IAN-2019-1"),
			"ersatter", List.of("2015-01-01, SN 2015-1"),
			"drycker", List.of("Starköl", "Vin"),
			"serveringTill", List.of("Allmänheten"),
			"omfattning", List.of("Uteservering"),
			"serveringstider", List.of("11:00–01:00"),
			"aretRunt", List.of("Ja"),
			"villkor", List.of("Krav på bordsservering: Bord")));
		assertThat(parameters(candidate)).containsEntry("senasteBeslut", List.of("Serveringstillstånd, 2019-04-20"))
			.containsEntry("noteringar", List.of("En notering"))
			.containsEntry("migrerad", List.of("2026-10-12 från AlkT"))
			.doesNotContainKeys("sasong", "period");
	}

	@Test
	void postOfAPermit() {
		final var candidate = permitsWithoutAnyRuleHit().candidates().getFirst();

		assertThat(candidate.asset().issued()).isEqualTo(LocalDate.of(2019, 5, 1));
		assertThat(candidate.asset().validTo()).isNull();
		assertThat(candidate.asset().titleSuffix()).isNull();
		assertThat(candidate.asset().description()).isEqualTo("Serveringstillstånd för Restaurang 1");
		assertThat(candidate.asset().parameters()).containsExactly(
			Map.entry("permitHolderName", "Bolaget 10 AB"),
			Map.entry("permitHolderOrgNumber", "556677-8899"),
			Map.entry("permitHolderStreet", "Box 1 Våning 2"),
			Map.entry("permitHolderPostalAddress", "85230 Sundsvall"),
			Map.entry("permitHolderPhone", "060-654321"),
			Map.entry("premisesName", "Restaurang 1"),
			Map.entry("premisesStreet", "Storgatan 1"),
			Map.entry("premisesPostalAddress", "85230 Sundsvall"),
			Map.entry("premisesPhone", "060-123456"),
			Map.entry("premisesRestaurantNumber", "12345678"),
			Map.entry("servingToPublic", "true"),
			Map.entry("servingToClosedCompany", "false"),
			Map.entry("scopeAllYear", "true"),
			Map.entry("scopeAnnualPeriod", "false"),
			Map.entry("scopeTasting", "false"),
			Map.entry("beverageSpirits", "false"),
			Map.entry("beverageWine", "true"),
			Map.entry("beverageBeer", "true"),
			Map.entry("beverageOtherFermented", "false"),
			Map.entry("servingAreaDescription", "Matsal"),
			Map.entry("servingAreaSeats", "80"),
			Map.entry("servingAreaMaxPersons", "200"),
			Map.entry("servingHours", "11:00–01:00"),
			Map.entry("conditions", "Krav på bordsservering: Bord"),
			Map.entry("caseNumber", "IAN-2019-1"),
			Map.entry("decisionDateAndCaseNumber", "2019-05-01, IAN-2019-1"),
			Map.entry("diarienummer", "IAN-2019-1"),
			Map.entry("migreradFran", "AlkT"));
	}

	@Test
	void filesOfAPermit() {
		final var candidate = permitsWithoutAnyRuleHit().candidates().getFirst();

		assertThat(candidate.files()).containsExactly(
			new FileReference("22222222.rtf", DOCUMENTS),
			new FileReference("11111111.rtf", DOCUMENTS),
			new FileReference("bilaga.pdf", ATTACHMENTS),
			new FileReference("ansokan.pdf", ATTACHMENTS),
			new FileReference("ritning.pdf", ATTACHMENTS),
			new FileReference("33333333.rtf", DOCUMENTS));
		assertThat(candidate.assetFiles()).containsExactly(
			new AssetFile(new FileReference("33333333.rtf", DOCUMENTS), "Tillståndsbevis"),
			new AssetFile(new FileReference("ritning.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("11111111.rtf", DOCUMENTS), "Beslut"));
		// A decision's file gets the day of the decision, and a drawing the purpose of one
		assertThat(candidate.detailsOf(new FileReference("11111111.rtf", DOCUMENTS))).isEqualTo(new FileDetails(null, LocalDateTime.of(2019, 4, 20, 0, 0)));
		assertThat(candidate.detailsOf(new FileReference("ritning.pdf", ATTACHMENTS))).isEqualTo(new FileDetails(FilePurposes.SERVING_AREA_DRAWING, null));
	}

	/**
	 * A permit with its place, owner, decision, errands, files, persons and conditions, that hits no review rule.
	 */
	private static ReaderResult permitsWithoutAnyRuleHit() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).gtAgarId(10).gtRestaurantNumber("12345678").scope(true, false, false, false).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "556677-8899"))
			.decision(new DecisionRow(100, 500, "GT", LocalDate.of(2019, 4, 20), null, "TDB", "11111111.rtf", "bilaga.pdf"))
			.errand(new ErrandRow(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.errand(new ErrandRow(501, 1, "01", "ian 2019 1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.errand(new ErrandRow(502, 2, "01", "IAN-2019-1", null, null, null))
			.file(new FileRow(500, "22222222.rtf", true))
			.file(new FileRow(501, "ansokan.pdf", false))
			.file(new FileRow(502, "annat-stalle.pdf", false))
			.document(new DocumentRow(1, 500, null, "TDBV2", LocalDate.of(2019, 5, 2), "33333333.rtf"))
			.person(new PersonRow(10, "Person Ett", "800101-1234", List.of("VD"), false))
			.person(new PersonRow(10, "Person Två", null, List.of(), true))
			.condition(new ConditionRow(1, "0100", "Bord", null, null))
			.condition(new ConditionRow(1, null, "Utgånget villkor", null, "2020-01-01"))
			.build();

		return new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits();
	}

	@Test
	void permitWithManyRuleHits() {
		final var snapshot = snapshot()
			.permit(permit(1).gtAgarId(11).beslutsId(100).diaryNumber(null).issued(null).servingTo(false, false).gtRestaurantNumber("87654321")
				.aretRunt(true).period("2026-01-01", "2026-03-31").time("10:00", null).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "55667"))
			.decision(new DecisionRow(100, null, "GT", LocalDate.of(2019, 4, 20), LocalDate.of(2026, 1, 1), null, null, null))
			.condition(new ConditionRow(1, null, "Villkor", "då och då", "tills vidare"))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(
			OWNER_DIFFERS, DECISION_EXPIRED, PERIOD_PASSED, PERIOD_ON_ALL_YEAR, RESTAURANT_NUMBER_DIFFERS, LEGAL_ID_INVALID, UNPARSEABLE, UNPARSEABLE, UNPARSEABLE, NO_ISSUING_ERRAND,
			DIARY_NUMBER_MISSING, ISSUE_DATE_MISSING, SUBTYPE_MISSING, CERTIFICATE_MISSING);
		assertThat(candidate.ruleHits()).extracting(RuleHit::detail).contains(
			"AgarID i tillståndet 11, på serveringsstället 10",
			"beslutstyp GT, giltigt till 2026-01-01",
			"2026-01-01–2026-03-31",
			"på tillståndet 87654321, på serveringsstället 12345678",
			"serveringstid 2 '10:00–'",
			"villkorets slutdatum 'tills vidare'",
			"villkorets startdatum 'då och då'");
		assertThat(candidate.labelPaths()).isEmpty();
		assertThat(candidate.holder().legalId()).isNull();
		assertThat(parameters(candidate)).containsEntry("serveringstider", List.of("11:00–01:00", "10:00–"))
			.containsEntry("villkor", List.of("Villkor"))
			.containsEntry("period", List.of("2026-01-01–2026-03-31"));
	}

	@Test
	void aFileNameThatAnotherPlaceAlsoRefersToIsMarked() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "5566778899"))
			.decision(new DecisionRow(100, 500, "GT", LocalDate.of(2019, 4, 20), null, null, null, "beslut.pdf"))
			.decision(new DecisionRow(200, null, "GT", null, null, null, null, "Beslut.pdf"))
			.errand(new ErrandRow(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.errand(new ErrandRow(501, 1, "07", "TIL-2020-9", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null))
			.errand(new ErrandRow(600, 2, "07", "TIL-2021-7", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.file(new FileRow(500, "ansokan.pdf", false))
			.file(new FileRow(500, "intyg.pdf", false))
			.file(new FileRow(501, "intyg.pdf", false))
			.file(new FileRow(600, "ANSOKAN.pdf", false))
			// An event with neither an errand nor a place counts as a place of its own
			.file(new FileRow(500, "kvitto.pdf", false))
			.unplacedFile(77, new FileRow(null, "KVITTO.pdf", false))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.files()).containsExactlyInAnyOrder(
			new FileReference("ansokan.pdf", ATTACHMENTS, true),
			new FileReference("intyg.pdf", ATTACHMENTS, false),
			new FileReference("kvitto.pdf", ATTACHMENTS, true),
			new FileReference("beslut.pdf", ATTACHMENTS, true),
			new FileReference("ritning.pdf", ATTACHMENTS, false));
	}

	@Test
	void thePostGetsTheCurrentCertificateAndAnnexesOfThePlaceAndTheDrawingsAndDecisionsOfTheIssuingErrand() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).issued(LocalDate.of(2020, 1, 1)).scope(false, true, false, false).build())
			.permit(permit(2).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 10, "12345679"))
			.owner(owner(10, "5566778899"))
			.decision(new DecisionRow(100, 500, "GT", LocalDate.of(2019, 12, 31), null, "TDB", "44444444.rtf", "Planritning.PDF"))
			.decision(new DecisionRow(101, 600, "GT", LocalDate.of(2015, 1, 31), null, "TDBBS", "55555555.rtf", null))
			.errand(new ErrandRow(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 12, 1), LocalDate.of(2020, 1, 1), null))
			.errand(new ErrandRow(600, 1, "02", "IAN-2015-6", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), null))
			.file(new FileRow(500, "situationsritning.pdf", false, new FileDetails(FilePurposes.SERVING_AREA_DRAWING, null)))
			// A referral answer named as a drawing stays on the errand
			.file(new FileRow(500, "ritning yttrande.pdf", false, new FileDetails(FilePurposes.RESCUE_SERVICE_RESPONSE, null)))
			.file(new FileRow(600, "gammal ritning.pdf", false))
			// Two certificates of the same day, the one without an errand made after the other
			.document(new DocumentRow(10, 600, null, "TDBV2", LocalDate.of(2020, 6, 1), "66666666.rtf"))
			.document(new DocumentRow(11, null, 1, "tdbv2", LocalDate.of(2020, 6, 1), "77777777.rtf"))
			.document(new DocumentRow(12, 500, null, "TDBB", LocalDate.of(2018, 1, 1), "88888888.rtf"))
			.document(new DocumentRow(13, 600, null, "TDBBC", LocalDate.of(2020, 2, 1), "99999999.rtf"))
			.document(new DocumentRow(14, 600, null, "TDBBS", LocalDate.of(2020, 3, 1), "12121212.rtf"))
			.document(new DocumentRow(15, null, 2, "TDBV2", LocalDate.of(2024, 1, 1), "13131313.rtf"))
			// An annex of catering on a permit without catering, and an annex older than the permit
			.document(new DocumentRow(16, null, 2, "TDBBC", LocalDate.of(2024, 1, 1), "14141414.rtf"))
			.document(new DocumentRow(17, null, 2, "TDBBS", LocalDate.of(2018, 1, 1), "15151515.rtf"))
			.placeFile(1, new FileRow(null, "anmalan serveringsansvarig.pdf", false))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		final var candidate = candidates.getFirst();
		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).doesNotContain(CERTIFICATE_MISSING, CERTIFICATE_OLDER);
		assertThat(candidate.assetFiles()).containsExactly(
			new AssetFile(new FileReference("77777777.rtf", DOCUMENTS), "Tillståndsbevis"),
			new AssetFile(new FileReference("12121212.rtf", DOCUMENTS), "Beslutsbilaga"),
			new AssetFile(new FileReference("99999999.rtf", DOCUMENTS), "Beslutsbilaga"),
			new AssetFile(new FileReference("situationsritning.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("Planritning.PDF", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("ritning.pdf", ATTACHMENTS, true), "Ritning"),
			new AssetFile(new FileReference("44444444.rtf", DOCUMENTS), "Beslut"));
		assertThat(candidate.files()).containsExactly(
			new FileReference("situationsritning.pdf", ATTACHMENTS),
			new FileReference("ritning yttrande.pdf", ATTACHMENTS),
			new FileReference("44444444.rtf", DOCUMENTS),
			new FileReference("Planritning.PDF", ATTACHMENTS),
			new FileReference("ritning.pdf", ATTACHMENTS, true),
			new FileReference("anmalan serveringsansvarig.pdf", ATTACHMENTS),
			new FileReference("77777777.rtf", DOCUMENTS),
			new FileReference("12121212.rtf", DOCUMENTS),
			new FileReference("99999999.rtf", DOCUMENTS));

		final var withoutCatering = candidates.get(1);
		assertThat(withoutCatering.assetFiles()).extracting(AssetFile::category).containsExactly("Tillståndsbevis", "Ritning");
		assertThat(withoutCatering.files()).extracting(FileReference::name).doesNotContain("14141414.rtf", "15151515.rtf");
	}

	@Test
	void onlyThePermitDecisionsOfTheIssuingErrandGoOnThePost() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "5566778899"))
			.decision(new DecisionRow(100, 500, "0100", LocalDate.of(2019, 4, 20), null, "tbt", "11111111.rtf", "Beslut 2019.pdf"))
			.decision(new DecisionRow(101, 500, "0200", LocalDate.of(2019, 4, 20), null, "TDU", "22222222.rtf", "remissvar räddningstjänsten.pdf"))
			.decision(new DecisionRow(102, 500, "D 02", LocalDate.of(2019, 4, 21), null, null, null, "Registreringsbevis.pdf"))
			.decision(new DecisionRow(103, 500, "Deleg", LocalDate.of(2019, 4, 22), null, null, null, "Beslutsunderlag.pdf"))
			.errand(new ErrandRow(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.files()).extracting(FileReference::name).contains(
			"11111111.rtf", "Beslut 2019.pdf", "22222222.rtf", "remissvar räddningstjänsten.pdf", "Registreringsbevis.pdf", "Beslutsunderlag.pdf");
		// An investigation, a referral answer, a registration certificate and the basis of a decision go on the errand only
		assertThat(candidate.assetFiles()).filteredOn(file -> "Beslut".equals(file.category())).extracting(file -> file.reference().name())
			.containsExactly("11111111.rtf", "Beslut 2019.pdf");
	}

	@Test
	void aCertificateOlderThanThePermitOrWithoutADateGoesOnTheErrandOnly() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).issued(LocalDate.of(2022, 6, 1)).build())
			.permit(permit(2).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 10, "12345679"))
			.owner(owner(10, "5566778899"))
			.errand(new ErrandRow(500, 1, "01", "IAN-2015-1", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), null))
			.decision(new DecisionRow(100, 500, "GT", LocalDate.of(2015, 2, 1), null, "TDB", "33333333.rtf", null))
			// The certificate of the former holder is also a decision document, which would otherwise go on the post
			.document(new DocumentRow(10, 500, null, "TDBV2", LocalDate.of(2015, 2, 1), "33333333.rtf"))
			.document(new DocumentRow(11, null, 2, "TDBV2", null, "44444444.rtf"))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.contains(tuple(CERTIFICATE_OLDER, "beviset är från 2015-02-01, tillståndet utfärdades 2022-06-01"));
		assertThat(candidates.get(0).files()).contains(new FileReference("33333333.rtf", DOCUMENTS));
		assertThat(candidates.get(0).assetFiles()).extracting(file -> file.reference().name()).doesNotContain("33333333.rtf");
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(CERTIFICATE_OLDER, "beviset saknar datum"));
		assertThat(candidates.get(1).files()).contains(new FileReference("44444444.rtf", DOCUMENTS));
		assertThat(candidates.get(1).assetFiles()).extracting(file -> file.reference().name()).doesNotContain("44444444.rtf");
	}

	@Test
	void aRestaurantNumberWrittenWithSpacesOrADotIsTheSameNumber() {
		for (final var written : List.of("1234 5678", "1234 567 8", "1234 5678.", " 12345678 ")) {
			final var snapshot = snapshot().permit(permit(1).gtRestaurantNumber(written).build()).place(place(1, 10, "12345678")).owner(owner(10, "5566778899")).build();

			final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

			assertThat(candidate.ruleHits()).as(written).extracting(RuleHit::rule).doesNotContain(RESTAURANT_NUMBER_DIFFERS);
		}
	}

	@Test
	void aRestaurantNumberWithMoreDigitsDiffers() {
		final var snapshot = snapshot().permit(permit(1).gtRestaurantNumber("1234 5678 01").build()).place(place(1, 10, "12345678")).owner(owner(10, "5566778899")).build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).contains(RESTAURANT_NUMBER_DIFFERS);
		assertThat(candidate.ruleHits()).extracting(RuleHit::detail).contains("på tillståndet 1234 5678 01, på serveringsstället 12345678");
	}

	@Test
	void permitWithoutPlace() {
		final var snapshot = snapshot().permit(permit(1).build()).build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(PLACE_MISSING, NO_ISSUING_ERRAND, CERTIFICATE_MISSING);
		assertThat(candidate.holder()).isNull();
		assertThat(candidate.place()).isNull();
		assertThat(candidate.title()).isEqualTo("Serveringstillstånd – okänt serveringsställe");
	}

	@Test
	void placeWithoutOwnerAndWithoutRestaurantNumber() {
		final var snapshot = snapshot().permit(permit(1).build()).place(place(1, null, null)).build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(
			tuple(RESTAURANT_NUMBER_INVALID, null),
			tuple(LEGAL_ID_INVALID, "serveringsstället saknar ägare"));
	}

	@Test
	void restaurantNumberOfWrongFormat() {
		final var snapshot = snapshot().permit(permit(1).build()).place(place(1, 10, "1234")).owner(owner(10, "5566778899")).build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(RESTAURANT_NUMBER_INVALID, "'1234'"));
	}

	@Test
	void sameNumberOnSeveralOwnersAndSameDiaryNumberOnSeveralPermits() {
		final var snapshot = snapshot()
			.permit(permit(1).diaryNumber("IAN-2019-5").build())
			.permit(permit(2).diaryNumber("ian 2019 5").build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 11, "12345679"))
			.owner(owner(10, "556677-8899"))
			.owner(owner(11, "5566778899"))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.ruleHits()).extracting(RuleHit::rule).contains(LEGAL_ID_ON_SEVERAL_OWNERS, DIARY_NUMBER_ON_SEVERAL));
	}

	@Test
	void anOpenErrandThatIssuedThePermitIsHeldForReview() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).gtAgarId(10).gtRestaurantNumber("12345678").scope(true, false, false, false).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "556677-8899"))
			.decision(new DecisionRow(100, 500, "GT", LocalDate.of(2019, 4, 20), null, null, null, null))
			.errand(new ErrandRow(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.errand(new ErrandRow(501, 1, "01", "ian 2019 1", LocalDate.of(2019, 1, 1), null, null))
			.errand(new ErrandRow(502, 2, "01", "IAN-2019-1", null, null, null))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).containsExactly(tuple(UNDERLYING_ERRAND_OPEN, "ArendeID 501, diarienummer ian 2019 1"), tuple(CERTIFICATE_MISSING, null));
	}

	@Test
	void theSameNumberWrittenInAnotherWayIsTheSameOwner() {
		final var snapshot = snapshot()
			.permit(permit(1).build())
			.permit(permit(2).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 11, "12345679"))
			.owner(owner(10, "121212+1212"))
			.owner(owner(11, "191212121212"))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates).allSatisfy(candidate -> assertThat(candidate.ruleHits()).extracting(RuleHit::rule).contains(LEGAL_ID_ON_SEVERAL_OWNERS));
	}

	@Test
	void severalRowsOfOnePlaceAndADecisionThatIsMissing() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(999).build())
			.permit(permit(1).diaryNumber("IAN-2019-9").build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "5566778899"))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates).hasSize(2).allSatisfy(candidate -> assertThat(candidate.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(PERMIT_ROWS_ON_PLACE, "2 rader")));
		assertThat(candidates.getFirst().ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(DECISION_MISSING, "BeslutsID 999"));
	}

	@Test
	void aDatePeriodOpenAtOneEndCannotBeInterpreted() {
		final var snapshot = snapshot()
			.permit(permit(1).aretRunt(false).period("2026-11-01", null).build())
			.permit(permit(2).aretRunt(false).period(null, "2026-11-30").build())
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "perioden '2026-11-01–' saknar slut"));
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "perioden '–2026-11-30' saknar början"));
	}

	@Test
	void notAllYearWithoutPeriod() {
		final var snapshot = snapshot().permit(permit(1).aretRunt(false).build()).place(place(1, 10, "12345678")).owner(owner(10, "5566778899")).build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).contains(PERIOD_MISSING);
	}

	@Test
	void temporaryPermitGetsItsSubtypeAndLastDay() {
		final var snapshot = snapshot()
			.permit(permit(1).aretRunt(false).period("2026-11-01", "2026-11-30").build())
			.permit(permit(2).aretRunt(false).servingTo(false, true).period("2026-11-01", "2026-11-30").build())
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates.get(0).labelPaths()).containsExactly("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TEMPORARY_SERVING_PUBLIC");
		assertThat(candidates.get(0).asset().validTo()).isEqualTo(LocalDate.of(2026, 11, 30));
		assertThat(candidates.get(1).labelPaths()).containsExactly("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TEMPORARY_SERVING_PRIVATE");
		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule).doesNotContain(PERIOD_PASSED, PERIOD_MISSING, PERIOD_ON_ALL_YEAR, UNPARSEABLE);
	}

	@Test
	void subtypeOrder() {
		final var snapshot = snapshot()
			.permit(permit(1).servingTo(false, false).scope(false, true, true, true).build())
			.permit(permit(2).servingTo(false, false).scope(false, false, true, true).build())
			.permit(permit(3).servingTo(false, false).scope(false, false, true, false).build())
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates).extracting(candidate -> candidate.labelPaths().getFirst()).containsExactly(
			"CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/PERMANENT_CATERING",
			"CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/FARM_SALES",
			"CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TASTING");
	}

	@Test
	void seasons() {
		final var snapshot = snapshot()
			.permit(permit(1).season("1 maj", "30 sept").build())
			.permit(permit(2).period("05-01", "09-30").build())
			.permit(permit(3).season("vår", "höst").build())
			.permit(permit(4).period("maj", "2026-09-30").build())
			.permit(permit(5).period("2026-09-30", "2026-05-01").aretRunt(false).build())
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(parameters(candidates.get(0))).containsEntry("sasong", List.of("05-01–09-30"));
		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule).doesNotContain(UNPARSEABLE);
		assertThat(parameters(candidates.get(1))).containsEntry("sasong", List.of("05-01–09-30")).doesNotContainKey("period");
		assertThat(parameters(candidates.get(2))).containsEntry("sasong", List.of("vår–höst"));
		assertThat(candidates.get(2).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "säsong 'vår–höst'"));
		assertThat(parameters(candidates.get(3))).containsEntry("period", List.of("maj–2026-09-30"));
		assertThat(candidates.get(3).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "period 'maj–2026-09-30'"));
		assertThat(candidates.get(4).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "perioden '2026-09-30–2026-05-01' slutar före sin början"));
	}

	@Test
	void drinksAndScope() {
		assertThat(ServingPermits.drinks(permit(1).drinks(true, true, true, true, true).build()))
			.containsExactly("Starköl", "Vin", "Spritdrycker", "Andra jästa alkoholdrycker", "Alkoholdrycksliknande preparat");
		assertThat(ServingPermits.servingTo(permit(1).servingTo(true, true).build())).containsExactly("Allmänheten", "Slutna sällskap");
		assertThat(ServingPermits.scope(permit(1).scope(true, true, true, true).build())).containsExactly("Uteservering", "Catering", "Provsmakning", "Gårdsförsäljning");
	}

	@Test
	void placeMarking() {
		final var placeWithMarking = new AlktSnapshot.PlaceRow(1, 10, "12345678", "UNG", "Ungdomsgården", null, null, null, null, null, null, null, null);
		final var snapshot = snapshot().permit(permit(1).build()).place(placeWithMarking).owner(owner(10, "5566778899")).build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.place().parameters()).extracting(Parameter::key, Parameter::values).containsExactly(
			tuple("restaurantNumber", List.of("12345678")),
			tuple("markering", List.of("Ungdomlig målgrupp")));
	}

	@Test
	void permitWithoutObjektIdIsANotice() {
		final var row = new AlktSnapshot.PermitRow(null, null, false, false, false, false, false, true, null, null, null, null, false, false, false, false, false, false, false, false, false, true, false, List.of(), null, null, null,
			List.of(), null, null, null, "IAN-1", null, null);
		final var snapshot = snapshot().permit(row).build();

		final var result = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits();

		assertThat(result.candidates()).isEmpty();
		assertThat(result.notices()).singleElement().satisfies(notice -> {
			assertThat(notice.key()).isEqualTo(new ItemKey(ALKT, TILLSTAND, "utan-id-1"));
			assertThat(notice.outcome()).isEqualTo(Outcome.GRANSKAS);
			assertThat(notice.detail()).isEqualTo("G1 Tillståndet saknar ObjektID, diarienummer IAN-1");
		});
	}

	@Test
	void inspectionErrands() {
		final var snapshot = snapshot()
			.permit(permit(1).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 12, "12345679"))
			.owner(owner(10, "556677-8899"))
			.applicant(AlktTestData.applicant(70, "5566778899"))
			.applicant(AlktTestData.applicant(71, "5599887766"))
			.errand(new ErrandRow(700, 1, "07", "TIL-2020-1", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 3, 1), 70))
			.errand(new ErrandRow(701, 1, "09", null, LocalDate.of(2021, 1, 1), null, 71))
			.errand(new ErrandRow(702, 2, "07", null, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.errand(new ErrandRow(703, 1, "01", null, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.decision(new DecisionRow(800, 700, "ER", LocalDate.of(2020, 2, 1), null, null, "33333333.rtf", null))
			.file(new FileRow(700, "protokoll.pdf", false))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).inspections().candidates();

		assertThat(candidates).extracting(Candidate::key).containsExactlyInAnyOrder(new ItemKey(ALKT, TILLSYN, "700"), new ItemKey(ALKT, TILLSYN, "701"));
		final var closed = candidates.stream().filter(candidate -> candidate.key().sourceId().equals("700")).findFirst().orElseThrow();
		assertThat(closed.type()).isEqualTo(ErrandType.ALCOHOL_INSPECTION);
		assertThat(closed.ruleHits()).isEmpty();
		assertThat(closed.title()).isEqualTo("Tillsyn, ifrågasatt åtgärd – Restaurang 1");
		assertThat(closed.labelPaths()).containsExactly("TAGROOT/ALCOHOL_INSPECTION", "CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION");
		assertThat(closed.externalTags()).containsEntry("diarienummer", "TIL-2020-1");
		assertThat(closed.asset()).isNull();
		assertThat(closed.permitLink().permitKey()).isEqualTo(new ItemKey(ALKT, TILLSTAND, "1"));
		assertThat(closed.permitLink().sameHolder()).isTrue();
		assertThat(closed.holder().name()).isEqualTo("Sökande 70 AB");
		assertThat(parameters(closed)).containsEntry("beslut", List.of("Erinran, 2020-02-01")).containsEntry("arendetyp", List.of("Tillsyn, ifrågasatt åtgärd"));
		assertThat(closed.files()).containsExactly(new FileReference("protokoll.pdf", ATTACHMENTS), new FileReference("33333333.rtf", DOCUMENTS));
		assertThat(closed.investigation())
			.isEqualTo(new InvestigationData("Tillsyn, ifrågasatt åtgärd – Restaurang 1", LocalDateTime.of(2020, 1, 1, 0, 0), LocalDateTime.of(2020, 3, 1, 0, 0)));

		final var open = candidates.stream().filter(candidate -> candidate.key().sourceId().equals("701")).findFirst().orElseThrow();
		assertThat(open.ruleHits()).extracting(RuleHit::rule).containsExactly(ERRAND_OPEN);
		assertThat(open.labelPaths()).containsExactly("TAGROOT/ALCOHOL_INSPECTION");
		assertThat(open.permitLink().sameHolder()).isFalse();
		assertThat(open.investigation().startedAt()).isEqualTo(LocalDateTime.of(2021, 1, 1, 0, 0));
		assertThat(open.investigation().completed()).isFalse();
	}

	@Test
	void inspectionErrandsOfAllPlaces() {
		final var snapshot = snapshot()
			.errand(new ErrandRow(702, 2, "07", null, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, TestData.properties(ErrandType.ALCOHOL_INSPECTION, false, Map.of("07", "X"))).inspections().candidates();

		assertThat(candidates).singleElement().satisfies(candidate -> {
			assertThat(candidate.ruleHits()).extracting(RuleHit::rule).containsExactly(ReviewRule.PLACE_MISSING);
			assertThat(candidate.permitLink().permitKey()).isNull();
			assertThat(candidate.holder()).isNull();
			assertThat(candidate.title()).isEqualTo("Tillsyn, ifrågasatt åtgärd – okänt serveringsställe");
			assertThat(candidate.labelPaths()).containsExactly("TAGROOT/ALCOHOL_INSPECTION", "X");
		});
	}

	@Test
	void inspectionVisits() {
		final var snapshot = snapshot()
			.permit(permit(1).build())
			.permit(permit(5).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 12, "12345679"))
			.owner(owner(10, "556677-8899"))
			.visit(new VisitRow(900, 1, LocalDate.of(2023, 11, 4), "21.30", "23", "04", "Mycket folk i baren.", "66666666.rtf", "protokoll.pdf"))
			.visit(new VisitRow(901, 2, LocalDate.of(2023, 11, 4), null, null, "04", null, null, "PROTOKOLL.pdf"))
			.visit(new VisitRow(902, 1, null, null, null, null, null, null, null))
			.visit(new VisitRow(903, 5, LocalDate.of(2022, 3, 1), "09:15:00", "10:00:00", "99", null, null, null))
			.remark(new RemarkRow(900, "05", "Ungdomar i baren", "bild.jpg"))
			.remark(new RemarkRow(900, "77", null, null))
			.visitFile(900, "anteckningar.pdf")
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).visits().candidates();

		assertThat(candidates).extracting(Candidate::key)
			.containsExactly(new ItemKey(ALKT, TILLSYNSBESOK, "900"), new ItemKey(ALKT, TILLSYNSBESOK, "902"), new ItemKey(ALKT, TILLSYNSBESOK, "903"));
		final var visit = candidates.getFirst();
		assertThat(visit.type()).isEqualTo(ErrandType.ALCOHOL_INSPECTION_VISIT);
		assertThat(visit.ruleHits()).isEmpty();
		assertThat(visit.holder()).isNull();
		assertThat(visit.permitLink()).isNull();
		assertThat(visit.placeName()).isEqualTo("Restaurang 1");
		assertThat(visit.placeNumber()).isEqualTo("12345678");
		assertThat(visit.title()).isEqualTo("Tillsynsbesök – Restaurang 1");
		// The end time 23 cannot be read, so the visit has no end
		assertThat(visit.investigation()).isEqualTo(new InvestigationData("Tillsynsbesök – Restaurang 1", LocalDateTime.of(2023, 11, 4, 21, 30), null));
		assertThat(visit.labelPaths()).containsExactly(TestData.EXTERNAL_INSPECTION, "TAGROOT/ALCOHOL_INSPECTION");
		assertThat(visit.externalTags()).isEmpty();
		assertThat(visit.asset()).isNull();
		assertThat(visit.description()).isEqualTo("""
			Tillsynsbesök på Restaurang 1 2023-11-04, migrerat från AlkT.
			Tillsynsart: Rutintillsyn
			Tid: 21:30–23
			Anmärkningar:
			- Servering till minderårig: Ungdomar i baren
			- 77
			Anteckning: Mycket folk i baren.""");
		assertThat(parameters(visit))
			.containsEntry("alktTillsynsbesokId", List.of("900"))
			.containsEntry("besoksdatum", List.of("2023-11-04"))
			.containsEntry("besokstid", List.of("21:30–23"))
			.containsEntry("tillsynsart", List.of("Rutintillsyn"))
			.containsEntry("anmarkningar", List.of("Servering till minderårig: Ungdomar i baren", "77"))
			.containsEntry("anteckning", List.of("Mycket folk i baren."));
		// The attachment shares its name with the visit to another place, which is not migrated but still refers to it
		assertThat(visit.files()).containsExactly(
			new FileReference("66666666.rtf", DOCUMENTS),
			new FileReference("protokoll.pdf", ATTACHMENTS, true),
			new FileReference("anteckningar.pdf", ATTACHMENTS),
			new FileReference("bild.jpg", ATTACHMENTS));

		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::toReportText)
			.containsExactly("G28 Tillsynsbesöket saknar datum eller har ett datum efter migreringsdagen och kan vara planerat men inte genomfört: datum saknas");
		assertThat(candidates.get(1).description()).isEqualTo("Tillsynsbesök på Restaurang 1, migrerat från AlkT.");
		final var withoutPlace = candidates.get(2);
		assertThat(withoutPlace.ruleHits()).extracting(RuleHit::toReportText).containsExactly("G1 Tillståndet saknar serveringsställe: tillsynsbesöket saknar serveringsställe");
		assertThat(withoutPlace.title()).isEqualTo("Tillsynsbesök – okänt serveringsställe");
		assertThat(parameters(withoutPlace)).containsEntry("tillsynsart", List.of("99")).containsEntry("besokstid", List.of("09:15–10:00")).doesNotContainKeys("anmarkningar", "anteckning");
	}

	@Test
	void inspectionVisitsOfAllPlaces() {
		final var snapshot = snapshot()
			.visit(new VisitRow(901, 2, LocalDate.of(2023, 11, 4), null, null, null, null, null, null))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, TestData.properties(ErrandType.ALCOHOL_INSPECTION_VISIT, false, Map.of())).visits().candidates();

		assertThat(candidates).singleElement().satisfies(candidate -> assertThat(candidate.ruleHits()).extracting(RuleHit::rule).containsExactly(ReviewRule.PLACE_MISSING));
	}

	private static Map<String, List<String>> parameters(final Candidate candidate) {
		return candidate.parameters().stream().collect(java.util.stream.Collectors.toMap(Parameter::key, Parameter::values));
	}
}
