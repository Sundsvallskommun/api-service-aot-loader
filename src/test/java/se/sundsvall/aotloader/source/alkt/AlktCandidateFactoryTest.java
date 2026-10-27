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
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.InvestigationTexts.Part;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.MeasureData;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PermitLink.Basis;
import se.sundsvall.aotloader.service.model.ReaderResult;
import se.sundsvall.aotloader.service.model.ReviewRule;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.InspectionProtocols;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.RoundRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.StaffMarks;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.VisitTestRows;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.ConditionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.DocumentRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.FileRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.PlaceEventRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.StaffRow;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot.TextRow;

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
import static se.sundsvall.aotloader.service.model.ReviewRule.FORMER_HOLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.GRANT_DECISION_MISSING;
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
		assertThat(candidate.errand().title()).isEqualTo("Serveringstillstånd – Restaurang 1");
		assertThat(candidate.errand().labelPaths()).containsExactly("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/PERMANENT_SERVING");
		assertThat(candidate.errand().externalTags()).containsExactly(Map.entry("diarienummer", "IAN-2019-1"));
		assertThat(candidate.errand().description()).contains("Serveringstillstånd för Restaurang 1.", "Drycker: Starköl, Vin", "Villkor:", "- Krav på bordsservering: Bord");
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
		assertThat(candidate.persons()).hasSize(7);
		assertThat(candidate.persons().getFirst().legalId()).isEqualTo(new LegalId("198001011234", PRIVATE));
		assertThat(candidate.persons().getFirst().positions()).containsExactly("Verkställande direktör");
		assertThat(candidate.persons().get(1).legalId()).isNull();
		assertThat(candidate.persons().get(1).foreign()).isTrue();
		// An owning company keeps its organization number, so that it is looked up as a company
		assertThat(candidate.persons().get(2).legalId()).isEqualTo(new LegalId("5599887766", ENTERPRISE));
		assertThat(candidate.persons().get(2).isCompany()).isTrue();
		assertThat(candidate.persons().get(3).legalId()).isNull();
		assertThat(candidate.persons().get(3).foreign()).isFalse();
		// A person marked as foreign whose number is still a Swedish personal identity number, check digit and all, is
		// looked up; another number, which may be a foreign one, is not
		assertThat(candidate.persons().get(4).legalId()).isEqualTo(new LegalId("199001011239", PRIVATE));
		assertThat(candidate.persons().get(4).foreign()).isTrue();
		assertThat(candidate.persons().get(5).legalId()).isNull();
		assertThat(candidate.persons().get(6).legalId()).isNull();
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
		// The serving area under the key and label of Draken's examination of the premises (decision 82)
		assertThat(candidate.errand().parameters()).filteredOn(parameter -> parameter.key().equals("PREMISES_SERVING_AREA"))
			.extracting(Parameter::displayName, Parameter::values).containsExactly(tuple("Serveringsytor", List.of("Matsal")));
		assertThat(parameters(candidate)).doesNotContainKey("serveringslokal");
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
			Map.entry("executionDate", "2019-05-01"),
			Map.entry("decisionDateAndCaseNumber", "2019-05-01, IAN-2019-1"),
			Map.entry("diarienummer", "IAN-2019-1"),
			Map.entry("migreradFran", "AlkT"));
	}

	@Test
	void filesOfAPermit() {
		final var candidate = permitsWithoutAnyRuleHit().candidates().getFirst();

		assertThat(candidate.files().references()).containsExactly(
			new FileReference("22222222.rtf", DOCUMENTS),
			new FileReference("11111111.rtf", DOCUMENTS),
			new FileReference("bilaga.pdf", ATTACHMENTS),
			new FileReference("ansokan.pdf", ATTACHMENTS),
			new FileReference("ritning.pdf", ATTACHMENTS),
			new FileReference("33333333.rtf", DOCUMENTS));
		assertThat(candidate.files().onPost()).containsExactly(
			new AssetFile(new FileReference("33333333.rtf", DOCUMENTS), "Tillståndsbevis"),
			new AssetFile(new FileReference("ritning.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("11111111.rtf", DOCUMENTS), "Beslut"));
		// A decision's file gets the day of the decision, and a drawing the purpose of one
		assertThat(candidate.files().detailsOf(new FileReference("11111111.rtf", DOCUMENTS))).isEqualTo(new FileDetails(null, LocalDateTime.of(2019, 4, 20, 0, 0)));
		assertThat(candidate.files().detailsOf(new FileReference("ritning.pdf", ATTACHMENTS))).isEqualTo(new FileDetails(FilePurposes.FLOOR_PLAN, null));
		// The decision the permit points to, then its certificate, may state its legal basis (decision 58)
		assertThat(candidate.asset().legalBasisDocuments()).containsExactly(new FileReference("11111111.rtf", DOCUMENTS), new FileReference("33333333.rtf", DOCUMENTS));
	}

	@Test
	void theServingTimesKeepTheirTextsAndTheDecisionHasThemInTheFieldsOfDraken() {
		final var snapshot = snapshot()
			.permit(permit(1).noTimes().scope(true, false, false, false).time("11:00", "01:00", "Inomhus").time("11:00", "22:00", "Uteservering").build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "556677-8899"))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(parameters(candidate)).containsEntry("serveringstider", List.of("11:00–01:00 Inomhus", "11:00–22:00 Uteservering"));
		assertThat(candidate.errand().description()).contains("Serveringstider: 11:00–01:00 Inomhus, 11:00–22:00 Uteservering");
		assertThat(candidate.asset().parameters()).containsEntry("servingHours", "11:00–01:00 Inomhus\n11:00–22:00 Uteservering");
		assertThat(candidate.decision().decided()).extracting(Parameter::key, Parameter::values).containsExactly(
			tuple("decidedBeverages", List.of("STARKOL", "VIN")),
			tuple("decidedServingHoursIndoorPublic", List.of("11:00", "01:00")),
			tuple("decidedServingHoursOutdoorPublic", List.of("11:00", "22:00")));
		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).doesNotContain(ReviewRule.SERVING_TIME_UNPLACED);
	}

	@Test
	void theErrandOfAPermitGetsTheDecisionThePermitPointsTo() {
		final var candidate = permitsWithoutAnyRuleHit().candidates().getFirst();

		final var decision = candidate.decision();
		// AlkT names nobody who made it, and the decision has no days of its own, which the permit then gives (decision 70)
		assertThat(decision.decidedBy()).isEqualTo("Uppgift saknas i AlkT");
		assertThat(decision.decidedAt()).isEqualTo(LocalDateTime.of(2019, 4, 20, 0, 0));
		assertThat(decision.description()).isEqualTo("Serveringstillstånd");
		assertThat(decision.justification()).isNull();
		assertThat(decision.validFrom()).isEqualTo(candidate.asset().issued());
		assertThat(decision.validTo()).isEqualTo(candidate.asset().validTo());
		// The conditions that still apply are its terms, and its document and attachment the files it links
		assertThat(decision.terms()).containsExactly("Krav på bordsservering: Bord");
		assertThat(decision.documents()).containsExactly(new FileReference("11111111.rtf", DOCUMENTS), new FileReference("bilaga.pdf", ATTACHMENTS));
	}

	@Test
	void theDecisionOfAPermitIsMadeByTheHandlerAlkTNamesAndValidForTheDaysItGives() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).build())
			.permit(permit(2).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 10, "12345679"))
			.owner(owner(10, "556677-8899"))
			.decision(new DecisionRow(100, 500, "0100", LocalDateTime.of(2019, 4, 20, 14, 0), LocalDate.of(2019, 5, 1), LocalDate.of(2025, 4, 30), null, null, null, "Tillstånd beviljas.",
				"ABE"))
			.errand(AlktTestRows.errand(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		final var decision = candidates.stream().filter(candidate -> candidate.key().sourceId().equals("1")).findFirst().orElseThrow().decision();
		assertThat(decision.decidedBy()).isEqualTo("Anna Berg");
		assertThat(decision.decidedAt()).isEqualTo(LocalDateTime.of(2019, 4, 20, 14, 0));
		assertThat(decision.justification()).isEqualTo("Tillstånd beviljas.");
		assertThat(decision.validFrom()).isEqualTo(LocalDate.of(2019, 5, 1));
		assertThat(decision.validTo()).isEqualTo(LocalDate.of(2025, 4, 30));
		assertThat(decision.terms()).isEmpty();
		assertThat(decision.documents()).isEmpty();
		// A permit that points to no decision gets one built from the permit (decision 75)
		assertThat(candidates.stream().filter(candidate -> candidate.key().sourceId().equals("2")).findFirst().orElseThrow().decision().description())
			.isEqualTo("Beslutet finns inte i AlkT och bygger på tillståndet");
	}

	@Test
	void theErrandOfAPermitGetsTheDecisionThatGrantedItRatherThanALaterOneThatExtendedIt() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(102).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "556677-8899"))
			// In the errand of the permit's diary number, a temporary permit until the decision, and the decision, a grant in
			// part
			.decision(new DecisionRow(100, 500, "0231", LocalDateTime.of(2019, 3, 1, 9, 0), null, LocalDate.of(2019, 4, 30), null, null, null, null, null))
			.decision(new DecisionRow(101, 500, "0110", LocalDateTime.of(2019, 4, 20, 14, 0), null, null, null, null, null, "Tillstånd beviljas delvis.", "ABE"))
			// A later temporary permit for a closed company beside it, which is not the grant of the permit
			.decision(new DecisionRow(103, 500, "0235", LocalDateTime.of(2020, 6, 1, 9, 0), null, LocalDate.of(2020, 6, 2), null, null, null, null, null))
			// The extension the permit points to, in an errand of its own
			.decision(new DecisionRow(102, 501, "0200", LocalDateTime.of(2021, 6, 1, 10, 0), null, null, null, null, null, "Utökning beviljas.", "ABE"))
			.errand(AlktTestRows.errand(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.errand(AlktTestRows.errand(501, 1, "01", "IAN-2021-7", LocalDate.of(2021, 5, 1), LocalDate.of(2021, 6, 2), null))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		final var decision = candidate.decision();
		assertThat(decision.decidedAt()).isEqualTo(LocalDateTime.of(2019, 4, 20, 14, 0));
		assertThat(decision.description()).isEqualTo("Tillstånd allmänheten, delvis bifall");
		assertThat(decision.justification()).isEqualTo("Tillstånd beviljas delvis.");
		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).doesNotContain(GRANT_DECISION_MISSING);
		// The errand still tells the latest decision
		assertThat(parameters(candidate)).containsEntry("senasteBeslut", List.of("Utökat tillstånd, bifall, 2021-06-01"));
	}

	@Test
	void theGrantThePermitPointsToCountsWithoutAnErrandAndATemporaryGrantOnlyWithoutAnother() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).build())
			.permit(permit(2).beslutsId(200).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 10, "12345679"))
			.owner(owner(10, "556677-8899"))
			.decision(new DecisionRow(100, null, "0100", LocalDateTime.of(2018, 3, 1, 9, 0), null, null, null, null, null, null, null))
			.decision(new DecisionRow(200, 600, "0232", LocalDateTime.of(2026, 5, 1, 9, 0), null, LocalDate.of(2026, 12, 31), null, null, null, null, null))
			.errand(AlktTestRows.errand(600, 2, "01", "IAN-2019-2", LocalDate.of(2026, 4, 1), LocalDate.of(2026, 5, 2), null))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates.stream().filter(candidate -> candidate.key().sourceId().equals("1")).findFirst().orElseThrow().decision().decidedAt())
			.isEqualTo(LocalDateTime.of(2018, 3, 1, 9, 0));
		final var temporary = candidates.stream().filter(candidate -> candidate.key().sourceId().equals("2")).findFirst().orElseThrow();
		assertThat(temporary.decision().decidedAt()).isEqualTo(LocalDateTime.of(2026, 5, 1, 9, 0));
		assertThat(temporary.decision().validTo()).isEqualTo(LocalDate.of(2026, 12, 31));
		assertThat(temporary.ruleHits()).extracting(RuleHit::rule).doesNotContain(GRANT_DECISION_MISSING);
	}

	@Test
	void aPermitWithoutADecisionThatGrantedItGetsOneBuiltFromThePermit() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(102).build())
			.permit(permit(2).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 10, "12345679"))
			.owner(owner(10, "556677-8899"))
			.decision(new DecisionRow(102, 501, "0200", LocalDateTime.of(2021, 6, 1, 10, 0), null, null, null, null, null, "Utökning beviljas.", "ABE"))
			.errand(AlktTestRows.errand(501, 1, "01", "IAN-2021-7", LocalDate.of(2021, 5, 1), LocalDate.of(2021, 6, 2), null))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		final var extended = candidates.stream().filter(candidate -> candidate.key().sourceId().equals("1")).findFirst().orElseThrow();
		assertThat(extended.decision()).usingRecursiveComparison().ignoringFields("decided").isEqualTo(new DecisionData("Uppgift saknas i AlkT", LocalDateTime.of(2019, 5, 1, 0, 0),
			"Beslutet finns inte i AlkT och bygger på tillståndet", null, LocalDate.of(2019, 5, 1), extended.asset().validTo(), List.of(), List.of()));
		assertThat(extended.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(GRANT_DECISION_MISSING, "tillståndet pekar på Utökat tillstånd, bifall, 2021-06-01"));
		final var without = candidates.stream().filter(candidate -> candidate.key().sourceId().equals("2")).findFirst().orElseThrow();
		assertThat(without.decision().decidedAt()).isEqualTo(LocalDateTime.of(2019, 5, 1, 0, 0));
		assertThat(without.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(GRANT_DECISION_MISSING, null));
	}

	/**
	 * A permit with its place, owner, decision, errands, files, persons and conditions, that hits no review rule.
	 */
	private static ReaderResult permitsWithoutAnyRuleHit() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).gtAgarId(10).gtRestaurantNumber("12345678").scope(true, false, false, false).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "556677-8899"))
			.decision(AlktTestRows.decision(100, 500, "0100", LocalDate.of(2019, 4, 20).atStartOfDay(), null, "TDB", "11111111.rtf", "bilaga.pdf"))
			.errand(AlktTestRows.errand(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.errand(AlktTestRows.errand(501, 1, "01", "ian 2019 1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.errand(AlktTestRows.errand(502, 2, "01", "IAN-2019-1", null, null, null))
			.file(AlktTestRows.file(500, "22222222.rtf", true))
			.file(AlktTestRows.file(501, "ansokan.pdf", false))
			.file(AlktTestRows.file(502, "annat-stalle.pdf", false))
			.document(new DocumentRow(1, 500, null, "TDBV2", LocalDate.of(2019, 5, 2).atStartOfDay(), "33333333.rtf"))
			.person(new PersonRow(10, "Person Ett", "800101-1234", List.of("VD"), false))
			.person(new PersonRow(10, "Person Två", null, List.of(), true))
			.person(new PersonRow(10, "Ägarbolaget AB", "559988-7766", List.of(), false))
			.person(new PersonRow(10, "Person Tre", "800101", List.of(), false))
			.person(new PersonRow(10, "Person Fyra", "900101-1239", List.of(), true))
			.person(new PersonRow(10, "Person Fem", "900101-1234", List.of(), true))
			.person(new PersonRow(10, "Utländskt Bolag", "559988-7766", List.of(), true))
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
			.decision(AlktTestRows.decision(100, null, "0100", LocalDate.of(2019, 4, 20).atStartOfDay(), LocalDate.of(2026, 1, 1), null, null, null))
			.condition(new ConditionRow(1, null, "Villkor", "då och då", "tills vidare"))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(
			OWNER_DIFFERS, DECISION_EXPIRED, PERIOD_PASSED, PERIOD_ON_ALL_YEAR, RESTAURANT_NUMBER_DIFFERS, LEGAL_ID_INVALID, UNPARSEABLE, UNPARSEABLE, UNPARSEABLE, NO_ISSUING_ERRAND,
			DIARY_NUMBER_MISSING, ISSUE_DATE_MISSING, SUBTYPE_MISSING, CERTIFICATE_MISSING);
		assertThat(candidate.ruleHits()).extracting(RuleHit::detail).contains(
			"AgarID i tillståndet 11, på serveringsstället 10",
			"beslutstyp 0100, giltigt till 2026-01-01",
			"2026-01-01 – 2026-03-31",
			"på tillståndet 87654321, på serveringsstället 12345678",
			"serveringstid 2 '10:00–'",
			"villkorets slutdatum 'tills vidare'",
			"villkorets startdatum 'då och då'");
		assertThat(candidate.errand().labelPaths()).isEmpty();
		assertThat(candidate.holder().legalId()).isNull();
		assertThat(parameters(candidate)).containsEntry("serveringstider", List.of("11:00–01:00", "10:00–"))
			.containsEntry("villkor", List.of("Villkor"))
			.containsEntry("period", List.of("2026-01-01 – 2026-03-31"));
	}

	@Test
	void aFileNameThatAnotherPlaceAlsoRefersToIsMarked() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "5566778899"))
			.decision(AlktTestRows.decision(100, 500, "0100", LocalDate.of(2019, 4, 20).atStartOfDay(), null, null, null, "beslut.pdf"))
			.decision(AlktTestRows.decision(200, null, "0100", null, null, null, null, "Beslut.pdf"))
			.errand(AlktTestRows.errand(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.errand(AlktTestRows.errand(501, 1, "07", "TIL-2020-9", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null))
			.errand(AlktTestRows.errand(600, 2, "07", "TIL-2021-7", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.file(AlktTestRows.file(500, "ansokan.pdf", false))
			.file(AlktTestRows.file(500, "intyg.pdf", false))
			.file(AlktTestRows.file(501, "intyg.pdf", false))
			.file(AlktTestRows.file(600, "ANSOKAN.pdf", false))
			// An event with neither an errand nor a place counts as a place of its own
			.file(AlktTestRows.file(500, "kvitto.pdf", false))
			.unplacedFile(77, AlktTestRows.file(null, "KVITTO.pdf", false))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.files().references()).containsExactlyInAnyOrder(
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
			.decision(AlktTestRows.decision(100, 500, "0100", LocalDate.of(2019, 12, 31).atStartOfDay(), null, "TDB", "44444444.rtf", "Planritning.PDF"))
			.decision(AlktTestRows.decision(101, 600, "0100", LocalDate.of(2015, 1, 31).atStartOfDay(), null, "TDBBS", "55555555.rtf", null))
			.errand(AlktTestRows.errand(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 12, 1), LocalDate.of(2020, 1, 1), null))
			.errand(AlktTestRows.errand(600, 1, "02", "IAN-2015-6", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), null))
			.file(new FileRow(500, "situationsritning.pdf", false, new FileDetails(FilePurposes.FLOOR_PLAN, null)))
			// A referral answer named as a drawing stays on the errand
			.file(new FileRow(500, "ritning yttrande.pdf", false, new FileDetails(FilePurposes.RESCUE_SERVICE_RESPONSE, null)))
			.file(AlktTestRows.file(600, "gammal ritning.pdf", false))
			// Two certificates of the same day, the one without an errand made after the other
			.document(new DocumentRow(10, 600, null, "TDBV2", LocalDate.of(2020, 6, 1).atStartOfDay(), "66666666.rtf"))
			.document(new DocumentRow(11, null, 1, "tdbv2", LocalDate.of(2020, 6, 1).atStartOfDay(), "77777777.rtf"))
			.document(new DocumentRow(12, 500, null, "TDBB", LocalDate.of(2018, 1, 1).atStartOfDay(), "88888888.rtf"))
			.document(new DocumentRow(13, 600, null, "TDBBC", LocalDate.of(2020, 2, 1).atStartOfDay(), "99999999.rtf"))
			.document(new DocumentRow(14, 600, null, "TDBBS", LocalDate.of(2020, 3, 1).atStartOfDay(), "12121212.rtf"))
			.document(new DocumentRow(15, null, 2, "TDBV2", LocalDate.of(2024, 1, 1).atStartOfDay(), "13131313.rtf"))
			// An annex of catering on a permit without catering, and an annex older than the permit
			.document(new DocumentRow(16, null, 2, "TDBBC", LocalDate.of(2024, 1, 1).atStartOfDay(), "14141414.rtf"))
			.document(new DocumentRow(17, null, 2, "TDBBS", LocalDate.of(2018, 1, 1).atStartOfDay(), "15151515.rtf"))
			.placeFile(1, AlktTestRows.file(null, "anmalan serveringsansvarig.pdf", false))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		final var candidate = candidates.getFirst();
		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).doesNotContain(CERTIFICATE_MISSING, CERTIFICATE_OLDER);
		assertThat(candidate.files().onPost()).containsExactly(
			new AssetFile(new FileReference("77777777.rtf", DOCUMENTS), "Tillståndsbevis"),
			new AssetFile(new FileReference("12121212.rtf", DOCUMENTS), "Beslutsbilaga"),
			new AssetFile(new FileReference("99999999.rtf", DOCUMENTS), "Beslutsbilaga"),
			new AssetFile(new FileReference("situationsritning.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("Planritning.PDF", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("ritning.pdf", ATTACHMENTS, true), "Ritning"),
			new AssetFile(new FileReference("44444444.rtf", DOCUMENTS), "Beslut"));
		assertThat(candidate.files().references()).containsExactly(
			new FileReference("situationsritning.pdf", ATTACHMENTS),
			new FileReference("ritning yttrande.pdf", ATTACHMENTS),
			new FileReference("44444444.rtf", DOCUMENTS),
			new FileReference("Planritning.PDF", ATTACHMENTS),
			new FileReference("ritning.pdf", ATTACHMENTS, true),
			new FileReference("77777777.rtf", DOCUMENTS),
			new FileReference("12121212.rtf", DOCUMENTS),
			new FileReference("99999999.rtf", DOCUMENTS));
		assertThat(candidate.files().detailsOf(new FileReference("77777777.rtf", DOCUMENTS)))
			.as("A certificate of no errand, or of another errand than the issuing one, gets its purpose and time too")
			.isEqualTo(new FileDetails(FilePurposes.ofDocument(ALKT, "tdbv2"), LocalDateTime.of(2020, 6, 1, 0, 0)));
		assertThat(candidate.files().detailsOf(new FileReference("12121212.rtf", DOCUMENTS)).received()).isEqualTo(LocalDateTime.of(2020, 3, 1, 0, 0));

		final var withoutCatering = candidates.get(1);
		assertThat(withoutCatering.files().onPost()).extracting(AssetFile::category).containsExactly("Tillståndsbevis", "Ritning");
		assertThat(withoutCatering.files().references()).extracting(FileReference::name).doesNotContain("14141414.rtf", "15151515.rtf");
		assertThat(candidate.asset().legalBasisDocuments()).containsExactly(new FileReference("44444444.rtf", DOCUMENTS), new FileReference("77777777.rtf", DOCUMENTS));
		assertThat(withoutCatering.asset().legalBasisDocuments()).containsExactly(new FileReference("13131313.rtf", DOCUMENTS));
	}

	@Test
	void onlyThePermitDecisionsOfTheIssuingErrandGoOnThePost() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "5566778899"))
			.decision(AlktTestRows.decision(100, 500, "0100", LocalDate.of(2019, 4, 20).atStartOfDay(), null, "tbt", "11111111.rtf", "Beslut 2019.pdf"))
			.decision(AlktTestRows.decision(101, 500, "0200", LocalDate.of(2019, 4, 20).atStartOfDay(), null, "TDU", "22222222.rtf", "remissvar räddningstjänsten.pdf"))
			.decision(AlktTestRows.decision(102, 500, "D 02", LocalDate.of(2019, 4, 21).atStartOfDay(), null, null, null, "Registreringsbevis.pdf"))
			.decision(AlktTestRows.decision(103, 500, "Deleg", LocalDate.of(2019, 4, 22).atStartOfDay(), null, null, null, "Beslutsunderlag.pdf"))
			.errand(AlktTestRows.errand(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.files().references()).extracting(FileReference::name).contains(
			"11111111.rtf", "Beslut 2019.pdf", "22222222.rtf", "remissvar räddningstjänsten.pdf", "Registreringsbevis.pdf", "Beslutsunderlag.pdf");
		// An investigation, a referral answer, a registration certificate and the basis of a decision go on the errand only
		assertThat(candidate.files().onPost()).filteredOn(file -> "Beslut".equals(file.category())).extracting(file -> file.reference().name())
			.containsExactly("11111111.rtf", "Beslut 2019.pdf");
		assertThat(candidate.files().onPost()).extracting(file -> file.reference().name()).doesNotContain("Registreringsbevis.pdf");
		// The attachment of a decision gets a purpose by its name as well (decision 86)
		assertThat(candidate.files().detailsOf(new FileReference("Registreringsbevis.pdf", ATTACHMENTS)).purpose()).isEqualTo(FilePurposes.CERTIFICATE_OF_REGISTRATION);
		// Only the document made from the template of a permit decision states the legal basis
		assertThat(candidate.asset().legalBasisDocuments()).containsExactly(new FileReference("11111111.rtf", DOCUMENTS));
	}

	@Test
	void aCertificateOlderThanThePermitOrWithoutADateGoesOnTheErrandOnly() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(100).issued(LocalDate.of(2022, 6, 1)).build())
			.permit(permit(2).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 10, "12345679"))
			.owner(owner(10, "5566778899"))
			.errand(AlktTestRows.errand(500, 1, "01", "IAN-2015-1", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), null))
			.decision(AlktTestRows.decision(100, 500, "0100", LocalDate.of(2015, 2, 1).atStartOfDay(), null, "TDB", "33333333.rtf", null))
			// The certificate of the former holder is also a decision document, which would otherwise go on the post
			.document(new DocumentRow(10, 500, null, "TDBV2", LocalDate.of(2015, 2, 1).atStartOfDay(), "33333333.rtf"))
			.document(new DocumentRow(11, null, 2, "TDBV2", null, "44444444.rtf"))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.contains(tuple(CERTIFICATE_OLDER, "beviset är från 2015-02-01, tillståndet utfärdades 2022-06-01"));
		assertThat(candidates.get(0).files().references()).contains(new FileReference("33333333.rtf", DOCUMENTS));
		assertThat(candidates.get(0).files().onPost()).extracting(file -> file.reference().name()).doesNotContain("33333333.rtf");
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(CERTIFICATE_OLDER, "beviset saknar datum"));
		assertThat(candidates.get(1).files().references()).contains(new FileReference("44444444.rtf", DOCUMENTS));
		assertThat(candidates.get(1).files().onPost()).extracting(file -> file.reference().name()).doesNotContain("44444444.rtf");
		// A document that may be a former holder's does not state the legal basis of the permit, even as its decision
		assertThat(candidates.get(0).asset().legalBasisDocuments()).isEmpty();
		assertThat(candidates.get(1).asset().legalBasisDocuments()).isEmpty();
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

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(PLACE_MISSING, NO_ISSUING_ERRAND, GRANT_DECISION_MISSING, CERTIFICATE_MISSING);
		assertThat(candidate.holder()).isNull();
		assertThat(candidate.place()).isNull();
		assertThat(candidate.errand().title()).isEqualTo("Serveringstillstånd – okänt serveringsställe");
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
			.decision(AlktTestRows.decision(100, 500, "0100", LocalDate.of(2019, 4, 20).atStartOfDay(), null, null, null, null))
			.errand(AlktTestRows.errand(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 1, 1), LocalDate.of(2019, 5, 1), null))
			.errand(AlktTestRows.errand(501, 1, "01", "ian 2019 1", LocalDate.of(2019, 1, 1), null, null))
			.errand(AlktTestRows.errand(502, 2, "01", "IAN-2019-1", null, null, null))
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

		// One item per place, built from its first row, since the rows would share their key
		assertThat(candidates).singleElement().satisfies(candidate -> assertThat(candidate.ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.contains(tuple(PERMIT_ROWS_ON_PLACE, "2 rader"), tuple(DECISION_MISSING, "BeslutsID 999")));
	}

	@Test
	void theRowsOfOnePlaceWithTheSameDiaryNumberCountOnceForG16() {
		final var snapshot = snapshot()
			.permit(permit(1).diaryNumber("IAN-2019-1").build())
			.permit(permit(1).diaryNumber("IAN-2019-1").build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "5566778899"))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule).contains(PERMIT_ROWS_ON_PLACE).doesNotContain(DIARY_NUMBER_ON_SEVERAL);
	}

	@Test
	void anOwnerThatIsMissingIsToldApartFromAPlaceWithoutOwner() {
		final var snapshot = snapshot().permit(permit(1).build()).place(place(1, 123, "12345678")).build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(LEGAL_ID_INVALID, "AgarID 123 finns inte"));
	}

	@Test
	void twoOwnerPostsWithTheSameNumberAreTheSameHolder() {
		final var snapshot = snapshot()
			.permit(permit(1).gtAgarId(11).build())
			.permit(permit(2).gtAgarId(12).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 10, "12345679"))
			.owner(owner(10, "5566778899"))
			.owner(owner(11, "556677-8899"))
			.owner(owner(12, "5599887766"))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule).doesNotContain(OWNER_DIFFERS);
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::rule).contains(OWNER_DIFFERS);
	}

	@Test
	void anIssuingErrandThatIsMissingHitsG13() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(500).build())
			.decision(AlktTestRows.decision(500, 777, "TDB", LocalDateTime.of(2019, 5, 1, 10, 0), null, null, null, null))
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "5566778899"))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(NO_ISSUING_ERRAND, "ArendeID 777 finns inte i Ärende"));
	}

	@Test
	void aDatePeriodOpenAtOneEndCannotBeInterpreted() {
		final var snapshot = snapshot()
			.permit(permit(1).aretRunt(false).period("2026-11-01", null).build())
			.permit(permit(2).aretRunt(false).period(null, "2026-11-30").build())
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "perioden '2026-11-01 –' saknar slut"));
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "perioden '– 2026-11-30' saknar början"));
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

		assertThat(candidates.get(0).errand().labelPaths()).containsExactly("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TEMPORARY_SERVING_PUBLIC");
		assertThat(candidates.get(0).asset().validTo()).isEqualTo(LocalDate.of(2026, 11, 30));
		assertThat(candidates.get(1).errand().labelPaths()).containsExactly("CATEGORYROOT/ALCOHOL/SERVING_PERMIT_APPLICATION/TEMPORARY_SERVING_PRIVATE");
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

		assertThat(candidates).extracting(candidate -> candidate.errand().labelPaths().getFirst()).containsExactly(
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

		assertThat(parameters(candidates.get(0))).containsEntry("sasong", List.of("05-01 – 09-30"));
		// A period as Draken writes it on the permit
		assertThat(candidates.get(0).asset().parameters()).containsEntry("scopePeriodFromTo", "05-01 – 09-30");
		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule).doesNotContain(UNPARSEABLE);
		assertThat(parameters(candidates.get(1))).containsEntry("sasong", List.of("05-01 – 09-30")).doesNotContainKey("period");
		assertThat(parameters(candidates.get(2))).containsEntry("sasong", List.of("vår – höst"));
		assertThat(candidates.get(2).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "säsong 'vår – höst'"));
		assertThat(parameters(candidates.get(3))).containsEntry("period", List.of("maj – 2026-09-30"));
		assertThat(candidates.get(3).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "period 'maj – 2026-09-30'"));
		assertThat(candidates.get(4).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "perioden '2026-09-30 – 2026-05-01' slutar före sin början"));
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
		final var row = new AlktSnapshot.PermitRow(null, null, new AlktSnapshot.Drinks(false, false, false, false, false), new AlktSnapshot.Period(true, null, null, null, null),
			new AlktSnapshot.Scope(false, false, false, false, false, false, false, false, false), true, false, List.of(), new AlktSnapshot.ServingArea(null, null, null), List.of(), null, null, null, "IAN-1", null,
			null);
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
			.errand(AlktTestRows.errand(700, 1, "07", "TIL-2020-1", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 3, 1), 70, List.of("Klagomål från granne", "Uppföljning i mars"),
				List.of("ABE", "ZZ")))
			.errand(AlktTestRows.errand(701, 1, "09", null, LocalDate.of(2021, 1, 1), null, 71))
			.errand(AlktTestRows.errand(702, 2, "07", null, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.errand(AlktTestRows.errand(703, 1, "01", null, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.decision(new DecisionRow(800, 700, "ER", LocalDate.of(2020, 2, 1).atStartOfDay(), null, null, null, "33333333.rtf", null, "Erinran meddelas.", "ABE"))
			.file(AlktTestRows.file(700, "protokoll.pdf", false))
			.errandText(700, new TextRow(LocalDateTime.of(2020, 1, 20, 9, 0), "ANT", false, "Ringde bolaget.", true, "til-2020-1"))
			// The event was registered in another case
			.errandText(700, new TextRow(LocalDateTime.of(2020, 1, 22, 0, 0), "HAND", false, "Från annat ärende", false, "TIL-2019-9"))
			.errandText(700, AlktTestRows.text(LocalDateTime.of(2020, 1, 25, 0, 0), "REMP", false, null))
			.errandText(700, AlktTestRows.text(LocalDateTime.of(2020, 1, 10, 14, 0), "UTSKR", false, "Brev om tillsyn"))
			.errandText(700, AlktTestRows.text(null, "ANT", false, "Utan datum"))
			.errandText(700, AlktTestRows.text(LocalDateTime.of(2020, 1, 15, 0, 0), null, true, "Fasaden"))
			.errandText(702, AlktTestRows.text(LocalDateTime.of(2021, 1, 2, 0, 0), "ANT", false, "Annat ställe"))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).inspections().candidates();

		assertThat(candidates).extracting(Candidate::key).containsExactlyInAnyOrder(new ItemKey(ALKT, TILLSYN, "700"), new ItemKey(ALKT, TILLSYN, "701"));
		final var closed = candidates.stream().filter(candidate -> candidate.key().sourceId().equals("700")).findFirst().orElseThrow();
		assertThat(closed.type()).isEqualTo(ErrandType.ALCOHOL_INSPECTION);
		assertThat(closed.ruleHits()).isEmpty();
		assertThat(closed.errand().title()).isEqualTo("Tillsyn, ifrågasatt åtgärd – Restaurang 1");
		assertThat(closed.errand().labelPaths()).containsExactly("TAGROOT/ALCOHOL_INSPECTION", "CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION");
		assertThat(closed.errand().externalTags()).containsEntry("diarienummer", "TIL-2020-1");
		assertThat(closed.asset()).isNull();
		assertThat(closed.permitLink().permitKey()).isEqualTo(new ItemKey(ALKT, TILLSTAND, "1"));
		assertThat(closed.permitLink().basis()).isEqualTo(Basis.SAME_HOLDER);
		assertThat(closed.holder().name()).isEqualTo("Sökande 70 AB");
		// A handler AlkT has no name for stands with the code
		assertThat(parameters(closed)).containsEntry("beslut", List.of("Erinran, 2020-02-01")).containsEntry("arendetyp", List.of("Tillsyn, ifrågasatt åtgärd"))
			.containsEntry("handlaggare", List.of("Anna Berg", "ZZ"));
		assertThat(closed.errand().description()).startsWith("Tillsyn, ifrågasatt åtgärd.\n").contains("\nHandläggare: Anna Berg, ZZ\n");
		assertThat(closed.files().references()).containsExactly(new FileReference("protokoll.pdf", ATTACHMENTS), new FileReference("33333333.rtf", DOCUMENTS));
		// The events are in order of time, an event without a time first, also one without a text, a confidential one is
		// marked, a picture is headed "Bild", and a decision tells who made it
		assertThat(closed.investigation())
			.isEqualTo(new InvestigationData("Tillsyn, ifrågasatt åtgärd – Restaurang 1", LocalDateTime.of(2020, 1, 1, 0, 0), LocalDateTime.of(2020, 3, 1, 0, 0),
				InvestigationTexts.of(new Part("Noteringar", "Klagomål från granne\nUppföljning i mars"), new Part("Händelser", """
					Anteckning: Utan datum
					2020-01-10 UTSKR: Brev om tillsyn
					2020-01-15 Bild: Fasaden
					2020-01-20 Anteckning (sekretess): Ringde bolaget.
					2020-01-22 HAND (diarienummer TIL-2019-9): Från annat ärende
					2020-01-25 REMP"""), new Part("Beslut", "2020-02-01 Erinran, beslutsfattare Anna Berg: Erinran meddelas.")))
				// Draken shows it as the protocol of an internal inspection (decision 77)
				.withProtocol(InspectionProtocols.INTERNAL_INSPECTION, List.of(
					new Parameter("businessName", "Verksamhetens namn", List.of("Sökande 70 AB")),
					new Parameter("businessOrgNumber", "Organisationsnummer", List.of("556677-8899")),
					new Parameter("businessAddress", "Adress", List.of("Storgatan 1, 85230 Sundsvall")),
					new Parameter("inspectionType", "Typ av tillsyn", List.of("Tillsyn, ifrågasatt åtgärd")),
					new Parameter("inspectionDate", "Datum", List.of("2020-01-01")),
					new Parameter("inspectorName", "Utförd av", List.of("Anna Berg, ZZ")))));
		// The protocol has no room for the texts, so the errand also has them, a line each
		assertThat(parameters(closed))
			.containsEntry("noteringar", List.of("Klagomål från granne", "Uppföljning i mars"))
			.containsEntry("handelser", List.of("Anteckning: Utan datum", "2020-01-10 UTSKR: Brev om tillsyn", "2020-01-15 Bild: Fasaden", "2020-01-20 Anteckning (sekretess): Ringde bolaget.",
				"2020-01-22 HAND (diarienummer TIL-2019-9): Från annat ärende", "2020-01-25 REMP"))
			.containsEntry("beslutstexter", List.of("2020-02-01 Erinran, beslutsfattare Anna Berg: Erinran meddelas."));
		assertThat(closed.errand().parameters()).filteredOn(parameter -> parameter.key().equals("handelser")).extracting(Parameter::displayName).containsExactly("Händelser i AlkT");

		final var open = candidates.stream().filter(candidate -> candidate.key().sourceId().equals("701")).findFirst().orElseThrow();
		// The applicant is not the owner, so the errand concerned a former holder and is not migrated (decision 72)
		assertThat(open.ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.containsExactly(tuple(ERRAND_OPEN, null), tuple(FORMER_HOLDER, "sökanden i ärendet är inte ställets nuvarande ägare"));
		assertThat(open.errand().labelPaths()).containsExactly("TAGROOT/ALCOHOL_INSPECTION");
		assertThat(open.permitLink().basis()).isEqualTo(Basis.OTHER_HOLDER);
		// Still open in the source and without events, so it ended when it started (decision 59)
		assertThat(open.investigation().startedAt()).isEqualTo(LocalDateTime.of(2021, 1, 1, 0, 0));
		assertThat(open.investigation().completedAt()).isEqualTo(LocalDateTime.of(2021, 1, 1, 0, 0));
		assertThat(open.investigation().texts()).isEqualTo(InvestigationTexts.NONE);
		assertThat(parameters(open)).doesNotContainKey("handlaggare");
		assertThat(open.errand().description()).doesNotContain("Handläggare");
	}

	@Test
	void anInspectionErrandStillOpenEndsAtItsLatestEventOrDecision() {
		final var snapshot = snapshot()
			.permit(permit(1).build())
			.place(place(1, 10, "12345678"))
			.errand(AlktTestRows.errand(701, 1, "09", null, LocalDate.of(2021, 1, 1), null, null))
			.decision(new DecisionRow(801, 701, "ER", LocalDateTime.of(2021, 3, 1, 9, 0), null, null, null, null, null, null, null))
			.errandText(701, AlktTestRows.text(LocalDateTime.of(2021, 2, 10, 13, 0), "ANT", false, "Ringde bolaget."))
			.errandText(701, AlktTestRows.text(null, "ANT", false, "Utan datum"))
			.build();

		final var investigation = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).inspections().candidates().getFirst().investigation();

		assertThat(investigation.startedAt()).isEqualTo(LocalDateTime.of(2021, 1, 1, 0, 0));
		assertThat(investigation.completedAt()).isEqualTo(LocalDateTime.of(2021, 3, 1, 9, 0));
	}

	@Test
	void everyPermitGetsAnInvestigationAndTheEventsOfItsPlaceBecomeDocumentationErrands() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(900).build())
			.permit(permit(2).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 10, "12345679"))
			.owner(owner(10, "556677-8899"))
			// The errand of the decision the permit points to, and the errand of the place with the permit's diary number
			.errand(AlktTestRows.errand(501, 1, "01", "IAN-2019-0", LocalDate.of(2019, 1, 15), null, null, List.of(), List.of("ABE")))
			// The errand has the note AlkT copied to the permit, which the permit shows as a parameter already
			.errand(AlktTestRows.errand(500, 1, "01", "IAN-2019-1", LocalDate.of(2019, 2, 1), LocalDate.of(2019, 5, 10), null, List.of("Ansökan komplett", "En notering"),
				List.of("abe", "KLM")))
			.decision(AlktTestRows.decision(900, 501, "0100", LocalDateTime.of(2019, 5, 1, 13, 0), null, null, null, null, "Tillstånd beviljas."))
			.errandText(500, AlktTestRows.text(LocalDateTime.of(2019, 3, 1, 10, 0), "ANT", false, "Remiss skickad"))
			.placeEvent(1, new PlaceEventRow(31020, new TextRow(LocalDateTime.of(2021, 6, 1, 8, 0), "ANT", false, "Ny serveringsansvarig", false, "IAN-2021-1"),
				List.of(new FileRow(null, "bilaga.pdf", false, new FileDetails(null, LocalDateTime.of(2021, 6, 1, 8, 0))))))
			.placeText(1, AlktTestRows.text(LocalDateTime.of(2020, 1, 1, 8, 0), "HAND", false, "Inkommen ritning"))
			.staff(new StaffRow(1, "Per Ek", null))
			.staff(new StaffRow(1, "Anna Berg", LocalDate.of(2021, 6, 1)))
			.staff(new StaffRow(1, "anna berg", LocalDate.of(2015, 3, 1)))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates();

		final var withErrands = candidates.stream().filter(candidate -> candidate.key().sourceId().equals("1")).findFirst().orElseThrow();
		// The serving staff of the place as a table, each person once (decision 79)
		assertThat(withErrands.errand().parameters()).filteredOn(parameter -> parameter.key().equals("serveringsansvariga"))
			.extracting(Parameter::displayName, Parameter::group, Parameter::values)
			.containsExactly(tuple("Namn|Från", "Serveringsansvariga i AlkT", List.of("Anna Berg|2015-03-01", "Per Ek|")));
		// Started when the first issuing errand was opened, completed when the permit was issued
		assertThat(withErrands.investigation()).isEqualTo(new InvestigationData("Serveringstillstånd – Restaurang 1", LocalDateTime.of(2019, 1, 15, 0, 0),
			LocalDateTime.of(2019, 5, 1, 0, 0), InvestigationTexts.of(new Part("Noteringar", "Ansökan komplett"), new Part("Händelser", "2019-03-01 Anteckning: Remiss skickad"),
				new Part("Beslut", "2019-05-01 Serveringstillstånd: Tillstånd beviljas."))));
		// The events of the place without an errand become documentation errands filed under its permit, and the permit has
		// neither their texts nor their files (decision 71)
		assertThat(parameters(withErrands)).doesNotContainKey("handelselogg");
		assertThat(withErrands.files().references()).extracting(FileReference::name).doesNotContain("bilaga.pdf");
		final var documentation = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).documentation().candidates();
		assertThat(documentation).extracting(candidate -> candidate.key().sourceId(), candidate -> candidate.errand().title(), candidate -> candidate.permitLink().permitKey())
			.containsExactly(tuple("10000", "HAND – Restaurang 1", new ItemKey(ALKT, TILLSTAND, "1")), tuple("31020", "Anteckning – Restaurang 1", new ItemKey(ALKT, TILLSTAND, "1")));
		final var withFile = documentation.get(1);
		assertThat(withFile.type()).isEqualTo(ErrandType.ALCOHOL_DOCUMENTATION);
		assertThat(withFile.errand().description()).isEqualTo("""
			Anteckning på Restaurang 1 2021-06-01, som inte hörde till något ärende.
			Diarienummer: IAN-2021-1
			Ny serveringsansvarig""");
		assertThat(withFile.errand().labelPaths()).containsExactly(TestData.DOCUMENTATION);
		assertThat(withFile.files().references()).containsExactly(new FileReference("bilaga.pdf", ATTACHMENTS));
		assertThat(withFile.files().detailsOf(new FileReference("bilaga.pdf", ATTACHMENTS)).received()).isEqualTo(LocalDateTime.of(2021, 6, 1, 8, 0));
		assertThat(withFile.holder().legalId().number()).isEqualTo("5566778899");
		assertThat(withFile.placeNumber()).isEqualTo("12345678");
		assertThat(withFile.investigation()).isNull();
		assertThat(parameters(withFile)).containsEntry("alktHandelseId", List.of("31020")).containsEntry("handelsetyp", List.of("Anteckning"))
			.containsEntry("handelsedatum", List.of("2021-06-01")).containsEntry("diarienummer", List.of("IAN-2021-1")).doesNotContainKey("sekretess");
		// The handlers of the issuing errands, each named once
		assertThat(parameters(withErrands)).containsEntry("handlaggare", List.of("Anna Berg", "KLM")).containsEntry("noteringar", List.of("En notering"));

		final var withoutErrand = candidates.stream().filter(candidate -> candidate.key().sourceId().equals("2")).findFirst().orElseThrow();
		assertThat(withoutErrand.ruleHits()).extracting(RuleHit::rule).contains(NO_ISSUING_ERRAND);
		assertThat(withoutErrand.investigation()).isEqualTo(new InvestigationData("Serveringstillstånd – Restaurang 2", null, LocalDateTime.of(2019, 5, 1, 0, 0), InvestigationTexts.NONE));
		assertThat(parameters(withoutErrand)).doesNotContainKeys("handelselogg", "handlaggare", "serveringsansvariga");
	}

	@Test
	void theTextsOfTheServingTimesAreNoNotesOfTheInvestigation() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(900).noTimes().time("11:00", "01:00", "Inomhus").time("11:00", "22:00", "Uteservering").build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "556677-8899"))
			// AlkT copies the notes of the issuing errand to the permit, where the first two are the texts of its serving times
			.errand(AlktTestRows.errand(501, 1, "01", "IAN-2019-0", LocalDate.of(2019, 1, 15), null, null, List.of("Inomhus", "Uteservering", "En notering", "Ansökan komplett")))
			.decision(AlktTestRows.decision(900, 501, "0100", LocalDateTime.of(2019, 5, 1, 13, 0), null, null, null, null))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.investigation().texts().parts()).filteredOn(part -> part.heading().equals("Noteringar")).extracting(Part::text).containsExactly("Ansökan komplett");
	}

	@Test
	void aGrantWithoutATimeRegisteredAfterTheChosenOneAndAGrantAfterTheMigrationHitG9() {
		final var undated = snapshot()
			.permit(permit(1).beslutsId(900).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "556677-8899"))
			.errand(AlktTestRows.errand(501, 1, "01", "IAN-2019-0", LocalDate.of(2019, 1, 15), null, null))
			.decision(AlktTestRows.decision(900, 501, "0100", LocalDateTime.of(2019, 5, 1, 13, 0), null, null, null, null))
			// A grant without a time loses to one with, but was registered later and may be the latest; an earlier one is no hit
			.decision(AlktTestRows.decision(899, 501, "0110", null, null, null, null, null))
			.decision(AlktTestRows.decision(901, 501, "0110", null, null, null, null, null))
			.build();
		final var future = snapshot()
			.permit(permit(1).beslutsId(900).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "556677-8899"))
			.errand(AlktTestRows.errand(501, 1, "01", "IAN-2019-0", LocalDate.of(2019, 1, 15), null, null))
			.decision(AlktTestRows.decision(900, 501, "0100", LocalDateTime.of(2062, 4, 20, 9, 0), null, null, null, null))
			.build();

		final var withUndated = new AlktCandidateFactory(undated, TODAY, PROPERTIES).permits().candidates().getFirst();
		final var withFuture = new AlktCandidateFactory(future, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(withUndated.ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.containsOnlyOnce(tuple(UNPARSEABLE, "det beviljande beslutet 901 saknar datum och kan vara det senaste"))
			.doesNotContain(tuple(UNPARSEABLE, "det beviljande beslutet 899 saknar datum och kan vara det senaste"));
		assertThat(withUndated.decision().decidedAt()).isEqualTo(LocalDateTime.of(2019, 5, 1, 13, 0));
		// A mistyped year: the decision is made the day the permit was issued instead
		assertThat(withFuture.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "beslutet 900 har datumet 2062-04-20, efter migreringsdagen"));
		assertThat(withFuture.decision().decidedAt()).isEqualTo(LocalDateTime.of(2019, 5, 1, 0, 0));
	}

	@Test
	void theDecisionThePermitPointsToBringsItsOwnFilesWhenItHasNoErrand() {
		final var snapshot = snapshot()
			.permit(permit(1).beslutsId(900).build())
			.place(place(1, 10, "12345678"))
			.owner(owner(10, "556677-8899"))
			.decision(AlktTestRows.decision(900, null, "0100", LocalDateTime.of(2019, 5, 1, 13, 0), null, "TDB", "55555555.rtf", "tillstandsbeslut.pdf"))
			.build();

		final var candidate = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).permits().candidates().getFirst();

		assertThat(candidate.files().references()).contains(new FileReference("55555555.rtf", DOCUMENTS), new FileReference("tillstandsbeslut.pdf", ATTACHMENTS));
		assertThat(candidate.files().onPost()).contains(
			new AssetFile(new FileReference("55555555.rtf", DOCUMENTS), "Beslut"),
			new AssetFile(new FileReference("tillstandsbeslut.pdf", ATTACHMENTS), "Beslut"));
		// The decision of the errand links its files, and the legal basis is read from its document (decision 58)
		assertThat(candidate.decision().documents()).containsExactly(new FileReference("55555555.rtf", DOCUMENTS), new FileReference("tillstandsbeslut.pdf", ATTACHMENTS));
		assertThat(candidate.asset().legalBasisDocuments()).contains(new FileReference("55555555.rtf", DOCUMENTS));
	}

	@Test
	void theEventsBeforeTheFirstErrandOfTheOwnerWereAFormerHoldersWhenThePlaceHadOne() {
		final var snapshot = snapshot()
			.permit(permit(1).build())
			.permit(permit(2).build())
			.place(place(1, 10, "12345678"))
			.place(place(2, 10, "12345679"))
			.owner(owner(10, "556677-8899"))
			.applicant(AlktTestData.applicant(70, "5566778899"))
			.applicant(AlktTestData.applicant(71, "5599887766"))
			.errand(AlktTestRows.errand(600, 1, "01", null, LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), 71))
			.errand(AlktTestRows.errand(601, 1, "01", null, LocalDate.of(2019, 1, 1), LocalDate.of(2019, 3, 1), 70))
			.placeText(1, AlktTestRows.text(LocalDateTime.of(2018, 6, 1, 8, 0), "ANT", false, "Förra ägaren ringde"))
			.placeText(1, AlktTestRows.text(LocalDateTime.of(2019, 1, 1, 8, 0), "ANT", false, "Nya ägaren ringde"))
			// A place where only the owner had errands has nothing of a former holder, however old
			.errand(AlktTestRows.errand(602, 2, "01", null, LocalDate.of(2019, 1, 1), LocalDate.of(2019, 3, 1), 70))
			.placeText(2, AlktTestRows.text(LocalDateTime.of(2010, 1, 1, 8, 0), "ANT", false, "Tidig fråga"))
			.build();

		final var documentation = new AlktCandidateFactory(snapshot, TODAY, PROPERTIES).documentation().candidates();

		assertThat(documentation).extracting(candidate -> candidate.key().sourceId(), Candidate::ruleHits).containsExactly(
			tuple("10000", List.of(RuleHit.of(FORMER_HOLDER, "händelsen var 2018-06-01, och stället hade en tidigare ägare till och med 2018-12-31"))),
			tuple("10001", List.of()),
			tuple("10002", List.of()));
	}

	@Test
	void inspectionErrandsOfAllPlaces() {
		final var snapshot = snapshot()
			.errand(AlktTestRows.errand(702, 2, "07", null, LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, TestData.properties(ErrandType.ALCOHOL_INSPECTION, false, Map.of("07", "X"))).inspections().candidates();

		assertThat(candidates).singleElement().satisfies(candidate -> {
			assertThat(candidate.ruleHits()).extracting(RuleHit::rule).containsExactly(ReviewRule.PLACE_MISSING);
			assertThat(candidate.permitLink().permitKey()).isNull();
			assertThat(candidate.holder()).isNull();
			assertThat(candidate.errand().title()).isEqualTo("Tillsyn, ifrågasatt åtgärd – okänt serveringsställe");
			assertThat(candidate.errand().labelPaths()).containsExactly("TAGROOT/ALCOHOL_INSPECTION", "X");
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
			// The code of a handler AlkT has no name for stands as it is, and a handler named twice is named once
			.visit(new VisitRow(900, 1, LocalDate.of(2023, 11, 4), null, "21.30", "23", "04", "Mycket folk i baren.", "66666666.rtf", "protokoll.pdf", List.of("ABE", "XYZ", "abe"),
				new StaffMarks(true, false, true, false), 40, List.of(), false))
			.visit(VisitTestRows.visit(901, 2, LocalDate.of(2023, 11, 4), null, null, "04", null, null, "PROTOKOLL.pdf"))
			.visit(VisitTestRows.visit(902, 1, null, null, null, null, null, null, null))
			// The round of the visit is missing
			.visit(new VisitRow(903, 5, LocalDate.of(2022, 3, 1), null, "09:15:00", "10:00:00", "99", null, null, null, List.of(), null, 41, List.of(), false))
			.round(new RoundRow(40, "Krogrunda", LocalDate.of(2023, 11, 4), LocalDate.of(2023, 11, 5), "Tillsammans med polisen."))
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
		// A visit after the permit was issued concerned its holder (decision 61), one without a day cannot tell, and the
		// permit of a place that is missing may be another owner's
		assertThat(candidates).extracting(candidate -> candidate.permitLink().basis()).containsExactly(Basis.AFTER_ISSUE, Basis.NO_DATE, Basis.ISSUED_TO_OTHER);
		assertThat(visit.placeName()).isEqualTo("Restaurang 1");
		assertThat(visit.placeNumber()).isEqualTo("12345678");
		assertThat(visit.errand().title()).isEqualTo("Tillsynsbesök – Restaurang 1");
		// The end time 23 cannot be read, so the visit ended when it started
		// The texts of the investigation are the round of inspection, the note of the visit and the remarks
		assertThat(visit.investigation()).isEqualTo(new InvestigationData("Tillsynsbesök – Restaurang 1", LocalDateTime.of(2023, 11, 4, 21, 30), LocalDateTime.of(2023, 11, 4, 21, 30),
			InvestigationTexts.of(new Part("Tillsynsomgång", "Krogrunda, 2023-11-04 – 2023-11-05\nTillsammans med polisen."), new Part("Anteckning", "Mycket folk i baren."),
				new Part("Anmärkningar", "Servering till minderårig: Ungdomar i baren\n77")))
			// Draken shows it as the protocol of an inspection of a serving permit (decision 77), without the end time that
			// cannot be read
			.withProtocol(InspectionProtocols.SERVING_PERMIT, List.of(
				new Parameter("premisesName", "Restaurang", List.of("Restaurang 1")),
				new Parameter("premisesAddress", "Adress", List.of("Storgatan 1, 85230 Sundsvall")),
				new Parameter("inspectionDate", "Tillsynsdatum", List.of("2023-11-04")),
				new Parameter("visitStartTime", "Inleds kl.", List.of("21:30")),
				new Parameter("additionalRemarks", "Beskrivning", List.of("""
					Tillsynsomgång:
					Krogrunda, 2023-11-04 – 2023-11-05
					Tillsammans med polisen.

					Anteckning:
					Mycket folk i baren.

					Anmärkningar:
					Servering till minderårig: Ungdomar i baren
					77""")),
				new Parameter("inspector1NameAndTitle", "Namn, befattning", List.of("Anna Berg")),
				new Parameter("inspector2NameAndTitle", "Namn, befattning", List.of("XYZ")))));
		assertThat(visit.errand().labelPaths()).containsExactly(TestData.EXTERNAL_INSPECTION, "TAGROOT/ALCOHOL_INSPECTION");
		assertThat(visit.errand().externalTags()).isEmpty();
		assertThat(visit.asset()).isNull();
		assertThat(visit.errand().description()).isEqualTo("""
			Tillsynsbesök på Restaurang 1 2023-11-04.
			Tillsynsart: Rutintillsyn
			Tid: 21:30–23
			Tillsynsomgång: Krogrunda, 2023-11-04 – 2023-11-05
			Kontrollant: Anna Berg, XYZ
			Anmärkningar:
			- Servering till minderårig: Ungdomar i baren
			- 77
			Serveringspersonal: Ägaren på plats, Personalen inte på plats
			Anteckning: Mycket folk i baren.""");
		assertThat(parameters(visit))
			.containsEntry("alktTillsynsbesokId", List.of("900"))
			.containsEntry("besoksdatum", List.of("2023-11-04"))
			.containsEntry("besokstid", List.of("21:30–23"))
			.containsEntry("tillsynsart", List.of("Rutintillsyn"))
			.containsEntry("tillsynsomgang", List.of("Krogrunda, 2023-11-04 – 2023-11-05"))
			.doesNotContainKeys("tillsynenGallde", "anmaltBesok")
			.containsEntry("kontrollant", List.of("Anna Berg", "XYZ"))
			.containsEntry("serveringspersonal", List.of("Ägaren på plats", "Personalen inte på plats"))
			.containsEntry("anmarkningar", List.of("Servering till minderårig: Ungdomar i baren", "77"))
			.containsEntry("anteckning", List.of("Mycket folk i baren."));
		// The remarks are also measures, as the AoT solution keeps the deficiencies of an inspection (decision 57)
		final var visited = LocalDateTime.of(2023, 11, 4, 21, 30);
		assertThat(visit.measures()).containsExactly(new MeasureData("Servering till minderårig", "Ungdomar i baren", visited), new MeasureData("77", null, visited));
		// Every file of the visit is material of its investigation (decision 60)
		assertThat(visit.files().references()).allSatisfy(file -> assertThat(visit.files().detailsOf(file).investigation()).isTrue());
		// The attachment shares its name with the visit to another place, which is not migrated but still refers to it
		assertThat(visit.files().references()).containsExactly(
			new FileReference("66666666.rtf", DOCUMENTS),
			new FileReference("protokoll.pdf", ATTACHMENTS, true),
			new FileReference("anteckningar.pdf", ATTACHMENTS),
			new FileReference("bild.jpg", ATTACHMENTS));

		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::toReportText)
			.containsExactly("G28 Tillsynsbesöket saknar datum eller har ett datum efter migreringsdagen och kan vara planerat men inte genomfört: datum saknas");
		assertThat(candidates.get(1).errand().description()).isEqualTo("""
			Tillsynsbesök på Restaurang 1.
			Anmärkningar: Inga anmärkningar registrerade""");
		final var withoutPlace = candidates.get(2);
		assertThat(withoutPlace.ruleHits()).extracting(RuleHit::toReportText).containsExactly("G1 Serverings- eller försäljningsstället saknas: tillsynsbesöket saknar serveringsställe");
		assertThat(withoutPlace.errand().title()).isEqualTo("Tillsynsbesök – okänt serveringsställe");
		assertThat(parameters(withoutPlace)).containsEntry("tillsynsart", List.of("99")).containsEntry("besokstid", List.of("09:15–10:00"))
			.doesNotContainKeys("anmarkningar", "anteckning", "tillsynsomgang");
		assertThat(withoutPlace.investigation().texts().parts()).extracting(Part::heading).doesNotContain("Tillsynsomgång");
		assertThat(withoutPlace.investigation().protocolParameters()).extracting(Parameter::key, parameter -> parameter.values().getFirst())
			.containsExactly(tuple("inspectionDate", "2022-03-01"), tuple("visitStartTime", "09:15"), tuple("visitEndTime", "10:00"));
	}

	@Test
	void inspectionVisitsOfAllPlaces() {
		final var snapshot = snapshot()
			.visit(VisitTestRows.visit(901, 2, LocalDate.of(2023, 11, 4), null, null, null, null, null, null))
			.build();

		final var candidates = new AlktCandidateFactory(snapshot, TODAY, TestData.properties(ErrandType.ALCOHOL_INSPECTION_VISIT, false, Map.of())).visits().candidates();

		assertThat(candidates).singleElement().satisfies(candidate -> assertThat(candidate.ruleHits()).extracting(RuleHit::rule).containsExactly(ReviewRule.PLACE_MISSING));
	}

	private static Map<String, List<String>> parameters(final Candidate candidate) {
		return candidate.errand().parameters().stream().collect(java.util.stream.Collectors.toMap(Parameter::key, Parameter::values));
	}

	@Test
	void theContentOfAPermitShowsOnlyTheKeysOfItsRow() {
		final var content = new AlktPermits.PermitContent(permit(1).beslutsId(100).gtAgarId(10).build(), null, List.of("11:00–01:00"), List.of("Ordningsvakt"));

		assertThat(content).hasToString("PermitContent[permit=PermitRow[objektId=1, gtAgarId=10, beslutsId=100]]");
	}
}
