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
import se.sundsvall.aotloader.service.model.AssetFile;
import se.sundsvall.aotloader.service.model.Candidate;
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
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.FilePurposes;
import se.sundsvall.aotloader.source.InspectionProtocols;
import se.sundsvall.aotloader.source.InspectionVisitRows;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.RoundRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.VisitTestRows;
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
import static se.sundsvall.aotloader.service.model.Kind.DOKUMENTATION;
import static se.sundsvall.aotloader.service.model.Kind.TILLSTAND;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYN;
import static se.sundsvall.aotloader.service.model.Kind.TILLSYNSBESOK;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;
import static se.sundsvall.aotloader.service.model.ReviewRule.APPLICANT_NOT_OWNER;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_OLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.DIARY_NUMBER_ON_SEVERAL;
import static se.sundsvall.aotloader.service.model.ReviewRule.ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.FORMER_HOLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.ISSUE_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_INVALID;
import static se.sundsvall.aotloader.service.model.ReviewRule.LEGAL_ID_ON_SEVERAL_OWNERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_DATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_OF_FORMER_HOLDER;
import static se.sundsvall.aotloader.service.model.ReviewRule.NOTIFICATION_ONLY_FLAG;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_COPY_DIFFERS;
import static se.sundsvall.aotloader.service.model.ReviewRule.OWNER_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.PERMIT_ENDED;
import static se.sundsvall.aotloader.service.model.ReviewRule.PLACE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.SALES_PLACE_CEASED;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNDERLYING_ERRAND_OPEN;
import static se.sundsvall.aotloader.service.model.ReviewRule.UNPARSEABLE;
import static se.sundsvall.aotloader.service.model.Source.ALKOL;

class AlkolCandidateFactoryTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 12);
	private static final AotLoaderProperties PROPERTIES = TestData.properties(ErrandType.TOBACCO_INSPECTION, true, Map.of("tills", "", "it", TestData.INTERNAL_INSPECTION));

	@Test
	void tobaccoPermit() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, false, true))
			.owner(owner(10, "556677-8899"))
			.view(new PermitViewRow(1, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-2019-100", LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), 50))
			.errand(AlkolTestRows.errand(101, 1, "WADE", "IAN-2015-1", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), null))
			.errand(AlkolTestRows.errand(102, 1, "WAAÄ", "IAN-2021-1", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.decision(AlkolTestRows.decision(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 7, 2), "TillstT", "11111111.rtf", "bilaga.pdf"))
			.decision(AlkolTestRows.decision(201, 101, "GTDE", LocalDateTime.of(2015, 2, 1, 10, 0), null, null, null, null))
			.decision(AlkolTestRows.decision(202, 102, "AÄDE", LocalDateTime.of(2021, 2, 1, 10, 0), null, null, null, null))
			.decision(AlkolTestRows.decision(203, 101, "GTDEU", LocalDateTime.of(2016, 1, 1, 10, 0), null, null, null, null))
			.event(AlkolTestRows.event(300, 1, 100, "001", LocalDateTime.of(2019, 6, 1, 9, 0), "IAN-2019-100", null, "22222222.rtf", "ansokan.pdf"))
			.applicant(new ContactRow(50, "5566778899", "Bolaget", null, null, null, null, null, null, null))
			.person(new PersonRow(10, "Person", "8001011234", List.of("VD"), false));

		final var candidate = data.factory().permits().candidates().getFirst();

		assertThat(candidate.key()).isEqualTo(new ItemKey(ALKOL, TILLSTAND, "1"));
		assertThat(candidate.type()).isEqualTo(ErrandType.TOBACCO_PERMIT);
		assertThat(candidate.ruleHits()).isEmpty();
		assertThat(candidate.errand().title()).isEqualTo("Tobakstillstånd – Butik 1");
		assertThat(candidate.errand().labelPaths()).containsExactly("CATEGORYROOT/TOBACCO/SALES_PERMIT_APPLICATION");
		assertThat(candidate.errand().externalTags()).containsExactly(Map.entry("diarienummer", "IAN-2019-100"));
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
			// Only marks on the place, so their notifications have the place's earliest date, estimated (decision 68)
			"anmaldaProdukter", List.of("Folköl, anmäld 2015-01-01 (uppskattat)", "Tobaksfria nikotinprodukter, anmäld 2015-01-01 (uppskattat)"),
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
			Map.entry("executionDate", "2019-07-02"),
			Map.entry("diarienummer", "IAN-2019-100"),
			Map.entry("migreradFran", "AlkOL"));
		assertThat(candidate.files().references()).containsExactly(
			new FileReference("22222222.rtf", DOCUMENTS),
			new FileReference("ansokan.pdf", ATTACHMENTS),
			new FileReference("11111111.rtf", DOCUMENTS),
			new FileReference("bilaga.pdf", ATTACHMENTS));
		// An attachment whose name does not tell it is a decision goes on the errand only
		assertThat(candidate.files().onPost()).containsExactly(new AssetFile(new FileReference("11111111.rtf", DOCUMENTS), "Tillståndsbevis"));
		// The certificate is also the document of the grant (decision 58)
		assertThat(candidate.asset().legalBasisDocuments()).containsExactly(new FileReference("11111111.rtf", DOCUMENTS));
	}

	@Test
	void theNotifiedProductsOfATobaccoPermitAreThoseOfTheWholePlaceWithTheDatesOfTheirNotifications() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, true, false))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-2019-1", LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), null))
			.errand(AlkolTestRows.errand(110, 1, "Fföl", "IAN-2017-1", LocalDate.of(2017, 5, 1), LocalDate.of(2017, 6, 1), null))
			.decision(AlkolTestRows.decision(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null));

		final var permit = data.factory().permits().candidates().getFirst();

		// Folköl has an errand that registered it; e-cigarettes are only a mark, dated by the place's earliest errand
		assertThat(permit.errand().description()).startsWith("Tobakstillstånd för Butik 1.\n").contains("\nAnmälda produkter på försäljningsstället (gäller hela stället, inte bara detta ärende): "
			+ "Folköl, anmäld 2017-05-01; E-cigaretter och påfyllningsbehållare, anmäld 2017-05-01 (uppskattat)");
		assertThat(parameters(permit)).containsEntry("anmaldaProdukter",
			List.of("Folköl, anmäld 2017-05-01", "E-cigaretter och påfyllningsbehållare, anmäld 2017-05-01 (uppskattat)"));
		assertThat(permit.errand().parameters()).filteredOn(parameter -> "anmaldaProdukter".equals(parameter.key())).extracting(Parameter::displayName)
			.containsExactly("Anmälda produkter på försäljningsstället");
	}

	@Test
	void theCertificateIsThatOfTheLatestGrantThatHasOneAndAnOlderOneIsNoted() {
		final var data = new Data()
			.place(place(1, 10))
			.place(place(2, 10))
			.place(place(3, 10))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false, false))
			.view(new PermitViewRow(2, true, false, false, false))
			.view(new PermitViewRow(3, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-2019-1", null, LocalDate.of(2019, 7, 1), null))
			.errand(AlkolTestRows.errand(101, 1, "WADE", "IAN-2023-1", null, LocalDate.of(2023, 3, 1), null))
			.errand(AlkolTestRows.errand(200, 2, "WADE", "IAN-2019-2", null, LocalDate.of(2019, 7, 1), null))
			.errand(AlkolTestRows.errand(201, 2, "WADE", "IAN-2022-2", null, LocalDate.of(2022, 1, 1), null))
			.errand(AlkolTestRows.errand(300, 3, "WADE", "IAN-2019-3", null, LocalDate.of(2019, 7, 1), null))
			.decision(AlkolTestRows.decision(1000, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "TillstT", "11111111.rtf", null))
			.decision(AlkolTestRows.decision(1001, 101, "GTDE", LocalDateTime.of(2023, 3, 1, 10, 0), null, null, null, "Tillståndsbevis 2023.pdf"))
			.decision(AlkolTestRows.decision(2000, 200, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "TILLTT", "22222222.rtf", null))
			.decision(AlkolTestRows.decision(2001, 201, "GTDE", LocalDateTime.of(2022, 1, 1, 10, 0), null, "MotTob", "33333333.rtf", "beslut.pdf"))
			.decision(AlkolTestRows.decision(3000, 300, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "MotTob", "44444444.rtf", "Mottagningsbevis.pdf"))
			.event(AlkolTestRows.event(400, 1, 101, "016", LocalDateTime.of(2023, 2, 1, 9, 0), null, null, null, "Planritning.pdf"));

		final var candidates = data.factory().permits().candidates();

		final var scanned = candidates.get(0);
		assertThat(scanned.ruleHits()).isEmpty();
		assertThat(scanned.files().onPost()).containsExactly(
			new AssetFile(new FileReference("Tillståndsbevis 2023.pdf", ATTACHMENTS), "Tillståndsbevis"),
			new AssetFile(new FileReference("Planritning.pdf", ATTACHMENTS), "Ritning"));
		assertThat(scanned.files().references()).containsExactly(new FileReference("Planritning.pdf", ATTACHMENTS), new FileReference("Tillståndsbevis 2023.pdf", ATTACHMENTS));

		final var older = candidates.get(1);
		assertThat(older.ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.containsExactly(tuple(CERTIFICATE_OLDER, "beviset hör till beslutet 2019-07-01, det senaste beviljandet är 2022-01-01"));
		// The certificate of the earlier grant may be a former holder's, so it goes on the errand only, as does a receipt
		// letter, which is no permit decision
		assertThat(older.files().onPost()).containsExactly(new AssetFile(new FileReference("beslut.pdf", ATTACHMENTS), "Beslut"));
		assertThat(older.files().references()).endsWith(new FileReference("22222222.rtf", DOCUMENTS));
		assertThat(older.files().detailsOf(new FileReference("22222222.rtf", DOCUMENTS)))
			.as("A certificate of another errand than that of the latest grant gets its purpose and time too")
			.isEqualTo(new FileDetails(FilePurposes.ofDocument(ALKOL, "TILLTT"), LocalDateTime.of(2019, 7, 1, 10, 0)));

		assertThat(candidates.get(2).ruleHits()).extracting(RuleHit::rule).containsExactly(CERTIFICATE_MISSING);
		assertThat(candidates.get(2).files().onPost()).as("a receipt letter and a receipt are no permit decisions").isEmpty();
		assertThat(candidates.get(2).files().references()).containsExactly(new FileReference("44444444.rtf", DOCUMENTS), new FileReference("Mottagningsbevis.pdf", ATTACHMENTS));

		// The certificate, then the document of the latest grant, may state the legal basis; not the certificate that may be
		// a former holder's
		assertThat(scanned.asset().legalBasisDocuments()).containsExactly(new FileReference("Tillståndsbevis 2023.pdf", ATTACHMENTS));
		assertThat(older.asset().legalBasisDocuments()).containsExactly(new FileReference("33333333.rtf", DOCUMENTS));
		assertThat(candidates.get(2).asset().legalBasisDocuments()).containsExactly(new FileReference("44444444.rtf", DOCUMENTS));
	}

	@Test
	void theEventsOfAFormerHolderAreNotMigratedAndAPermitWithoutItsSalesPlaceKeepsItsEvents() {
		final var data = new Data()
			.place(place(1, 10))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false, false))
			.view(new PermitViewRow(3, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", null, LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), 50))
			.errand(AlkolTestRows.errand(101, 1, "WADE", null, LocalDate.of(2019, 1, 1), LocalDate.of(2019, 3, 1), 51))
			.applicant(new ContactRow(50, "5599887766", "Förra ägaren", null, null, null, null, null, null, null))
			.applicant(new ContactRow(51, "556677-8899", "Bolaget", null, null, null, null, null, null, null))
			.event(AlkolTestRows.event(310, 1, null, "016", LocalDateTime.of(2018, 6, 1, 9, 0), null, null, null, "gammal.pdf"))
			.event(AlkolTestRows.event(311, 1, null, "016", LocalDateTime.of(2019, 2, 1, 9, 0), null, null, null, "ny.pdf"))
			.event(AlkolTestRows.event(312, 3, null, "016", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null, "utan-stalle.pdf"));

		final var documentation = data.factory().documentation().candidates();

		assertThat(documentation).extracting(Candidate::key, candidate -> candidate.permitLink().permitKey(), Candidate::placeName, Candidate::ruleHits).containsExactly(
			tuple(new ItemKey(ALKOL, DOKUMENTATION, "310"), new ItemKey(ALKOL, TILLSTAND, "1"), "Butik 1",
				List.of(RuleHit.of(FORMER_HOLDER, "händelsen var 2018-06-01, och stället hade en tidigare ägare till och med 2018-12-31"))),
			tuple(new ItemKey(ALKOL, DOKUMENTATION, "311"), new ItemKey(ALKOL, TILLSTAND, "1"), "Butik 1", List.of()),
			tuple(new ItemKey(ALKOL, DOKUMENTATION, "312"), new ItemKey(ALKOL, TILLSTAND, "3"), null, List.of()));
		assertThat(documentation.getLast().errand().title()).endsWith(" – okänt försäljningsställe");
		assertThat(documentation.getLast().holder()).isNull();
	}

	@Test
	void theEventsWithoutAnErrandGoToTheirNotificationOrBecomeDocumentationErrands() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, false, false))
			.place(place(2, 10).withFlags(true, false, true, false))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-2019-1", null, LocalDate.of(2019, 7, 1), null))
			.decision(AlkolTestRows.decision(1000, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "TillstT", "11111111.rtf", null))
			.event(AlkolTestRows.event(300, 1, null, "014", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null, "folkol-1.pdf"))
			.event(AlkolTestRows.event(301, 1, null, "016", LocalDateTime.of(2020, 1, 2, 9, 0), null, null, "22222222.rtf", "handling-1.pdf"))
			// E-cigarettes are not notified on place 1, so the event is one of the place's own
			.event(AlkolTestRows.event(302, 1, null, "105", LocalDateTime.of(2020, 1, 3, 9, 0), null, null, null, "ecig-1.pdf"))
			.event(AlkolTestRows.event(303, 2, null, "014", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null, "folkol-2.pdf"))
			.event(AlkolTestRows.event(304, 2, null, "105", LocalDateTime.of(2020, 1, 2, 9, 0), null, null, null, "ecig-2.pdf"))
			.event(AlkolTestRows.event(305, 2, null, "035", LocalDateTime.of(2020, 1, 3, 9, 0), null, null, null, "egentillsyn-2.pdf"))
			.event(AlkolTestRows.event(306, 2, null, "016", LocalDateTime.of(2020, 1, 4, 9, 0), null, null, null, "Planritning butik.pdf"))
			// A letter that confirms e-cigarettes, by its template, goes to that notification and its post only
			.event(AlkolTestRows.event(307, 2, null, "023", LocalDateTime.of(2020, 1, 5, 9, 0), null, "BekEci", "77777777.rtf", null))
			// An event with neither a text nor a file tells nothing, and becomes no errand
			.event(AlkolTestRows.event(308, 2, null, "024", LocalDateTime.of(2020, 1, 6, 9, 0), null, null, null, null));

		final var permit = data.factory().permits().candidates().getFirst();
		final var notifications = data.factory().notifications().candidates();
		final var documentation = data.factory().documentation().candidates();

		assertThat(permit.files().references()).containsExactly(new FileReference("11111111.rtf", DOCUMENTS));
		assertThat(permit.files().onPost()).extracting(AssetFile::category).containsExactly("Tillståndsbevis");
		assertThat(notifications).extracting(Candidate::key, candidate -> candidate.files().references()).containsExactly(
			tuple(new ItemKey(ALKOL, ANMALAN, "1-FOLKOL"), List.of(new FileReference("folkol-1.pdf", ATTACHMENTS))),
			tuple(new ItemKey(ALKOL, ANMALAN, "2-FOLKOL"), List.of(new FileReference("folkol-2.pdf", ATTACHMENTS))),
			tuple(new ItemKey(ALKOL, ANMALAN, "2-ECIGARETTER"), List.of(new FileReference("ecig-2.pdf", ATTACHMENTS), new FileReference("77777777.rtf", DOCUMENTS))));
		// The place's other events become documentation errands, filed under its current tobacco permit, or else under the
		// notification of the first product it is marked for (decision 71)
		assertThat(documentation).extracting(Candidate::key, candidate -> candidate.permitLink().permitKey(), candidate -> candidate.files().references()).containsExactly(
			tuple(new ItemKey(ALKOL, DOKUMENTATION, "301"), new ItemKey(ALKOL, TILLSTAND, "1"),
				List.of(new FileReference("22222222.rtf", DOCUMENTS), new FileReference("handling-1.pdf", ATTACHMENTS))),
			tuple(new ItemKey(ALKOL, DOKUMENTATION, "302"), new ItemKey(ALKOL, TILLSTAND, "1"), List.of(new FileReference("ecig-1.pdf", ATTACHMENTS))),
			tuple(new ItemKey(ALKOL, DOKUMENTATION, "305"), new ItemKey(ALKOL, ANMALAN, "2-FOLKOL"), List.of(new FileReference("egentillsyn-2.pdf", ATTACHMENTS))),
			tuple(new ItemKey(ALKOL, DOKUMENTATION, "306"), new ItemKey(ALKOL, ANMALAN, "2-FOLKOL"), List.of(new FileReference("Planritning butik.pdf", ATTACHMENTS))));
		assertThat(documentation).allSatisfy(candidate -> {
			assertThat(candidate.type()).isEqualTo(ErrandType.TOBACCO_DOCUMENTATION);
			assertThat(candidate.permitLink().basis()).isEqualTo(Basis.FILED);
			assertThat(candidate.files().onPost()).isEmpty();
			assertThat(candidate.asset()).isNull();
		});
		// The notifications get the attachments of the events that register their product on their post too
		assertThat(notifications).extracting(Candidate::key, candidate -> candidate.files().onPost()).containsExactly(
			tuple(new ItemKey(ALKOL, ANMALAN, "1-FOLKOL"), List.of(new AssetFile(new FileReference("folkol-1.pdf", ATTACHMENTS), null))),
			tuple(new ItemKey(ALKOL, ANMALAN, "2-FOLKOL"), List.of(new AssetFile(new FileReference("folkol-2.pdf", ATTACHMENTS), null))),
			tuple(new ItemKey(ALKOL, ANMALAN, "2-ECIGARETTER"), List.of(new AssetFile(new FileReference("ecig-2.pdf", ATTACHMENTS), null),
				new AssetFile(new FileReference("77777777.rtf", DOCUMENTS), null))));
	}

	@Test
	void onlyTheNotificationItsConfirmationsAndDrawingsGoOnItsPost() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, false, false, false))
			.owner(owner(10, "5566778899"))
			.errand(AlkolTestRows.errand(100, 1, "SÖL", "IAN-2015-1", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), null))
			.decision(AlkolTestRows.decision(200, 100, "AÄDE", LocalDateTime.of(2015, 1, 20, 10, 0), null, "BekFol", "44444444.rtf", null))
			.event(AlkolTestRows.event(300, 1, 100, "015", LocalDateTime.of(2015, 1, 2, 9, 0), "IAN-2015-1", null, null, "anmalan.pdf"))
			.event(AlkolTestRows.event(301, 1, 100, "023", LocalDateTime.of(2015, 1, 3, 9, 0), null, "MotFol", "11111111.rtf", null))
			.event(AlkolTestRows.event(302, 1, 100, "UTSKR", LocalDateTime.of(2015, 1, 4, 9, 0), null, "UtrTob", "22222222.rtf", null))
			.event(AlkolTestRows.event(303, 1, 100, "029", LocalDateTime.of(2015, 1, 5, 9, 0), null, null, null, "remissvar polisen.pdf"))
			.event(AlkolTestRows.event(304, 1, 100, "016", LocalDateTime.of(2015, 1, 6, 9, 0), null, null, null, "Planritning.pdf"))
			.event(AlkolTestRows.event(305, 1, 100, "015", LocalDateTime.of(2015, 1, 7, 9, 0), null, null, null, "ritning bar.pdf"))
			.event(AlkolTestRows.event(306, 1, null, "014", LocalDateTime.of(2016, 3, 4, 9, 0), null, "UtrTob", "33333333.rtf", "anmalan 2016.pdf"));

		final var notification = data.factory().notifications().candidates().getFirst();

		assertThat(notification.files().references()).hasSize(9);
		// An investigation, made from UtrTob, and a referral answer go on the errand only; a drawing keeps its category on
		// an event that registers the product
		assertThat(notification.files().onPost()).containsExactly(
			new AssetFile(new FileReference("Planritning.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("ritning bar.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("anmalan.pdf", ATTACHMENTS), null),
			new AssetFile(new FileReference("anmalan 2016.pdf", ATTACHMENTS), null),
			new AssetFile(new FileReference("11111111.rtf", DOCUMENTS), null),
			new AssetFile(new FileReference("44444444.rtf", DOCUMENTS), null));
		// Each file gets its purpose and the time of its event or decision, and a referral answer is material of the
		// investigation
		assertThat(notification.files().detailsOf(new FileReference("remissvar polisen.pdf", ATTACHMENTS)))
			.isEqualTo(new FileDetails(FilePurposes.POLICE_RESPONSE, LocalDateTime.of(2015, 1, 5, 9, 0), true));
		assertThat(notification.files().detailsOf(new FileReference("Planritning.pdf", ATTACHMENTS)))
			.isEqualTo(new FileDetails(FilePurposes.SALES_PREMISES_DRAWING, LocalDateTime.of(2015, 1, 6, 9, 0)));
		assertThat(notification.files().detailsOf(new FileReference("44444444.rtf", DOCUMENTS))).isEqualTo(new FileDetails(null, LocalDateTime.of(2015, 1, 20, 10, 0)));
		// A notification is made under no legal basis of a permit
		assertThat(notification.asset().legalBasisDocuments()).isEmpty();
	}

	@Test
	void aFileNameThatAnotherPlaceAlsoRefersToIsMarked() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, false, true))
			.owner(owner(10, "556677-8899"))
			.view(new PermitViewRow(1, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-2019-100", LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), null))
			.errand(AlkolTestRows.errand(101, 1, "WAAÄ", "IAN-2021-1", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.decision(AlkolTestRows.decision(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 7, 2), null, null, "bilaga.pdf"))
			.decision(AlkolTestRows.decision(201, null, "GTDE", null, null, null, null, "Bilaga.pdf"))
			.decision(AlkolTestRows.decision(202, 100, "BESOVR", LocalDateTime.of(2019, 7, 3, 10, 0), null, null, null, "protokoll.pdf"))
			.event(AlkolTestRows.event(300, 1, 100, "001", LocalDateTime.of(2019, 6, 1, 9, 0), null, null, "22222222.rtf", "ansokan.pdf"))
			.event(AlkolTestRows.event(301, null, 100, "001", LocalDateTime.of(2019, 6, 2, 9, 0), null, null, null, "intyg.pdf"))
			.event(AlkolTestRows.event(302, 1, 101, "001", LocalDateTime.of(2021, 1, 2, 9, 0), null, null, null, "intyg.pdf"))
			// Registered on another place than its errand's, whose files it goes to
			.event(AlkolTestRows.event(303, 2, 100, "001", LocalDateTime.of(2019, 7, 3, 9, 0), null, null, null, "protokoll.pdf"))
			.event(AlkolTestRows.event(400, 2, null, "001", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null, "ANSOKAN.PDF"));

		final var candidate = data.factory().permits().candidates().getFirst();

		assertThat(candidate.files().references()).containsExactlyInAnyOrder(
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
			.view(new PermitViewRow(1, false, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WAPH", null, null, null, 50))
			.decision(AlkolTestRows.decision(200, 100, "GTPH", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null))
			.decision(AlkolTestRows.decision(201, 100, "GTDEÅ", LocalDateTime.of(2022, 1, 1, 10, 0), null, null, null, null))
			.applicant(new ContactRow(50, "5599887766", "Annan", null, null, null, null, null, null, null));

		final var candidate = data.factory().permits().candidates().getFirst();

		assertThat(candidate.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).containsExactlyInAnyOrder(
			tuple(SALES_PLACE_CEASED, null),
			tuple(PERMIT_ENDED, "GTDEÅ 2022-01-01"),
			tuple(DIARY_NUMBER_MISSING, "det beviljande ärendet saknar diarienummer"),
			tuple(APPLICANT_NOT_OWNER, null),
			tuple(UNDERLYING_ERRAND_OPEN, "ArendeID 100"),
			tuple(CERTIFICATE_MISSING, null));
		assertThat(parameters(candidate)).containsEntry("tillstandstyp", List.of("Partihandel")).containsEntry("utfardat", List.of("2019-07-01"));
		assertThat(candidate.asset().titleSuffix()).isEqualTo("partihandel");
	}

	@Test
	void tobaccoPermitWithoutGrantOwnerOrPlace() {
		final var data = new Data()
			.place(place(1, 99))
			.view(new PermitViewRow(1, true, false, false, false))
			.view(new PermitViewRow(2, true, false, false, false));

		final var candidates = data.factory().permits().candidates();

		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).containsExactlyInAnyOrder(
			tuple(OWNER_MISSING, "AgarID 99 finns inte"),
			tuple(DIARY_NUMBER_MISSING, "inget beviljande beslut hittades"),
			tuple(CERTIFICATE_MISSING, null));
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(PLACE_MISSING, DIARY_NUMBER_MISSING, CERTIFICATE_MISSING);
		assertThat(candidates.get(1).errand().title()).isEqualTo("Tobakstillstånd – okänt försäljningsställe");
		// A permit without a grant gets an investigation all the same, without dates and texts
		assertThat(candidates.get(1).investigation()).isEqualTo(new InvestigationData("Tobakstillstånd – okänt försäljningsställe", null, null, InvestigationTexts.NONE));
		// but no decision
		assertThat(candidates).extracting(Candidate::decision).containsOnlyNulls();
	}

	@Test
	void theTobaccoPermitGetsAnInvestigationAndTheEventsOfItsPlaceAsALog() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, false, false))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-2019-1", LocalDate.of(2019, 5, 2), LocalDate.of(2019, 7, 3), null, "Komplett ansökan", List.of("2", "9")))
			// The errand of another place has the note its place shows as a parameter already
			.place(place(2, 10))
			.view(new PermitViewRow(2, true, false, false, false))
			.errand(AlkolTestRows.errand(101, 2, "WADE", "IAN-2019-2", LocalDate.of(2019, 5, 2), LocalDate.of(2019, 7, 3), null, "Notering 2"))
			.decision(AlkolTestRows.decision(1001, 101, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null))
			// The permit is valid from a later day, but the trial was completed when the grant was decided
			.decision(new DecisionRow(1000, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 8, 1), null, null, null, null, "Tillstånd beviljas.", "ka"))
			.event(AlkolTestRows.event(299, 1, 100, "024", LocalDateTime.of(2019, 6, 1, 9, 0), null, null, null, null, "Platsbesök gjort"))
			.event(AlkolTestRows.event(298, 1, 100, "017", LocalDateTime.of(2019, 5, 10, 9, 0), null, null, null, null))
			// The event registers the folköl the place has notified, so it belongs to the notification
			.event(AlkolTestRows.event(300, 1, null, "014", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null, null, "Anmälan folköl"))
			.event(new EventRow(301, 1, null, "024", LocalDateTime.of(2021, 1, 2, 9, 0), "IAN-2021-1", null, null, null, "Samtal med butiken", true))
			.event(AlkolTestRows.event(302, 1, null, "061", LocalDateTime.of(2020, 3, 1, 9, 0), null, null, null, null, "Besök enligt plan"))
			.event(AlkolTestRows.event(303, 1, null, "024", LocalDateTime.of(2020, 4, 1, 9, 0), null, null, null, null))
			// The errand of the event is missing, so the event counts as one of the place
			.event(AlkolTestRows.event(304, 1, 999, "024", LocalDateTime.of(2020, 6, 1, 9, 0), null, null, null, "borttaget.pdf", "Ärendet är borttaget"))
			// Another event of the place refers to the same file, which is therefore not shared with another place
			.event(AlkolTestRows.event(305, 1, null, "024", LocalDateTime.of(2020, 7, 1, 9, 0), null, null, null, "borttaget.pdf", null));

		final var permit = data.factory().permits().candidates().getFirst();

		// Started when the errand of the grant was opened, completed when the permit was granted; every event of the errand is
		// in the journal, also one without a text, and the decision tells who made it
		assertThat(permit.investigation()).isEqualTo(new InvestigationData("Tobakstillstånd – Butik 1", LocalDateTime.of(2019, 5, 2, 0, 0), LocalDateTime.of(2019, 7, 1, 0, 0),
			InvestigationTexts.of(new Part("Noteringar", "Komplett ansökan"), new Part("Händelser", "2019-05-10 017\n2019-06-01 Anteckningar: Platsbesök gjort"),
				new Part("Beslut", "2019-07-01 Tillstånd tobaksförsäljning – detaljhandel, beslutsfattare Karin Andersson: Tillstånd beviljas."))));
		// The errand gets the latest grant as its decision, valid from its own first day and until further notice, without
		// terms (decision 70)
		final var decision = permit.decision();
		assertThat(decision.decidedBy()).isEqualTo("Karin Andersson");
		assertThat(decision.decidedAt()).isEqualTo(LocalDateTime.of(2019, 7, 1, 10, 0));
		assertThat(decision.description()).isEqualTo("Tillstånd tobaksförsäljning – detaljhandel");
		assertThat(decision.justification()).isEqualTo("Tillstånd beviljas.");
		assertThat(decision.validFrom()).isEqualTo(LocalDate.of(2019, 8, 1));
		assertThat(decision.validTo()).isNull();
		assertThat(decision.terms()).isEmpty();
		assertThat(decision.documents()).isEmpty();
		// A tobacco decision has no beverages and no serving hours, which Draken would show as decided (decision 76)
		assertThat(decision.decided()).isEmpty();
		// The place's events without an errand, also the one whose errand is missing, become documentation errands, those
		// with a text or a file, but the one that registers the folköl of the place (decision 71)
		final var documentation = data.factory().documentation().candidates();
		assertThat(documentation).extracting(candidate -> candidate.key().sourceId(), candidate -> candidate.errand().title()).containsExactly(
			tuple("301", "Anteckningar – Butik 1"), tuple("302", "Tillsyn - Tillsynsbesök enl plan – Butik 1"), tuple("304", "Anteckningar – Butik 1"),
			tuple("305", "Anteckningar – Butik 1"));
		final var confidential = documentation.getFirst();
		assertThat(confidential.errand().description()).isEqualTo("""
			Anteckningar på Butik 1 2021-01-02, som inte hörde till något ärende.
			Diarienummer: IAN-2021-1
			Händelsen är markerad med sekretess i AlkOL.
			Samtal med butiken""");
		assertThat(parameters(confidential)).containsEntry("alkolHandelseId", List.of("301")).containsEntry("handelsetyp", List.of("Anteckningar"))
			.containsEntry("handelsedatum", List.of("2021-01-02")).containsEntry("diarienummer", List.of("IAN-2021-1")).containsEntry("sekretess", List.of("Ja"))
			.containsEntry("migrerad", List.of("2026-10-12 från AlkOL"));
		assertThat(confidential.errand().externalTags()).containsEntry("diarienummer", "IAN-2021-1");
		assertThat(confidential.holder().legalId().number()).isEqualTo("5566778899");
		assertThat(confidential.permitLink().permitKey()).isEqualTo(new ItemKey(ALKOL, TILLSTAND, "1"));
		// The file of an event came in when the event was
		final var withFile = documentation.get(2);
		assertThat(withFile.files().references()).containsExactly(new FileReference("borttaget.pdf", ATTACHMENTS));
		assertThat(withFile.files().detailsOf(new FileReference("borttaget.pdf", ATTACHMENTS)).received()).isEqualTo(LocalDateTime.of(2020, 6, 1, 9, 0));
		assertThat(parameters(documentation.get(1))).doesNotContainKeys("diarienummer", "sekretess");
		// The permit has neither their texts nor their files
		assertThat(parameters(permit)).doesNotContainKey("handelselogg");
		assertThat(permit.files().references()).doesNotContain(new FileReference("borttaget.pdf", ATTACHMENTS));
		// A handler AlkOL has no user for stands with the number
		assertThat(parameters(permit)).containsEntry("handlaggare", List.of("Karin Andersson", "9"));
		final var other = data.factory().permits().candidates().get(1);
		assertThat(parameters(other)).containsEntry("noteringar", List.of("Notering 2"));
		assertThat(other.investigation().texts().parts()).extracting(Part::heading).doesNotContain("Noteringar");
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
			.view(new PermitViewRow(1, true, false, false, false))
			.view(new PermitViewRow(2, true, false, false, false))
			.view(new PermitViewRow(3, true, false, false, false))
			.view(new PermitViewRow(4, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-1", null, LocalDate.of(2019, 7, 1), null))
			.errand(AlkolTestRows.errand(101, 2, "WADE", "ian 1", null, LocalDate.of(2019, 7, 1), null))
			.decision(AlkolTestRows.decision(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null))
			.decision(AlkolTestRows.decision(201, 101, "GTDE", null, null, null, null, null));

		final var candidates = data.factory().permits().candidates();

		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(OWNER_COPY_DIFFERS, LEGAL_ID_ON_SEVERAL_OWNERS, DIARY_NUMBER_ON_SEVERAL, CERTIFICATE_MISSING);
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(LEGAL_ID_ON_SEVERAL_OWNERS, DIARY_NUMBER_ON_SEVERAL, ISSUE_DATE_MISSING, CERTIFICATE_MISSING);
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
			.view(new PermitViewRow(1, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-1", null, LocalDate.of(2019, 7, 1), 50))
			.decision(AlkolTestRows.decision(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 7, 2), "TILLSTT", "bevis.rtf", null))
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
			.view(new PermitViewRow(1, true, false, false, false));

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
			.errand(AlkolTestRows.errand(100, 1, "Fföl", "IAN-2010-1", LocalDate.of(2010, 1, 1), LocalDate.of(2010, 2, 1), null))
			.errand(AlkolTestRows.errand(101, 1, "SÖL", "IAN-2015-1", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), null))
			.event(AlkolTestRows.event(300, 1, 101, "015", LocalDateTime.of(2015, 1, 2, 9, 0), "IAN-2015-1", null, "33333333.rtf", null))
			.event(AlkolTestRows.event(301, 2, null, "014", LocalDateTime.of(2012, 3, 4, 9, 0), "IAN-2012-1", null, null, null))
			.event(AlkolTestRows.event(302, 3, null, "999", LocalDateTime.of(2009, 3, 4, 9, 0), null, null, null, null));

		final var candidates = data.factory().notifications().candidates();

		assertThat(candidates).extracting(Candidate::key).containsExactly(
			new ItemKey(ALKOL, ANMALAN, "1-FOLKOL"), new ItemKey(ALKOL, ANMALAN, "2-FOLKOL"), new ItemKey(ALKOL, ANMALAN, "3-FOLKOL"), new ItemKey(ALKOL, ANMALAN, "4-FOLKOL"));

		final var latestServing = candidates.get(0);
		assertThat(latestServing.type()).isEqualTo(ErrandType.FOLKOL_SERVING_NOTIFICATION);
		assertThat(latestServing.ruleHits()).isEmpty();
		assertThat(latestServing.errand().title()).isEqualTo("Anmälan om servering av folköl – Butik 1");
		assertThat(latestServing.errand().description()).startsWith("Anmälan om servering av folköl för Butik 1.\n");
		assertThat(latestServing.errand().labelPaths()).containsExactly("CATEGORYROOT/ALCOHOL/FOLKOL_SERVING_NOTIFICATION");
		assertThat(latestServing.errand().externalTags()).containsExactly(Map.entry("alkolAnmalan", "1-FOLKOL"), Map.entry("diarienummer", "IAN-2015-1"));
		assertThat(parameters(latestServing)).containsEntry("anmald", List.of("2010-01-01")).containsEntry("produkt", List.of("Folköl")).doesNotContainKey("anmaldUppskattat");
		assertThat(latestServing.asset().issued()).isEqualTo(LocalDate.of(2010, 1, 1));
		assertThat(latestServing.asset().description()).isEqualTo("Anmälan om servering av folköl på Butik 1");
		assertThat(latestServing.files().references()).containsExactly(new FileReference("33333333.rtf", DOCUMENTS));
		assertThat(latestServing.persons()).isEmpty();

		final var eventOnly = candidates.get(1);
		assertThat(eventOnly.type()).isEqualTo(ErrandType.FOLKOL_SALES_NOTIFICATION);
		assertThat(eventOnly.errand().externalTags()).containsEntry("diarienummer", "IAN-2012-1");
		assertThat(parameters(eventOnly)).containsEntry("anmald", List.of("2012-03-04"));

		final var flagOnlyServingPlace = candidates.get(2);
		assertThat(flagOnlyServingPlace.type()).isEqualTo(ErrandType.FOLKOL_SERVING_NOTIFICATION);
		assertThat(flagOnlyServingPlace.ruleHits()).extracting(RuleHit::rule).containsExactly(NOTIFICATION_ONLY_FLAG);
		assertThat(parameters(flagOnlyServingPlace)).containsEntry("anmald", List.of("2009-03-04")).containsEntry("anmaldUppskattat", List.of("Ja")).doesNotContainKey("diarienummer");
		assertThat(flagOnlyServingPlace.asset().parameters()).containsEntry("anmaldUppskattat", "true")
			.containsKeys("premisesName", "premisesStreet", "premisesPostalAddress", "premisesPhone", "produkt", "migreradFran")
			.doesNotContainKeys("premisesRestaurantNumber", "permitHolderName", "permitHolderOrgNumber");
		assertThat(flagOnlyServingPlace.errand().externalTags()).containsOnlyKeys("alkolAnmalan");

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
			.errand(AlkolTestRows.errand(100, 1, "ec", "IAN-1", null, null, null))
			.event(AlkolTestRows.event(300, 1, null, "047", LocalDateTime.of(2022, 3, 4, 9, 0), null, null, null, null));

		final var result = data.factory().notifications();

		assertThat(result.candidates()).extracting(Candidate::key, Candidate::type).containsExactly(
			tuple(new ItemKey(ALKOL, ANMALAN, "1-ECIGARETTER"), ErrandType.ECIGARETTE_NOTIFICATION),
			tuple(new ItemKey(ALKOL, ANMALAN, "1-TOBAKSFRI_NIKOTIN"), ErrandType.TOBACCO_FREE_NICOTINE_NOTIFICATION));
		assertThat(result.candidates().get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.containsExactly(tuple(NOTIFICATION_DATE_MISSING, "E-cigaretter och påfyllningsbehållare, anmälan saknar datum"), tuple(UNDERLYING_ERRAND_OPEN, "ArendeID 100, diarienummer IAN-1"));
		assertThat(result.candidates().get(0).errand().labelPaths()).containsExactly("CATEGORYROOT/TOBACCO/ECIGARETTE_SALES_NOTIFICATION");
		assertThat(result.candidates().get(1).errand().labelPaths()).containsExactly("CATEGORYROOT/TOBACCO/TOBACCO_FREE_NICOTINE_SALES_NOTIFICATION");
		assertThat(result.candidates().get(1).errand().title()).isEqualTo("Anmälan om försäljning av tobaksfria nikotinprodukter – Butik 1");
		assertThat(result.notices()).singleElement().satisfies(notice -> {
			assertThat(notice.key()).isEqualTo(new ItemKey(ALKOL, ANMALAN, "2-INGEN"));
			assertThat(notice.outcome()).isEqualTo(Outcome.MIGRERAS_INTE);
		});
	}

	@Test
	void anOpenErrandThatAPermitOrNotificationBuildsOnIsHeldForReview() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, false, false))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-2019-100", LocalDate.of(2019, 6, 1), null, null))
			.errand(AlkolTestRows.errand(101, 1, "Fföl", "IAN-2010-1", LocalDate.of(2010, 1, 1), LocalDate.of(2010, 2, 1), null))
			.errand(AlkolTestRows.errand(102, 1, "SÖL", null, LocalDate.of(2015, 1, 1), null, null))
			.decision(AlkolTestRows.decision(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null));

		final var permit = data.factory().permits().candidates().get(0);
		final var notification = data.factory().notifications().candidates().get(0);

		assertThat(permit.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).containsExactly(tuple(UNDERLYING_ERRAND_OPEN, "ArendeID 100, diarienummer IAN-2019-100"), tuple(CERTIFICATE_MISSING, null));
		assertThat(notification.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).containsExactly(tuple(UNDERLYING_ERRAND_OPEN, "ArendeID 102"));
	}

	@Test
	void inspectionErrands() {
		final var data = new Data()
			.place(place(1, 10))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false, false))
			.errand(AlkolTestRows.errand(400, 1, "Tills", "TIL-1", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), 50, "Klagomål", List.of("2")))
			.errand(AlkolTestRows.errand(401, 1, "IT", null, LocalDate.of(2020, 1, 1), null, null))
			.errand(AlkolTestRows.errand(402, 2, "IT", null, LocalDate.of(2020, 1, 1), null, null))
			.decision(new DecisionRow(500, 400, "VT", LocalDateTime.of(2020, 1, 20, 9, 0), null, null, null, "varning.pdf", null, "Varning meddelas.", "XY"))
			.event(new EventRow(600, 1, 400, "024", LocalDateTime.of(2020, 1, 10, 9, 0), "til-1", null, null, null, "Kontrollköp gjort", true))
			.event(AlkolTestRows.event(601, 1, 400, "017", LocalDateTime.of(2020, 1, 15, 9, 0), "TIL-0", null, null, null))
			.applicant(new ContactRow(50, "5566778899", "Bolaget", null, null, null, null, null, null, null));

		final var candidates = data.factory().inspections().candidates();

		assertThat(candidates).extracting(Candidate::key).containsExactly(new ItemKey(ALKOL, TILLSYN, "400"), new ItemKey(ALKOL, TILLSYN, "401"));
		final var external = candidates.get(0);
		assertThat(external.type()).isEqualTo(ErrandType.TOBACCO_INSPECTION);
		assertThat(external.ruleHits()).isEmpty();
		assertThat(external.errand().title()).isEqualTo("Tillsynsärende – Butik 1");
		assertThat(external.errand().labelPaths()).containsExactly("TAGROOT/TOBACCO_INSPECTION");
		assertThat(external.asset()).isNull();
		assertThat(external.permitLink().permitKey()).isEqualTo(new ItemKey(ALKOL, TILLSTAND, "1"));
		assertThat(external.permitLink().basis()).isEqualTo(Basis.SAME_HOLDER);
		assertThat(parameters(external)).containsEntry("beslut", List.of("Varning, 2020-01-20")).containsEntry("handlaggare", List.of("Karin Andersson"));
		assertThat(external.errand().description()).contains("\nHandläggare: Karin Andersson\n");
		assertThat(external.files().references()).containsExactly(new FileReference("varning.pdf", DOCUMENTS));
		// A decision maker AlkOL has no user for stands with the code
		assertThat(external.investigation().texts()).isEqualTo(InvestigationTexts.of(new Part("Noteringar", "Klagomål"),
			new Part("Händelser", "2020-01-10 Anteckningar (sekretess): Kontrollköp gjort\n2020-01-15 017 (diarienummer TIL-0)"),
			new Part("Beslut", "2020-01-20 Varning, beslutsfattare XY: Varning meddelas.")));
		// An inspection errand of AlkOL is an internal inspection too, and has the texts as parameters (decision 77)
		assertThat(external.investigation().protocol()).isEqualTo(InspectionProtocols.INTERNAL_INSPECTION);
		assertThat(external.investigation().protocolParameters()).extracting(Parameter::key).containsExactly("businessName", "businessOrgNumber", "businessAddress", "inspectionType", "inspectionDate", "inspectorName");
		assertThat(parameters(external)).containsEntry("noteringar", List.of("Klagomål")).containsEntry("beslutstexter", List.of("2020-01-20 Varning, beslutsfattare XY: Varning meddelas."));
		final var internal = candidates.get(1);
		assertThat(internal.investigation().texts()).isEqualTo(InvestigationTexts.NONE);
		assertThat(parameters(internal)).doesNotContainKey("handlaggare");
		assertThat(internal.ruleHits()).extracting(RuleHit::rule).containsExactly(ERRAND_OPEN);
		assertThat(internal.errand().labelPaths()).containsExactly("TAGROOT/TOBACCO_INSPECTION", "CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION");
		assertThat(internal.asset()).isNull();
		assertThat(internal.holder()).isNull();
	}

	@Test
	void theFilesOfAnInspectionReportAreMaterialOfTheInvestigation() {
		final var data = new Data()
			.place(place(1, 10))
			.view(new PermitViewRow(1, true, false, false, false))
			.errand(AlkolTestRows.errand(400, 1, "Tills", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null))
			.event(AlkolTestRows.event(600, 1, 400, "063", LocalDateTime.of(2020, 1, 10, 9, 0), null, null, null, "rapport.pdf"))
			.event(AlkolTestRows.event(601, 1, 400, "024", LocalDateTime.of(2020, 1, 11, 9, 0), null, null, null, "anteckning.pdf"));

		final var errand = data.factory().inspections().candidates().getFirst();

		// The type of the event tells, whatever the file is called (decision 60)
		assertThat(errand.files().detailsOf(new FileReference("rapport.pdf", ATTACHMENTS)).investigation()).isTrue();
		assertThat(errand.files().detailsOf(new FileReference("anteckning.pdf", ATTACHMENTS)).investigation()).isFalse();
	}

	@Test
	void anInspectionErrandStillOpenEndsAtItsLatestEventOrDecision() {
		final var data = new Data()
			.place(place(1, 10))
			.view(new PermitViewRow(1, true, false, false, false))
			.errand(AlkolTestRows.errand(401, 1, "IT", null, LocalDate.of(2020, 1, 1), null, null))
			.decision(AlkolTestRows.decision(500, 401, "VT", LocalDateTime.of(2020, 3, 1, 9, 0), null, null, null, null))
			.event(AlkolTestRows.event(600, 1, 401, "024", LocalDateTime.of(2020, 3, 5, 10, 0), null, null, null, null))
			.event(AlkolTestRows.event(601, 1, 401, "024", null, null, null, null, null));

		final var investigation = data.factory().inspections().candidates().getFirst().investigation();

		assertThat(investigation.startedAt()).isEqualTo(LocalDateTime.of(2020, 1, 1, 0, 0));
		assertThat(investigation.completedAt()).isEqualTo(LocalDateTime.of(2020, 3, 5, 10, 0));
	}

	@Test
	void inspectionErrandsOfAllPlaces() {
		final var data = new Data()
			.errand(AlkolTestRows.errand(402, 2, "IT", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null))
			// An errand without a sales place has no permit to look for
			.errand(AlkolTestRows.errand(403, null, "it", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null));

		final var candidates = new AlkolCandidateFactory(data.build(), TODAY, TestData.properties(ErrandType.TOBACCO_INSPECTION, false, Map.of("it", "X"))).inspections().candidates();

		assertThat(candidates).hasSize(2).allSatisfy(candidate -> {
			assertThat(candidate.ruleHits()).extracting(RuleHit::rule).containsExactly(PLACE_MISSING);
			assertThat(candidate.permitLink().permitKey()).isNull();
		});
	}

	@Test
	void theInspectionErrandsOfACeasedSalesPlaceAreNotMigrated() {
		final var data = new Data()
			.place(place(1, 10))
			.place(place(3, 10).ceased())
			.view(new PermitViewRow(1, true, false, false, false))
			.view(new PermitViewRow(3, true, false, false, false))
			.errand(AlkolTestRows.errand(400, 1, "Tills", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null))
			.errand(AlkolTestRows.errand(404, 3, "Tills", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null));

		assertThat(data.factory().inspections().candidates()).extracting(candidate -> candidate.key().sourceId()).containsExactly("400");
		final var allPlaces = TestData.properties(ErrandType.TOBACCO_INSPECTION, false, Map.of("tills", "X"));
		assertThat(new AlkolCandidateFactory(data.build(), TODAY, allPlaces).inspections().candidates()).extracting(candidate -> candidate.key().sourceId()).containsExactly("400");
	}

	@Test
	void inspectionVisits() {
		final var data = new Data()
			.place(place(1, 10))
			.place(place(2, 10))
			.place(place(3, 10).ceased())
			.view(new PermitViewRow(1, true, false, false, false))
			.view(new PermitViewRow(3, true, false, false, false))
			.visit(new VisitRow(900, 1, LocalDate.of(2024, 5, 3), null, "21:30", "22:15", "Kontr", "Köpet nekades.", "55555555.rtf", "protokoll.pdf", List.of("4"), null, 7,
				List.of("Tobak", "E-cigaretter"), true))
			.round(new RoundRow(7, "Kontrollköp vecka 18", LocalDate.of(2024, 5, 3), LocalDate.of(2024, 5, 3), null))
			.visit(VisitTestRows.visit(901, 2, LocalDate.of(2024, 5, 3), null, null, "Kontr", null, null, null))
			.visit(VisitTestRows.visit(902, 3, LocalDate.of(2024, 5, 3), null, null, "Kontr", null, null, null))
			.visit(VisitTestRows.visit(903, 1, TODAY.plusDays(1), null, null, null, null, null, null))
			.remark(new RemarkRow(900, "Rekl I", null, null))
			.visitFile(900, "foto.jpg");

		final var candidates = data.factory().visits().candidates();

		assertThat(candidates).extracting(Candidate::key).containsExactly(new ItemKey(ALKOL, TILLSYNSBESOK, "900"), new ItemKey(ALKOL, TILLSYNSBESOK, "903"));
		final var visit = candidates.getFirst();
		assertThat(visit.type()).isEqualTo(ErrandType.TOBACCO_INSPECTION_VISIT);
		assertThat(visit.ruleHits()).isEmpty();
		assertThat(visit.holder()).isNull();
		// The sales place has no granting decision, so the day of the visit cannot tell which holder it concerned (decision 61)
		assertThat(visit.permitLink().permitKey()).isEqualTo(new ItemKey(ALKOL, TILLSTAND, "1"));
		assertThat(visit.permitLink().basis()).isEqualTo(Basis.NO_ISSUE_DATE);
		assertThat(visit.errand().title()).isEqualTo("Tillsynsbesök – Butik 1");
		assertThat(visit.errand().description()).isEqualTo("""
			Tillsynsbesök på Butik 1 2024-05-03.
			Tillsynsart: Kontrollköp
			Tid: 21:30–22:15
			Tillsynsomgång: Kontrollköp vecka 18, 2024-05-03
			Tillsynen gällde: Tobak, E-cigaretter
			Anmält besök: Ja
			Kontrollant: Per Eriksson
			Anmärkningar:
			- Påträngande reklam i butik
			Anteckning: Köpet nekades.""");
		assertThat(parameters(visit))
			.containsEntry("alkolTillsynsbesokId", List.of("900"))
			.containsEntry("besoksdatum", List.of("2024-05-03"))
			.containsEntry("besokstid", List.of("21:30–22:15"))
			.containsEntry("tillsynsart", List.of("Kontrollköp"))
			.containsEntry("tillsynsomgang", List.of("Kontrollköp vecka 18, 2024-05-03"))
			.containsEntry("tillsynenGallde", List.of("Tobak", "E-cigaretter"))
			.containsEntry("anmaltBesok", List.of("Ja"))
			.containsEntry("kontrollant", List.of("Per Eriksson"))
			.doesNotContainKey("serveringspersonal")
			.containsEntry("anmarkningar", List.of("Påträngande reklam i butik"))
			.containsEntry("anteckning", List.of("Köpet nekades."))
			.containsEntry("migrerad", List.of(TODAY + " från AlkOL"));
		assertThat(visit.investigation().texts()).isEqualTo(InvestigationTexts.of(new Part("Tillsynsomgång", "Kontrollköp vecka 18, 2024-05-03"), new Part("Anteckning", "Köpet nekades."),
			new Part("Anmärkningar", "Påträngande reklam i butik")));
		// Draken has no protocol for a visit to a sales place
		assertThat(visit.investigation().protocol()).isNull();
		assertThat(visit.investigation().protocolParameters()).isEmpty();
		assertThat(visit.measures()).containsExactly(new MeasureData("Påträngande reklam i butik", null, LocalDateTime.of(2024, 5, 3, 22, 15)));
		// Every file of the visit is material of its investigation (decision 60)
		assertThat(visit.files().references()).allSatisfy(file -> assertThat(visit.files().detailsOf(file).investigation()).isTrue());
		assertThat(candidates.get(1).measures()).isEmpty();
		assertThat(parameters(candidates.get(1))).doesNotContainKeys("tillsynsomgang", "tillsynenGallde", "anmaltBesok");
		assertThat(visit.place().name()).isEqualTo("Butik 1");
		assertThat(visit.files().references()).containsExactly(new FileReference("55555555.rtf", DOCUMENTS), new FileReference("protokoll.pdf", ATTACHMENTS), new FileReference("foto.jpg", ATTACHMENTS));
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::toReportText)
			.containsExactly("G28 Tillsynsbesöket saknar datum eller har ett datum efter migreringsdagen och kan vara planerat men inte genomfört: " + TODAY.plusDays(1));

		final var allPlaces = TestData.properties(ErrandType.TOBACCO_INSPECTION_VISIT, false, Map.of());
		assertThat(new AlkolCandidateFactory(data.build(), TODAY, allPlaces).visits().candidates()).extracting(candidate -> candidate.key().sourceId())
			.as("A ceased sales place gets no visits, whatever the settings say")
			.containsExactly("900", "901", "903");
	}

	@Test
	void theRegistrationsOfAFormerHolderStayOnTheErrand() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, false, false, false))
			.owner(owner(10, "5566778899"))
			.applicant(new ContactRow(50, "5599887766", "Förra ägaren", null, null, null, null, null, null, null))
			.applicant(new ContactRow(51, "556677-8899", "Bolaget", null, null, null, null, null, null, null))
			.errand(AlkolTestRows.errand(100, 1, "Fföl", "IAN-2009-1", LocalDate.of(2009, 1, 10), LocalDate.of(2009, 3, 1), 50))
			.event(AlkolTestRows.event(300, 1, 100, "014", LocalDateTime.of(2009, 1, 10, 9, 0), null, null, null, "anmalan-a.pdf"))
			.event(AlkolTestRows.event(301, 1, null, "BeaFol", LocalDateTime.of(2009, 2, 5, 9, 0), null, "BekFol", "11111111.rtf", null))
			.errand(AlkolTestRows.errand(101, 1, "SÖL", "IAN-2019-1", LocalDate.of(2019, 5, 1), LocalDate.of(2019, 6, 1), 51))
			.event(AlkolTestRows.event(302, 1, 101, "015", LocalDateTime.of(2019, 5, 1, 9, 0), null, null, null, "anmalan-b.pdf"))
			.event(AlkolTestRows.event(303, 1, null, "023", LocalDateTime.of(2019, 5, 10, 9, 0), null, "MotFol", "22222222.rtf", null));

		final var notification = data.factory().notifications().candidates().getFirst();

		assertThat(notification.files().references()).containsExactlyInAnyOrder(new FileReference("anmalan-a.pdf", ATTACHMENTS), new FileReference("11111111.rtf", DOCUMENTS),
			new FileReference("anmalan-b.pdf", ATTACHMENTS), new FileReference("22222222.rtf", DOCUMENTS));
		assertThat(notification.files().onPost()).extracting(AssetFile::reference)
			.containsExactlyInAnyOrder(new FileReference("anmalan-b.pdf", ATTACHMENTS), new FileReference("22222222.rtf", DOCUMENTS));
		assertThat(notification.ruleHits()).extracting(RuleHit::rule, RuleHit::detail).containsExactly(tuple(NOTIFICATION_OF_FORMER_HOLDER, "ArendeID 100, diarienummer IAN-2009-1"));
		assertThat(notification.type()).isEqualTo(ErrandType.FOLKOL_SERVING_NOTIFICATION);
		assertThat(parameters(notification)).containsEntry("anmald", List.of("2019-05-01")).containsEntry("diarienummer", List.of("IAN-2019-1"));
	}

	@Test
	void aNotificationOnlyAFormerHolderMadeIsHeldForReview() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, false, false, false))
			.owner(owner(10, "5566778899"))
			.applicant(new ContactRow(50, "5599887766", "Förra ägaren", null, null, null, null, null, null, null))
			.errand(AlkolTestRows.errand(100, 1, "Fföl", null, LocalDate.of(2009, 1, 10), LocalDate.of(2009, 3, 1), 50))
			.event(AlkolTestRows.event(300, 1, 100, "014", LocalDateTime.of(2009, 1, 10, 9, 0), null, null, null, "anmalan-a.pdf"));

		final var notification = data.factory().notifications().candidates().getFirst();

		assertThat(notification.ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.containsExactly(tuple(NOTIFICATION_OF_FORMER_HOLDER, "ArendeID 100"), tuple(APPLICANT_NOT_OWNER, "anmälan är gjord av en tidigare ägare"));
		assertThat(notification.files().onPost()).isEmpty();
		assertThat(parameters(notification)).containsEntry("anmald", List.of("2009-01-10"));
	}

	@Test
	void aConfirmationDoesNotDecideWhetherFolkolIsServed() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, false, false, false))
			.owner(owner(10, "5566778899"))
			.errand(AlkolTestRows.errand(100, 1, "SÖL", "IAN-2020-1", LocalDate.of(2020, 1, 10), LocalDate.of(2020, 2, 1), null))
			.event(AlkolTestRows.event(300, 1, null, "015", LocalDateTime.of(2020, 1, 10, 9, 0), null, null, null, null))
			.event(AlkolTestRows.event(301, 1, null, "BeaFol", LocalDateTime.of(2020, 1, 14, 9, 0), null, null, null, "bekraftelse.pdf"));

		final var notification = data.factory().notifications().candidates().getFirst();

		assertThat(notification.type()).isEqualTo(ErrandType.FOLKOL_SERVING_NOTIFICATION);
		assertThat(notification.files().onPost()).extracting(AssetFile::reference).containsExactly(new FileReference("bekraftelse.pdf", ATTACHMENTS));
	}

	@Test
	void theTextsOfANotificationGoInItsEventLogAndItsErrandsNotes() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, false, false, false))
			.owner(owner(10, "5566778899"))
			.errand(AlkolTestRows.errand(100, 1, "Fföl", "IAN-2020-1", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null, "Anteckning om anmälan"))
			.event(AlkolTestRows.event(300, 1, 100, "014", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null, null, "Anmälan om folköl"))
			.event(AlkolTestRows.event(301, 1, null, "BeaFol", LocalDateTime.of(2020, 1, 5, 9, 0), null, null, null, null, "Bekräftelse skickad"))
			// The place has no permit, so its other events without an errand are filed under the notification (decision 71)
			.event(AlkolTestRows.event(302, 1, null, "024", LocalDateTime.of(2020, 3, 1, 9, 0), null, null, null, null, "Samtal med butiken"));

		final var notification = data.factory().notifications().candidates().getFirst();

		assertThat(parameters(notification)).containsEntry("noteringar", List.of("Anteckning om anmälan"))
			.containsEntry("handelselogg", List.of("2020-01-01|014|Anmälan om folköl", "2020-01-05|BeaFol|Bekräftelse skickad"));
		assertThat(data.factory().documentation().candidates()).singleElement()
			.satisfies(documentation -> assertThat(documentation.key()).isEqualTo(new ItemKey(ALKOL, DOKUMENTATION, "302")))
			.satisfies(documentation -> assertThat(documentation.permitLink().permitKey()).isEqualTo(notification.key()))
			.satisfies(documentation -> assertThat(documentation.errand().description()).endsWith("Samtal med butiken"));
	}

	@Test
	void aNotificationErrandWithoutAnOpeningDayIsDatedByTheDayItWasRegistered() {
		final var data = new Data()
			.place(place(1, 10).withFlags(false, false, true, false))
			.owner(owner(10, "5566778899"))
			.errand(new ErrandRow(100, 1, "ec", "IAN-1", null, LocalDate.of(2015, 4, 1), null, null, List.of(), LocalDate.of(2015, 3, 2)));

		final var notification = data.factory().notifications().candidates().getFirst();

		assertThat(notification.ruleHits()).isEmpty();
		assertThat(parameters(notification)).containsEntry("anmald", List.of("2015-03-02"));
	}

	@Test
	void decisionsWithoutADateArePlacedByTheirIds() {
		final var data = new Data()
			.place(place(1, 10))
			.place(place(2, 10))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false, false))
			.view(new PermitViewRow(2, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-2019-1", LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), null))
			.errand(AlkolTestRows.errand(101, 2, "WADE", "IAN-2019-2", LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), null))
			.decision(AlkolTestRows.decision(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "TillstT", "11111111.rtf", null))
			// An ending entered after the grant, without a date
			.decision(AlkolTestRows.decision(201, 100, "GTDEÅ", null, null, null, null, null))
			.decision(AlkolTestRows.decision(202, 101, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "TillstT", "22222222.rtf", null))
			// A grant entered after the dated one, without a date
			.decision(AlkolTestRows.decision(203, 101, "GTDE", null, null, null, null, null));

		final var permits = data.factory().permits().candidates();

		assertThat(permits.get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(PERMIT_ENDED, "GTDEÅ utan datum"));
		assertThat(permits.get(1).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).contains(tuple(UNPARSEABLE, "det beviljande beslutet 203 saknar datum och kan vara det senaste"));
		assertThat(parameters(permits.get(1))).containsEntry("utfardat", List.of("2019-07-01"));
	}

	@Test
	void aKindOfPermitTheViewDoesNotHoldIsHeldForReview() {
		final var data = new Data()
			.place(place(1, 10))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false, true))
			.errand(AlkolTestRows.errand(100, 1, "WADE", "IAN-2019-1", LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), null))
			.decision(AlkolTestRows.decision(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "TillstT", "11111111.rtf", null));

		assertThat(data.factory().permits().candidates().getFirst().ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.contains(tuple(UNPARSEABLE, "tillståndstypen i FS_GT_Tobak går inte att tolka"));
	}

	@Test
	void theInspectionsOfAPlaceWhosePermitHasEndedAreNotMigrated() {
		final var data = new Data()
			.place(place(1, 10))
			.place(place(2, 10))
			.view(new PermitViewRow(1, true, false, false, false))
			.view(new PermitViewRow(2, true, false, false, false))
			.errand(AlkolTestRows.errand(100, 1, "WADE", null, LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), null))
			.errand(AlkolTestRows.errand(101, 2, "WADE", null, LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), null))
			.decision(AlkolTestRows.decision(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null))
			.decision(AlkolTestRows.decision(201, 101, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null))
			.decision(AlkolTestRows.decision(202, 101, "GTDEU", LocalDateTime.of(2021, 3, 1, 10, 0), null, null, null, null))
			.errand(AlkolTestRows.errand(400, 1, "Tills", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null))
			.errand(AlkolTestRows.errand(401, 2, "Tills", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null))
			.visit(VisitTestRows.visit(900, 1, LocalDate.of(2024, 5, 3), null, null, "Kontr", null, null, null))
			.visit(VisitTestRows.visit(901, 2, LocalDate.of(2024, 5, 3), null, null, "Kontr", null, null, null));

		assertThat(data.factory().inspections().candidates()).extracting(candidate -> candidate.key().sourceId()).containsExactly("400");
		assertThat(data.factory().visits().candidates()).extracting(candidate -> candidate.key().sourceId()).containsExactly("900");
	}

	private static Map<String, List<String>> parameters(final Candidate candidate) {
		return candidate.errand().parameters().stream().collect(Collectors.toMap(Parameter::key, Parameter::values));
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
		private final List<VisitRow> visits = new ArrayList<>();
		private final Map<Integer, List<RemarkRow>> remarks = new HashMap<>();
		private final Map<Integer, List<String>> visitFiles = new HashMap<>();
		private final Map<Integer, RoundRow> rounds = new HashMap<>();

		Data round(final RoundRow row) {
			rounds.put(row.id(), row);
			return this;
		}

		Data visit(final VisitRow row) {
			visits.add(row);
			return this;
		}

		Data remark(final RemarkRow row) {
			remarks.computeIfAbsent(row.tillfalleNr(), _ -> new ArrayList<>()).add(row);
			return this;
		}

		Data visitFile(final int tillfalleNr, final String name) {
			visitFiles.computeIfAbsent(tillfalleNr, _ -> new ArrayList<>()).add(name);
			return this;
		}

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
				new AlkolSnapshot.CodeLists(
					Map.of("GTDE", "Tillstånd tobaksförsäljning – detaljhandel", "AÄDE", "Godkänd anmälan ändring i bolag", "VT", "Varning"),
					Map.of("024", "Anteckningar", "061", "Tillsyn - Tillsynsbesök enl plan"),
					Map.of("Tills", "Tillsynsärende", "IT", "Inre tillsyn"),
					Map.of("LBK", "Livsmedelsbutik, butikskedja", "SERV", "Serveringsställe grill/pizza"),
					Map.of("12", "City"),
					Map.of("VD", "Verkställande direktör"),
					// The decision makers by the codes of the users (AnvändarKod)
					Map.of("KA", "Karin Andersson", "2", "Fel användare")),
				new InspectionVisitRows(visits, remarks, visitFiles, Map.of("Kontr", "Kontrollköp"), Map.of("Rekl I", "Påträngande reklam i butik"),
					// The handlers by the numbers of the users (AnvID), the codes the visits and errands name them with
					Map.of("4", "Per Eriksson", "2", "Karin Andersson"), rounds));
		}

		AlkolCandidateFactory factory() {
			return new AlkolCandidateFactory(build(), TODAY, PROPERTIES);
		}
	}
}
