package se.sundsvall.aotloader.source.alkol;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.configuration.AotLoaderProperties;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.Candidate;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.FileReference;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Outcome;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.DecisionRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.ErrandRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.EventRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.PermitViewRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot.SalesPlaceRow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Kind.ANMALAN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;
import static se.sundsvall.aotloader.service.model.ReviewRule.APPLICANT_NOT_OWNER;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_ON_SEVERAL;
import static se.sundsvall.aotloader.service.model.ReviewRule.ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.ISSUE_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_INVALID;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_ON_SEVERAL_OWNERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_ONLY_FLAG;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_COPY_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERMIT_ENDED;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.SALES_PLACE_CEASED;
import static se.sundsvall.aotloader.service.model.Source.ALKOL;

class AlkolCandidateFactoryTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
	private static final AotLoaderProperties PROPERTIES = TestData.properties(ErrandType.TOBACCO_INSPECTION, true, Map.of("tills", "", "it", TestData.INTERNAL_INSPECTION));

	@Test
	void tobaccoPermit() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, false, true))
			.owner(owner(10, "556677-8899"))
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-2019-100", LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), 50))
			.errand(new ErrandRow(101, 1, "WADE", "IAN-2015-1", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), null))
			.errand(new ErrandRow(102, 1, "WAAÄ", "IAN-2021-1", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.decision(new DecisionRow(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 7, 2), "11111111.rtf", "bilaga.pdf"))
			.decision(new DecisionRow(201, 101, "GTDE", LocalDateTime.of(2015, 2, 1, 10, 0), null, null, null))
			.decision(new DecisionRow(202, 102, "AÄDE", LocalDateTime.of(2021, 2, 1, 10, 0), null, null, null))
			.decision(new DecisionRow(203, 101, "GTDEU", LocalDateTime.of(2016, 1, 1, 10, 0), null, null, null))
			.event(new EventRow(300, 1, 100, "001", LocalDateTime.of(2019, 6, 1, 9, 0), "IAN-2019-100", "22222222.rtf", "ansokan.pdf"))
			.applicant(new ContactRow(50, "5566778899", "Bolaget", null, null, null, null, null, null, null))
			.person(new PersonRow(10, "Person", "8001011234", List.of("VD"), false));

		final var candidate = data.factory().permits().candidates().getFirst();

		assertThat(candidate.key()).isEqualTo(new ItemKey(ALKOL, TILLSTAND, "1"));
		assertThat(candidate.type()).isEqualTo(ErrandType.TOBACCO_PERMIT);
		assertThat(candidate.ruleHits()).isEmpty();
		assertThat(candidate.title()).isEqualTo("Tobakstillstånd – Butik 1");
		assertThat(candidate.labelPaths()).containsExactly("CATEGORYROOT/TOBACCO/SALES_PERMIT_APPLICATION");
		assertThat(candidate.externalTags()).containsExactly(Map.entry("diarienummer", "IAN-2019-100"));
		assertThat(candidate.holder().legalId()).isEqualTo(new LegalId("5566778899", ENTERPRISE));
		assertThat(candidate.persons()).singleElement().satisfies(person -> {
			assertThat(person.legalId()).isEqualTo(new LegalId("198001011234", PRIVATE));
			assertThat(person.positions()).containsExactly("Verkställande direktör");
		});
		assertThat(candidate.place().parameters()).extracting(Parameter::key, Parameter::values).containsExactly(
			tuple("kategori", List.of("Livsmedelsbutik, butikskedja")),
			tuple("omrade", List.of("City")));
		assertThat(parameters(candidate)).containsAllEntriesOf(Map.of(
			"alkolFsId", List.of("1"),
			"tillstandstyp", List.of("Detaljhandel"),
			"utfardat", List.of("2019-07-02"),
			"diarienummer", List.of("IAN-2019-100"),
			"tidigareBeviljanden", List.of("2015-02-01, IAN-2015-1"),
			"senasteBeslut", List.of("Godkänd anmälan ändring i bolag, 2021-02-01"),
			"anmaldaProdukter", List.of("Folköl", "Tobaksfria nikotinprodukter"),
			"noteringar", List.of("Notering 1"),
			"migrerad", List.of("2026-10-12 från AlkOL")));
		assertThat(candidate.asset().titleSuffix()).isEqualTo("detaljhandel");
		assertThat(candidate.asset().issued()).isEqualTo(LocalDate.of(2019, 7, 2));
		assertThat(candidate.asset().parameters()).containsExactly(
			Map.entry("permitHolderName", "Bolaget 10"),
			Map.entry("permitHolderOrgNumber", "556677-8899"),
			Map.entry("permitHolderStreet", "Gatan 1"),
			Map.entry("permitHolderPostalAddress", "85230 Sundsvall"),
			Map.entry("premisesName", "Butik 1"),
			Map.entry("premisesStreet", "Gatan 1"),
			Map.entry("premisesPostalAddress", "85230 Sundsvall"),
			Map.entry("premisesPhone", "060-1"),
			Map.entry("salesRetail", "true"),
			Map.entry("salesWholesale", "false"),
			Map.entry("salesOnline", "false"),
			Map.entry("caseNumber", "IAN-2019-100"),
			Map.entry("diarienummer", "IAN-2019-100"),
			Map.entry("migreradFran", "AlkOL"));
		assertThat(candidate.files()).containsExactly(
			new FileReference("22222222.rtf", DOCUMENTS),
			new FileReference("ansokan.pdf", ATTACHMENTS),
			new FileReference("11111111.rtf", DOCUMENTS),
			new FileReference("bilaga.pdf", ATTACHMENTS));
	}

	@Test
	void aFileNameThatAnotherPlaceAlsoRefersToIsMarked() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, false, true))
			.owner(owner(10, "556677-8899"))
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-2019-100", LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), null))
			.errand(new ErrandRow(101, 1, "WAAÄ", "IAN-2021-1", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.decision(new DecisionRow(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 7, 2), null, "bilaga.pdf"))
			.decision(new DecisionRow(201, null, "GTDE", null, null, null, "Bilaga.pdf"))
			.decision(new DecisionRow(202, 100, "BESOVR", LocalDateTime.of(2019, 7, 3, 10, 0), null, null, "protokoll.pdf"))
			.event(new EventRow(300, 1, 100, "001", LocalDateTime.of(2019, 6, 1, 9, 0), null, "22222222.rtf", "ansokan.pdf"))
			.event(new EventRow(301, null, 100, "001", LocalDateTime.of(2019, 6, 2, 9, 0), null, null, "intyg.pdf"))
			.event(new EventRow(302, 1, 101, "001", LocalDateTime.of(2021, 1, 2, 9, 0), null, null, "intyg.pdf"))
			// Registered on another place than its errand's, whose files it goes to
			.event(new EventRow(303, 2, 100, "001", LocalDateTime.of(2019, 7, 3, 9, 0), null, null, "protokoll.pdf"))
			.event(new EventRow(400, 2, null, "001", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, "ANSOKAN.PDF"));

		final var candidate = data.factory().permits().candidates().getFirst();

		assertThat(candidate.files()).containsExactlyInAnyOrder(
			new FileReference("22222222.rtf", DOCUMENTS, false),
			new FileReference("ansokan.pdf", ATTACHMENTS, true),
			new FileReference("intyg.pdf", ATTACHMENTS, false),
			new FileReference("protokoll.pdf", ATTACHMENTS, false),
			new FileReference("bilaga.pdf", ATTACHMENTS, true));
	}

	@Test
	void tobaccoPermitThatHasEnded() {
		final var data = new Data()
			.place(place(1, 10).ceased())
			.owner(owner(10, "556677-8899"))
			.view(new PermitViewRow(1, false, false, false))
			.errand(new ErrandRow(100, 1, "WAPH", null, null, null, 50))
			.decision(new DecisionRow(200, 100, "GTPH", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null))
			.decision(new DecisionRow(201, 100, "GTDEÅ", LocalDateTime.of(2022, 1, 1, 10, 0), null, null, null))
			.applicant(new ContactRow(50, "5599887766", "Annan", null, null, null, null, null, null, null));

		final var candidate = data.factory().permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).containsExactlyInAnyOrder(
			tuple(SALES_PLACE_CEASED, null),
			tuple(PERMIT_ENDED, "GTDEÅ 2022-01-01"),
			tuple(DIARY_NUMBER_MISSING, "det beviljande ärendet saknar diarienummer"),
			tuple(APPLICANT_NOT_OWNER, null));
		assertThat(parameters(candidate)).containsEntry("tillstandstyp", List.of("Partihandel")).containsEntry("utfardat", List.of("2019-07-01"));
		assertThat(candidate.asset().titleSuffix()).isEqualTo("partihandel");
	}

	@Test
	void tobaccoPermitWithoutGrantOwnerOrPlace() {
		final var data = new Data()
			.place(place(1, 99))
			.view(new PermitViewRow(1, true, false, false))
			.view(new PermitViewRow(2, true, false, false));

		final var candidates = data.factory().permits().candidates();

		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).containsExactlyInAnyOrder(
			tuple(OWNER_MISSING, "AgarID 99 finns inte"),
			tuple(DIARY_NUMBER_MISSING, "inget beviljande beslut hittades"));
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(PLACE_MISSING, DIARY_NUMBER_MISSING);
		assertThat(candidates.get(1).title()).isEqualTo("Tobakstillstånd – okänt försäljningsställe");
	}

	@Test
	void ownerRules() {
		final var data = new Data()
			.place(place(1, 10).withOwnerCopy("556677-0000"))
			.place(place(2, 11))
			.place(place(3, 12))
			.place(place(4, null))
			.owner(owner(10, "556677-8899"))
			.owner(owner(11, "5566778899"))
			.owner(owner(12, "12345"))
			.view(new PermitViewRow(1, true, false, false))
			.view(new PermitViewRow(2, true, false, false))
			.view(new PermitViewRow(3, true, false, false))
			.view(new PermitViewRow(4, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-1", null, null, null))
			.errand(new ErrandRow(101, 2, "WADE", "ian 1", null, null, null))
			.decision(new DecisionRow(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null))
			.decision(new DecisionRow(201, 101, "GTDE", null, null, null, null));

		final var candidates = data.factory().permits().candidates();

		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(OWNER_COPY_DIFFERS, LEGAL_ID_ON_SEVERAL_OWNERS, DIARY_NUMBER_ON_SEVERAL);
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(LEGAL_ID_ON_SEVERAL_OWNERS, DIARY_NUMBER_ON_SEVERAL, ISSUE_DATE_MISSING);
		assertThat(candidates.get(2).ruleHits()).extracting(RuleHit::rule).contains(LEGAL_ID_INVALID);
		assertThat(candidates.get(3).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(OWNER_MISSING, "ingen ägare kopplad"));
	}

	@Test
	void anActivePlaceMarkedForTobaccoWithoutAPermitIsANotice() {
		final var data = new Data()
			.place(place(1, 10).withFlags(false, true, false, false))
			.place(place(2, 10).withFlags(false, true, false, false).ceased())
			.place(place(3, 10).withFlags(true, false, false, false))
			.owner(owner(10, "5566778899"));

		final var result = data.factory().permits();

		assertThat(result.candidates()).isEmpty();
		assertThat(result.notices()).singleElement().satisfies(notice -> {
			assertThat(notice.key()).isEqualTo(new ItemKey(ALKOL, TILLSTAND, "1"));
			assertThat(notice.outcome()).isEqualTo(Outcome.MIGRERAS_INTE);
			assertThat(notice.detail()).startsWith("Ö2 ");
		});
	}

	@Test
	void theSameNumberWrittenInAnotherWayIsTheSameParty() {
		final var data = new Data()
			.place(place(1, 10).withOwnerCopy("19121212-1212"))
			.owner(owner(10, "121212+1212"))
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-1", null, null, 50))
			.decision(new DecisionRow(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 7, 2), null, null))
			.applicant(new ContactRow(50, "191212121212", "Tolvan", null, null, null, null, null, null, null));

		final var candidate = data.factory().permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).isEmpty();
		assertThat(candidate.holder().legalId()).isEqualTo(new LegalId("191212121212", PRIVATE));
	}

	@Test
	void codesAreLookedUpWithoutRegardToCase() {
		final var data = new Data()
			.place(place(1, 10).withCategory("lbk"))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false));

		final var candidate = data.factory().permits().candidates().getFirst();

		assertThat(candidate.place().parameters()).extracting(Parameter::key, Parameter::values)
			.contains(tuple("kategori", List.of("Livsmedelsbutik, butikskedja")));
	}

	@Test
	void notificationsOfFolkol() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, false, false, false))
			.place(place(2, 10).withFlags(true, false, false, false))
			.place(place(3, 10).withFlags(true, false, false, false).withCategory("SERV"))
			.place(place(4, 10).withFlags(true, false, false, false))
			.owner(owner(10, "5566778899"))
			.errand(new ErrandRow(100, 1, "Fföl", "IAN-2010-1", LocalDate.of(2010, 1, 1), LocalDate.of(2010, 2, 1), null))
			.errand(new ErrandRow(101, 1, "SÖL", "IAN-2015-1", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), null))
			.event(new EventRow(300, 1, 101, "015", LocalDateTime.of(2015, 1, 2, 9, 0), "IAN-2015-1", "33333333.rtf", null))
			.event(new EventRow(301, 2, null, "014", LocalDateTime.of(2012, 3, 4, 9, 0), "IAN-2012-1", null, null))
			.event(new EventRow(302, 3, null, "999", LocalDateTime.of(2009, 3, 4, 9, 0), null, null, null));

		final var candidates = data.factory().notifications().candidates();

		assertThat(candidates).extracting(Candidate::key).containsExactly(
			new ItemKey(ALKOL, ANMALAN, "1-FOLKOL"), new ItemKey(ALKOL, ANMALAN, "2-FOLKOL"), new ItemKey(ALKOL, ANMALAN, "3-FOLKOL"), new ItemKey(ALKOL, ANMALAN, "4-FOLKOL"));

		final var latestServing = candidates.get(0);
		assertThat(latestServing.type()).isEqualTo(ErrandType.FOLKOL_SERVING_NOTIFICATION);
		assertThat(latestServing.ruleHits()).isEmpty();
		assertThat(latestServing.title()).isEqualTo("Anmälan om servering av folköl – Butik 1");
		assertThat(latestServing.labelPaths()).containsExactly("CATEGORYROOT/ALCOHOL/FOLKOL_SERVING_NOTIFICATION");
		assertThat(latestServing.externalTags()).containsExactly(Map.entry("alkolAnmalan", "1-FOLKOL"), Map.entry("diarienummer", "IAN-2015-1"));
		assertThat(parameters(latestServing)).containsEntry("anmald", List.of("2010-01-01")).containsEntry("produkt", List.of("Folköl")).doesNotContainKey("anmaldUppskattat");
		assertThat(latestServing.asset().issued()).isEqualTo(LocalDate.of(2010, 1, 1));
		assertThat(latestServing.asset().description()).isEqualTo("Anmälan om servering av folköl på Butik 1");
		assertThat(latestServing.files()).containsExactly(new FileReference("33333333.rtf", DOCUMENTS));
		assertThat(latestServing.persons()).isEmpty();

		final var eventOnly = candidates.get(1);
		assertThat(eventOnly.type()).isEqualTo(ErrandType.FOLKOL_SALES_NOTIFICATION);
		assertThat(eventOnly.externalTags()).containsEntry("diarienummer", "IAN-2012-1");
		assertThat(parameters(eventOnly)).containsEntry("anmald", List.of("2012-03-04"));

		final var flagOnlyServingPlace = candidates.get(2);
		assertThat(flagOnlyServingPlace.type()).isEqualTo(ErrandType.FOLKOL_SERVING_NOTIFICATION);
		assertThat(flagOnlyServingPlace.ruleHits()).extracting(RuleHit::rule).containsExactly(NOTIFICATION_ONLY_FLAG);
		assertThat(parameters(flagOnlyServingPlace)).containsEntry("anmald", List.of("2009-03-04")).containsEntry("anmaldUppskattat", List.of("Ja")).doesNotContainKey("diarienummer");
		assertThat(flagOnlyServingPlace.asset().parameters()).containsEntry("anmaldUppskattat", "true")
			.containsKeys("premisesName", "premisesStreet", "premisesPostalAddress", "premisesPhone", "produkt", "migreradFran")
			.doesNotContainKeys("premisesRestaurantNumber", "permitHolderName", "permitHolderOrgNumber");
		assertThat(flagOnlyServingPlace.externalTags()).containsOnlyKeys("alkolAnmalan");

		final var withoutAnyDate = candidates.get(3);
		assertThat(withoutAnyDate.type()).isEqualTo(ErrandType.FOLKOL_SALES_NOTIFICATION);
		assertThat(withoutAnyDate.ruleHits()).extracting(RuleHit::rule).containsExactly(NOTIFICATION_ONLY_FLAG, NOTIFICATION_DATE_MISSING);
		assertThat(withoutAnyDate.asset().issued()).isNull();
	}

	@Test
	void notificationsOfOtherProductsAndNotices() {
		final var data = new Data()
			.place(place(1, 10).withFlags(false, false, true, true))
			.place(place(2, 10).withFlags(false, false, false, false))
			.place(place(3, 10).withFlags(false, false, false, false).ceased())
			.place(place(4, 10).withFlags(false, true, false, false))
			.owner(owner(10, "5566778899"))
			.errand(new ErrandRow(100, 1, "ec", "IAN-1", null, null, null))
			.event(new EventRow(300, 1, null, "047", LocalDateTime.of(2022, 3, 4, 9, 0), null, null, null));

		final var result = data.factory().notifications();

		assertThat(result.candidates()).extracting(Candidate::key, Candidate::type).containsExactly(
			tuple(new ItemKey(ALKOL, ANMALAN, "1-ECIGARETTER"), ErrandType.ECIGARETTE_NOTIFICATION),
			tuple(new ItemKey(ALKOL, ANMALAN, "1-TOBAKSFRI_NIKOTIN"), ErrandType.TOBACCO_FREE_NICOTINE_NOTIFICATION));
		assertThat(result.candidates().get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.containsExactly(tuple(NOTIFICATION_DATE_MISSING, "E-cigaretter och påfyllningsbehållare, anmälan saknar datum"));
		assertThat(result.candidates().get(0).labelPaths()).containsExactly("CATEGORYROOT/TOBACCO/ECIGARETTE_SALES_NOTIFICATION");
		assertThat(result.candidates().get(1).labelPaths()).containsExactly("CATEGORYROOT/TOBACCO/TOBACCO_FREE_NICOTINE_SALES_NOTIFICATION");
		assertThat(result.candidates().get(1).title()).isEqualTo("Anmälan om försäljning av tobaksfria nikotinprodukter – Butik 1");
		assertThat(result.notices()).singleElement().satisfies(notice -> {
			assertThat(notice.key()).isEqualTo(new ItemKey(ALKOL, ANMALAN, "2-INGEN"));
			assertThat(notice.outcome()).isEqualTo(Outcome.MIGRERAS_INTE);
		});
	}

	@Test
	void inspectionErrands() {
		final var data = new Data()
			.place(place(1, 10))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(400, 1, "Tills", "TIL-1", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), 50))
			.errand(new ErrandRow(401, 1, "IT", null, LocalDate.of(2020, 1, 1), null, null))
			.errand(new ErrandRow(402, 2, "IT", null, LocalDate.of(2020, 1, 1), null, null))
			.decision(new DecisionRow(500, 400, "VT", LocalDateTime.of(2020, 1, 20, 9, 0), null, "varning.pdf", null))
			.applicant(new ContactRow(50, "5566778899", "Bolaget", null, null, null, null, null, null, null));

		final var candidates = data.factory().inspections().candidates();

		assertThat(candidates).extracting(Candidate::key).containsExactly(new ItemKey(ALKOL, TILLSYN, "400"), new ItemKey(ALKOL, TILLSYN, "401"));
		final var external = candidates.get(0);
		assertThat(external.type()).isEqualTo(ErrandType.TOBACCO_INSPECTION);
		assertThat(external.ruleHits()).isEmpty();
		assertThat(external.title()).isEqualTo("Tillsynsärende – Butik 1");
		assertThat(external.labelPaths()).containsExactly("TAGROOT/TOBACCO_INSPECTION");
		assertThat(external.asset()).isNull();
		assertThat(external.permitLink().permitKey()).isEqualTo(new ItemKey(ALKOL, TILLSTAND, "1"));
		assertThat(external.permitLink().sameHolder()).isTrue();
		assertThat(parameters(external)).containsEntry("beslut", List.of("Varning, 2020-01-20"));
		assertThat(external.files()).containsExactly(new FileReference("varning.pdf", DOCUMENTS));
		final var internal = candidates.get(1);
		assertThat(internal.ruleHits()).extracting(RuleHit::rule).containsExactly(ERRAND_OPEN);
		assertThat(internal.labelPaths()).containsExactly("TAGROOT/TOBACCO_INSPECTION", "CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION");
		assertThat(internal.asset()).isNull();
		assertThat(internal.holder()).isNull();
	}

	@Test
	void inspectionErrandsOfAllPlaces() {
		final var data = new Data()
			.errand(new ErrandRow(402, 2, "IT", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null))
			// An errand without a sales place has no permit to look for
			.errand(new ErrandRow(403, null, "it", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null));

		final var candidates = new AlkolCandidateFactory(data.build(), TODAY, TestData.properties(ErrandType.TOBACCO_INSPECTION, false, Map.of("it", "X"))).inspections().candidates();

		assertThat(candidates).hasSize(2).allSatisfy(candidate -> {
			assertThat(candidate.ruleHits()).extracting(RuleHit::rule).containsExactly(PLACE_MISSING);
			assertThat(candidate.permitLink().permitKey()).isNull();
		});
	}

	private static Map<String, List<String>> parameters(final Candidate candidate) {
		return candidate.parameters().stream().collect(Collectors.toMap(Parameter::key, Parameter::values));
	}

	private static PlaceBuilder place(final int fsId, final Integer agarId) {
		return new PlaceBuilder(fsId, agarId);
	}

	private static ContactRow owner(final int agarId, final String number) {
		return new ContactRow(agarId, number, "Bolaget " + agarId, null, "Gatan 1", null, "85230", "Sundsvall", null, null);
	}

	private static final class PlaceBuilder {

		private final int fsId;
		private final Integer agarId;
		private boolean folkol;
		private boolean tobak = true;
		private boolean ecig;
		private boolean tobaksfri;
		private boolean upphort;
		private String category = "LBK";
		private String ownerCopy;

		private PlaceBuilder(final int fsId, final Integer agarId) {
			this.fsId = fsId;
			this.agarId = agarId;
		}

		PlaceBuilder withFlags(final boolean folkolValue, final boolean tobakValue, final boolean ecigValue, final boolean tobaksfriValue) {
			folkol = folkolValue;
			tobak = tobakValue;
			ecig = ecigValue;
			tobaksfri = tobaksfriValue;
			return this;
		}

		PlaceBuilder ceased() {
			upphort = true;
			return this;
		}

		PlaceBuilder withCategory(final String value) {
			category = value;
			return this;
		}

		PlaceBuilder withOwnerCopy(final String value) {
			ownerCopy = value;
			return this;
		}

		SalesPlaceRow build() {
			return new SalesPlaceRow(fsId, "Butik " + fsId, "Gatan " + fsId, "85230", "Sundsvall", "060-1", "butik@example.com", "12", category, ownerCopy, "Notering " + fsId, folkol, tobak, ecig, tobaksfri, upphort, agarId);
		}
	}

	private static final class Data {

		private final List<SalesPlaceRow> places = new ArrayList<>();
		private final Map<Integer, PermitViewRow> view = new HashMap<>();
		private final Map<Integer, ContactRow> owners = new HashMap<>();
		private final List<ErrandRow> errands = new ArrayList<>();
		private final List<DecisionRow> decisions = new ArrayList<>();
		private final List<EventRow> events = new ArrayList<>();
		private final Map<Integer, ContactRow> applicants = new HashMap<>();
		private final Map<Integer, List<PersonRow>> persons = new HashMap<>();

		Data place(final PlaceBuilder place) {
			places.add(place.build());
			return this;
		}

		Data owner(final ContactRow owner) {
			owners.put(owner.id(), owner);
			return this;
		}

		Data view(final PermitViewRow row) {
			view.put(row.fsId(), row);
			return this;
		}

		Data errand(final ErrandRow row) {
			errands.add(row);
			return this;
		}

		Data decision(final DecisionRow row) {
			decisions.add(row);
			return this;
		}

		Data event(final EventRow row) {
			events.add(row);
			return this;
		}

		Data applicant(final ContactRow row) {
			applicants.put(row.id(), row);
			return this;
		}

		Data person(final PersonRow row) {
			persons.computeIfAbsent(row.agarId(), _ -> new ArrayList<>()).add(row);
			return this;
		}

		AlkolSnapshot build() {
			return new AlkolSnapshot(places, view, owners, errands, decisions, events, applicants, persons,
				Map.of("GTDE", "Tillstånd tobaksförsäljning – detaljhandel", "AÄDE", "Godkänd anmälan ändring i bolag", "VT", "Varning"),
				Map.of("Tills", "Tillsynsärende", "IT", "Inre tillsyn"),
				Map.of("LBK", "Livsmedelsbutik, butikskedja", "SERV", "Serveringsställe grill/pizza"),
				Map.of("12", "City"),
				Map.of("VD", "Verkställande direktör"));
		}

		AlkolCandidateFactory factory() {
			return new AlkolCandidateFactory(build(), TODAY, PROPERTIES);
		}
	}
}
