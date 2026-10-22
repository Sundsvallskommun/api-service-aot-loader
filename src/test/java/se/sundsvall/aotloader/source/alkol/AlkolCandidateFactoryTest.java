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
import se.sundsvall.aotloader.source.InspectionVisitRows;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.RoundRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;
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
import static se.sundsvall.aotloader.service.model.Kind.TILLSYNSBESOK;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;
import static se.sundsvall.aotloader.service.model.ReviewRule.APPLICANT_NOT_OWNER;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_MISSING;
import static se.sundsvall.aotloader.service.model.ReviewRule.CERTIFICATE_OLDER;
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
import static se.sundsvall.aotloader.service.model.ReviewRule.UNDERLYING_ERRAND_OPEN;
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
			.decision(new DecisionRow(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 7, 2), "TillstT", "11111111.rtf", "bilaga.pdf"))
			.decision(new DecisionRow(201, 101, "GTDE", LocalDateTime.of(2015, 2, 1, 10, 0), null, null, null, null))
			.decision(new DecisionRow(202, 102, "AÄDE", LocalDateTime.of(2021, 2, 1, 10, 0), null, null, null, null))
			.decision(new DecisionRow(203, 101, "GTDEU", LocalDateTime.of(2016, 1, 1, 10, 0), null, null, null, null))
			.event(new EventRow(300, 1, 100, "001", LocalDateTime.of(2019, 6, 1, 9, 0), "IAN-2019-100", null, "22222222.rtf", "ansokan.pdf"))
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
		// An attachment whose name does not tell it is a decision goes on the errand only
		assertThat(candidate.assetFiles()).containsExactly(new AssetFile(new FileReference("11111111.rtf", DOCUMENTS), "Tillståndsbevis"));
		// The certificate is also the document of the grant (decision 58)
		assertThat(candidate.asset().legalBasisDocuments()).containsExactly(new FileReference("11111111.rtf", DOCUMENTS));
	}

	@Test
	void theCertificateIsThatOfTheLatestGrantThatHasOneAndAnOlderOneIsNoted() {
		final var data = new Data()
			.place(place(1, 10))
			.place(place(2, 10))
			.place(place(3, 10))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false))
			.view(new PermitViewRow(2, true, false, false))
			.view(new PermitViewRow(3, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-2019-1", null, LocalDate.of(2019, 7, 1), null))
			.errand(new ErrandRow(101, 1, "WADE", "IAN-2023-1", null, LocalDate.of(2023, 3, 1), null))
			.errand(new ErrandRow(200, 2, "WADE", "IAN-2019-2", null, LocalDate.of(2019, 7, 1), null))
			.errand(new ErrandRow(201, 2, "WADE", "IAN-2022-2", null, LocalDate.of(2022, 1, 1), null))
			.errand(new ErrandRow(300, 3, "WADE", "IAN-2019-3", null, LocalDate.of(2019, 7, 1), null))
			.decision(new DecisionRow(1000, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "TillstT", "11111111.rtf", null))
			.decision(new DecisionRow(1001, 101, "GTDE", LocalDateTime.of(2023, 3, 1, 10, 0), null, null, null, "Tillståndsbevis 2023.pdf"))
			.decision(new DecisionRow(2000, 200, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "TILLTT", "22222222.rtf", null))
			.decision(new DecisionRow(2001, 201, "GTDE", LocalDateTime.of(2022, 1, 1, 10, 0), null, "MotTob", "33333333.rtf", "beslut.pdf"))
			.decision(new DecisionRow(3000, 300, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "MotTob", "44444444.rtf", "Mottagningsbevis.pdf"))
			.event(new EventRow(400, 1, 101, "016", LocalDateTime.of(2023, 2, 1, 9, 0), null, null, null, "Planritning.pdf"));

		final var candidates = data.factory().permits().candidates();

		final var scanned = candidates.get(0);
		assertThat(scanned.ruleHits()).isEmpty();
		assertThat(scanned.assetFiles()).containsExactly(
			new AssetFile(new FileReference("Tillståndsbevis 2023.pdf", ATTACHMENTS), "Tillståndsbevis"),
			new AssetFile(new FileReference("Planritning.pdf", ATTACHMENTS), "Ritning"));
		assertThat(scanned.files()).containsExactly(new FileReference("Planritning.pdf", ATTACHMENTS), new FileReference("Tillståndsbevis 2023.pdf", ATTACHMENTS));

		final var older = candidates.get(1);
		assertThat(older.ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.containsExactly(tuple(CERTIFICATE_OLDER, "beviset hör till beslutet 2019-07-01, det senaste beviljandet är 2022-01-01"));
		// The certificate of the earlier grant may be a former holder's, so it goes on the errand only, as does a receipt
		// letter, which is no permit decision
		assertThat(older.assetFiles()).containsExactly(new AssetFile(new FileReference("beslut.pdf", ATTACHMENTS), "Beslut"));
		assertThat(older.files()).endsWith(new FileReference("22222222.rtf", DOCUMENTS));
		assertThat(older.detailsOf(new FileReference("22222222.rtf", DOCUMENTS)))
			.as("A certificate of another errand than that of the latest grant gets its purpose and time too")
			.isEqualTo(new FileDetails(FilePurposes.ofDocument(ALKOL, "TILLTT"), LocalDateTime.of(2019, 7, 1, 10, 0)));

		assertThat(candidates.get(2).ruleHits()).extracting(RuleHit::rule).containsExactly(CERTIFICATE_MISSING);
		assertThat(candidates.get(2).assetFiles()).as("a receipt letter and a receipt are no permit decisions").isEmpty();
		assertThat(candidates.get(2).files()).containsExactly(new FileReference("44444444.rtf", DOCUMENTS), new FileReference("Mottagningsbevis.pdf", ATTACHMENTS));

		// The certificate, then the document of the latest grant, may state the legal basis; not the certificate that may be
		// a former holder's
		assertThat(scanned.asset().legalBasisDocuments()).containsExactly(new FileReference("Tillståndsbevis 2023.pdf", ATTACHMENTS));
		assertThat(older.asset().legalBasisDocuments()).containsExactly(new FileReference("33333333.rtf", DOCUMENTS));
		assertThat(candidates.get(2).asset().legalBasisDocuments()).containsExactly(new FileReference("44444444.rtf", DOCUMENTS));
	}

	@Test
	void theFilesOfEventsWithoutAnErrandGoToTheirNotificationOrElseToThePermit() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, false, false))
			.place(place(2, 10).withFlags(true, false, true, false))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-2019-1", null, LocalDate.of(2019, 7, 1), null))
			.decision(new DecisionRow(1000, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, "TillstT", "11111111.rtf", null))
			.event(new EventRow(300, 1, null, "014", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null, "folkol-1.pdf"))
			.event(new EventRow(301, 1, null, "016", LocalDateTime.of(2020, 1, 2, 9, 0), null, null, "22222222.rtf", "handling-1.pdf"))
			// E-cigarettes are not notified on place 1, so the event is one of the place's own
			.event(new EventRow(302, 1, null, "105", LocalDateTime.of(2020, 1, 3, 9, 0), null, null, null, "ecig-1.pdf"))
			.event(new EventRow(303, 2, null, "014", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null, "folkol-2.pdf"))
			.event(new EventRow(304, 2, null, "105", LocalDateTime.of(2020, 1, 2, 9, 0), null, null, null, "ecig-2.pdf"))
			.event(new EventRow(305, 2, null, "035", LocalDateTime.of(2020, 1, 3, 9, 0), null, null, null, "egentillsyn-2.pdf"))
			// A drawing and a letter among the place's other events follow its notifications' errands, not their posts
			.event(new EventRow(306, 2, null, "016", LocalDateTime.of(2020, 1, 4, 9, 0), null, null, null, "Planritning butik.pdf"))
			.event(new EventRow(307, 2, null, "023", LocalDateTime.of(2020, 1, 5, 9, 0), null, "BekEci", "77777777.rtf", null));

		final var permit = data.factory().permits().candidates().getFirst();
		final var notifications = data.factory().notifications().candidates();

		assertThat(permit.files()).containsExactly(
			new FileReference("11111111.rtf", DOCUMENTS),
			new FileReference("22222222.rtf", DOCUMENTS),
			new FileReference("handling-1.pdf", ATTACHMENTS),
			new FileReference("ecig-1.pdf", ATTACHMENTS));
		assertThat(permit.assetFiles()).extracting(AssetFile::category).containsExactly("Tillståndsbevis");
		assertThat(notifications).extracting(Candidate::key, Candidate::files).containsExactly(
			tuple(new ItemKey(ALKOL, ANMALAN, "1-FOLKOL"), List.of(new FileReference("folkol-1.pdf", ATTACHMENTS))),
			tuple(new ItemKey(ALKOL, ANMALAN, "2-FOLKOL"), List.of(new FileReference("folkol-2.pdf", ATTACHMENTS), new FileReference("egentillsyn-2.pdf", ATTACHMENTS),
				new FileReference("Planritning butik.pdf", ATTACHMENTS), new FileReference("77777777.rtf", DOCUMENTS))),
			tuple(new ItemKey(ALKOL, ANMALAN, "2-ECIGARETTER"), List.of(new FileReference("ecig-2.pdf", ATTACHMENTS), new FileReference("egentillsyn-2.pdf", ATTACHMENTS),
				new FileReference("Planritning butik.pdf", ATTACHMENTS), new FileReference("77777777.rtf", DOCUMENTS))));
		// The notifications get the attachments of the events that register their product on their post too
		assertThat(notifications).extracting(Candidate::key, Candidate::assetFiles).containsExactly(
			tuple(new ItemKey(ALKOL, ANMALAN, "1-FOLKOL"), List.of(new AssetFile(new FileReference("folkol-1.pdf", ATTACHMENTS), null))),
			tuple(new ItemKey(ALKOL, ANMALAN, "2-FOLKOL"), List.of(new AssetFile(new FileReference("folkol-2.pdf", ATTACHMENTS), null))),
			tuple(new ItemKey(ALKOL, ANMALAN, "2-ECIGARETTER"), List.of(new AssetFile(new FileReference("ecig-2.pdf", ATTACHMENTS), null))));
	}

	@Test
	void onlyTheNotificationItsConfirmationsAndDrawingsGoOnItsPost() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, false, false, false))
			.owner(owner(10, "5566778899"))
			.errand(new ErrandRow(100, 1, "SÖL", "IAN-2015-1", LocalDate.of(2015, 1, 1), LocalDate.of(2015, 2, 1), null))
			.decision(new DecisionRow(200, 100, "AÄDE", LocalDateTime.of(2015, 1, 20, 10, 0), null, "BekFol", "44444444.rtf", null))
			.event(new EventRow(300, 1, 100, "015", LocalDateTime.of(2015, 1, 2, 9, 0), "IAN-2015-1", null, null, "anmalan.pdf"))
			.event(new EventRow(301, 1, 100, "023", LocalDateTime.of(2015, 1, 3, 9, 0), null, "MotFol", "11111111.rtf", null))
			.event(new EventRow(302, 1, 100, "UTSKR", LocalDateTime.of(2015, 1, 4, 9, 0), null, "UtrTob", "22222222.rtf", null))
			.event(new EventRow(303, 1, 100, "029", LocalDateTime.of(2015, 1, 5, 9, 0), null, null, null, "remissvar polisen.pdf"))
			.event(new EventRow(304, 1, 100, "016", LocalDateTime.of(2015, 1, 6, 9, 0), null, null, null, "Planritning.pdf"))
			.event(new EventRow(305, 1, 100, "015", LocalDateTime.of(2015, 1, 7, 9, 0), null, null, null, "ritning bar.pdf"))
			.event(new EventRow(306, 1, null, "014", LocalDateTime.of(2016, 3, 4, 9, 0), null, "UtrTob", "33333333.rtf", "anmalan 2016.pdf"));

		final var notification = data.factory().notifications().candidates().getFirst();

		assertThat(notification.files()).hasSize(9);
		// An investigation, made from UtrTob, and a referral answer go on the errand only; a drawing keeps its category on
		// an event that registers the product
		assertThat(notification.assetFiles()).containsExactly(
			new AssetFile(new FileReference("Planritning.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("ritning bar.pdf", ATTACHMENTS), "Ritning"),
			new AssetFile(new FileReference("anmalan.pdf", ATTACHMENTS), null),
			new AssetFile(new FileReference("anmalan 2016.pdf", ATTACHMENTS), null),
			new AssetFile(new FileReference("11111111.rtf", DOCUMENTS), null),
			new AssetFile(new FileReference("44444444.rtf", DOCUMENTS), null));
		// Each file gets its purpose and the time of its event or decision, and a referral answer is material of the
		// investigation
		assertThat(notification.detailsOf(new FileReference("remissvar polisen.pdf", ATTACHMENTS)))
			.isEqualTo(new FileDetails(FilePurposes.POLICE_RESPONSE, LocalDateTime.of(2015, 1, 5, 9, 0), true));
		assertThat(notification.detailsOf(new FileReference("Planritning.pdf", ATTACHMENTS)))
			.isEqualTo(new FileDetails(FilePurposes.SALES_PREMISES_DRAWING, LocalDateTime.of(2015, 1, 6, 9, 0)));
		assertThat(notification.detailsOf(new FileReference("44444444.rtf", DOCUMENTS))).isEqualTo(new FileDetails(null, LocalDateTime.of(2015, 1, 20, 10, 0)));
		// A notification is made under no legal basis of a permit
		assertThat(notification.asset().legalBasisDocuments()).isEmpty();
	}

	@Test
	void aFileNameThatAnotherPlaceAlsoRefersToIsMarked() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, false, true))
			.owner(owner(10, "556677-8899"))
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-2019-100", LocalDate.of(2019, 6, 1), LocalDate.of(2019, 7, 1), null))
			.errand(new ErrandRow(101, 1, "WAAÄ", "IAN-2021-1", LocalDate.of(2021, 1, 1), LocalDate.of(2021, 2, 1), null))
			.decision(new DecisionRow(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 7, 2), null, null, "bilaga.pdf"))
			.decision(new DecisionRow(201, null, "GTDE", null, null, null, null, "Bilaga.pdf"))
			.decision(new DecisionRow(202, 100, "BESOVR", LocalDateTime.of(2019, 7, 3, 10, 0), null, null, null, "protokoll.pdf"))
			.event(new EventRow(300, 1, 100, "001", LocalDateTime.of(2019, 6, 1, 9, 0), null, null, "22222222.rtf", "ansokan.pdf"))
			.event(new EventRow(301, null, 100, "001", LocalDateTime.of(2019, 6, 2, 9, 0), null, null, null, "intyg.pdf"))
			.event(new EventRow(302, 1, 101, "001", LocalDateTime.of(2021, 1, 2, 9, 0), null, null, null, "intyg.pdf"))
			// Registered on another place than its errand's, whose files it goes to
			.event(new EventRow(303, 2, 100, "001", LocalDateTime.of(2019, 7, 3, 9, 0), null, null, null, "protokoll.pdf"))
			.event(new EventRow(400, 2, null, "001", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null, "ANSOKAN.PDF"));

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
			.decision(new DecisionRow(200, 100, "GTPH", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null))
			.decision(new DecisionRow(201, 100, "GTDEÅ", LocalDateTime.of(2022, 1, 1, 10, 0), null, null, null, null))
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
			.view(new PermitViewRow(1, true, false, false))
			.view(new PermitViewRow(2, true, false, false));

		final var candidates = data.factory().permits().candidates();

		assertThat(candidates.get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail).containsExactlyInAnyOrder(
			tuple(OWNER_MISSING, "AgarID 99 finns inte"),
			tuple(DIARY_NUMBER_MISSING, "inget beviljande beslut hittades"),
			tuple(CERTIFICATE_MISSING, null));
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::rule).containsExactlyInAnyOrder(PLACE_MISSING, DIARY_NUMBER_MISSING, CERTIFICATE_MISSING);
		assertThat(candidates.get(1).title()).isEqualTo("Tobakstillstånd – okänt försäljningsställe");
		// A permit without a grant gets an investigation all the same, without dates and texts
		assertThat(candidates.get(1).investigation()).isEqualTo(new InvestigationData("Tobakstillstånd – okänt försäljningsställe", null, null));
	}

	@Test
	void theTobaccoPermitGetsAnInvestigationAndTheEventsOfItsPlaceAsALog() {
		final var data = new Data()
			.place(place(1, 10).withFlags(true, true, false, false))
			.owner(owner(10, "5566778899"))
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-2019-1", LocalDate.of(2019, 5, 2), LocalDate.of(2019, 7, 3), null, "Komplett ansökan", List.of("2", "9")))
			// The errand of another place has the note its place shows as a parameter already
			.place(place(2, 10))
			.view(new PermitViewRow(2, true, false, false))
			.errand(new ErrandRow(101, 2, "WADE", "IAN-2019-2", LocalDate.of(2019, 5, 2), LocalDate.of(2019, 7, 3), null, "Notering 2"))
			.decision(new DecisionRow(1001, 101, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null))
			// The permit is valid from a later day, but the trial was completed when the grant was decided
			.decision(new DecisionRow(1000, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 8, 1), null, null, null, "Tillstånd beviljas.", "ka"))
			.event(new EventRow(299, 1, 100, "024", LocalDateTime.of(2019, 6, 1, 9, 0), null, null, null, null, "Platsbesök gjort"))
			.event(new EventRow(298, 1, 100, "017", LocalDateTime.of(2019, 5, 10, 9, 0), null, null, null, null))
			// The event registers the folköl the place has notified, so it belongs to the notification
			.event(new EventRow(300, 1, null, "014", LocalDateTime.of(2020, 1, 1, 9, 0), null, null, null, null, "Anmälan folköl"))
			.event(new EventRow(301, 1, null, "024", LocalDateTime.of(2021, 1, 2, 9, 0), "IAN-2021-1", null, null, null, "Samtal med butiken", true))
			.event(new EventRow(302, 1, null, "061", LocalDateTime.of(2020, 3, 1, 9, 0), null, null, null, null, "Besök enligt plan"))
			.event(new EventRow(303, 1, null, "024", LocalDateTime.of(2020, 4, 1, 9, 0), null, null, null, null))
			// The errand of the event is missing, so the event counts as one of the place
			.event(new EventRow(304, 1, 999, "024", LocalDateTime.of(2020, 6, 1, 9, 0), null, null, null, "borttaget.pdf", "Ärendet är borttaget"))
			// Another event of the place refers to the same file, which is therefore not shared with another place
			.event(new EventRow(305, 1, null, "024", LocalDateTime.of(2020, 7, 1, 9, 0), null, null, null, "borttaget.pdf", null));

		final var permit = data.factory().permits().candidates().getFirst();

		// Started when the errand of the grant was opened, completed when the permit was granted; every event of the errand is
		// in the journal, also one without a text, and the decision tells who made it
		assertThat(permit.investigation()).isEqualTo(new InvestigationData("Tobakstillstånd – Butik 1", LocalDateTime.of(2019, 5, 2, 0, 0), LocalDateTime.of(2019, 7, 1, 0, 0),
			InvestigationTexts.of(new Part("Noteringar", "Komplett ansökan"), new Part("Händelser", "2019-05-10 017\n2019-06-01 Anteckningar: Platsbesök gjort"),
				new Part("Beslut", "2019-07-01 Tillstånd tobaksförsäljning – detaljhandel, beslutsfattare Karin Andersson: Tillstånd beviljas."))));
		// The log of the place takes only events with a text, and marks a confidential one
		assertThat(parameters(permit)).containsEntry("handelselogg",
			List.of("2020-03-01 Tillsyn - Tillsynsbesök enl plan: Besök enligt plan", "2020-06-01 Anteckningar: Ärendet är borttaget",
				"2021-01-02 Anteckningar (sekretess): Samtal med butiken"));
		assertThat(permit.files()).contains(new FileReference("borttaget.pdf", ATTACHMENTS));
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
			.view(new PermitViewRow(1, true, false, false))
			.view(new PermitViewRow(2, true, false, false))
			.view(new PermitViewRow(3, true, false, false))
			.view(new PermitViewRow(4, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-1", null, LocalDate.of(2019, 7, 1), null))
			.errand(new ErrandRow(101, 2, "WADE", "ian 1", null, LocalDate.of(2019, 7, 1), null))
			.decision(new DecisionRow(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null))
			.decision(new DecisionRow(201, 101, "GTDE", null, null, null, null, null));

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
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-1", null, LocalDate.of(2019, 7, 1), 50))
			.decision(new DecisionRow(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), LocalDate.of(2019, 7, 2), "TILLSTT", "bevis.rtf", null))
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
			.event(new EventRow(300, 1, 101, "015", LocalDateTime.of(2015, 1, 2, 9, 0), "IAN-2015-1", null, "33333333.rtf", null))
			.event(new EventRow(301, 2, null, "014", LocalDateTime.of(2012, 3, 4, 9, 0), "IAN-2012-1", null, null, null))
			.event(new EventRow(302, 3, null, "999", LocalDateTime.of(2009, 3, 4, 9, 0), null, null, null, null));

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
			.event(new EventRow(300, 1, null, "047", LocalDateTime.of(2022, 3, 4, 9, 0), null, null, null, null));

		final var result = data.factory().notifications();

		assertThat(result.candidates()).extracting(Candidate::key, Candidate::type).containsExactly(
			tuple(new ItemKey(ALKOL, ANMALAN, "1-ECIGARETTER"), ErrandType.ECIGARETTE_NOTIFICATION),
			tuple(new ItemKey(ALKOL, ANMALAN, "1-TOBAKSFRI_NIKOTIN"), ErrandType.TOBACCO_FREE_NICOTINE_NOTIFICATION));
		assertThat(result.candidates().get(0).ruleHits()).extracting(RuleHit::rule, RuleHit::detail)
			.containsExactly(tuple(NOTIFICATION_DATE_MISSING, "E-cigaretter och påfyllningsbehållare, anmälan saknar datum"), tuple(UNDERLYING_ERRAND_OPEN, "ArendeID 100, diarienummer IAN-1"));
		assertThat(result.candidates().get(0).labelPaths()).containsExactly("CATEGORYROOT/TOBACCO/ECIGARETTE_SALES_NOTIFICATION");
		assertThat(result.candidates().get(1).labelPaths()).containsExactly("CATEGORYROOT/TOBACCO/TOBACCO_FREE_NICOTINE_SALES_NOTIFICATION");
		assertThat(result.candidates().get(1).title()).isEqualTo("Anmälan om försäljning av tobaksfria nikotinprodukter – Butik 1");
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
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(100, 1, "WADE", "IAN-2019-100", LocalDate.of(2019, 6, 1), null, null))
			.errand(new ErrandRow(101, 1, "Fföl", "IAN-2010-1", LocalDate.of(2010, 1, 1), LocalDate.of(2010, 2, 1), null))
			.errand(new ErrandRow(102, 1, "SÖL", null, LocalDate.of(2015, 1, 1), null, null))
			.decision(new DecisionRow(200, 100, "GTDE", LocalDateTime.of(2019, 7, 1, 10, 0), null, null, null, null));

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
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(400, 1, "Tills", "TIL-1", LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), 50, "Klagomål", List.of("2")))
			.errand(new ErrandRow(401, 1, "IT", null, LocalDate.of(2020, 1, 1), null, null))
			.errand(new ErrandRow(402, 2, "IT", null, LocalDate.of(2020, 1, 1), null, null))
			.decision(new DecisionRow(500, 400, "VT", LocalDateTime.of(2020, 1, 20, 9, 0), null, null, "varning.pdf", null, "Varning meddelas.", "XY"))
			.event(new EventRow(600, 1, 400, "024", LocalDateTime.of(2020, 1, 10, 9, 0), "til-1", null, null, null, "Kontrollköp gjort", true))
			.event(new EventRow(601, 1, 400, "017", LocalDateTime.of(2020, 1, 15, 9, 0), "TIL-0", null, null, null))
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
		assertThat(external.permitLink().basis()).isEqualTo(Basis.SAME_HOLDER);
		assertThat(parameters(external)).containsEntry("beslut", List.of("Varning, 2020-01-20")).containsEntry("handlaggare", List.of("Karin Andersson"));
		assertThat(external.description()).contains("\nHandläggare: Karin Andersson\n");
		assertThat(external.files()).containsExactly(new FileReference("varning.pdf", DOCUMENTS));
		// A decision maker AlkOL has no user for stands with the code
		assertThat(external.investigation().texts()).isEqualTo(InvestigationTexts.of(new Part("Noteringar", "Klagomål"),
			new Part("Händelser", "2020-01-10 Anteckningar (sekretess): Kontrollköp gjort\n2020-01-15 017 (diarienummer TIL-0)"),
			new Part("Beslut", "2020-01-20 Varning, beslutsfattare XY: Varning meddelas.")));
		final var internal = candidates.get(1);
		assertThat(internal.investigation().texts()).isEqualTo(InvestigationTexts.NONE);
		assertThat(parameters(internal)).doesNotContainKey("handlaggare");
		assertThat(internal.ruleHits()).extracting(RuleHit::rule).containsExactly(ERRAND_OPEN);
		assertThat(internal.labelPaths()).containsExactly("TAGROOT/TOBACCO_INSPECTION", "CATEGORYROOT/INSPECTION/INTERNAL_INSPECTION");
		assertThat(internal.asset()).isNull();
		assertThat(internal.holder()).isNull();
	}

	@Test
	void theFilesOfAnInspectionReportAreMaterialOfTheInvestigation() {
		final var data = new Data()
			.place(place(1, 10))
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(400, 1, "Tills", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null))
			.event(new EventRow(600, 1, 400, "063", LocalDateTime.of(2020, 1, 10, 9, 0), null, null, null, "rapport.pdf"))
			.event(new EventRow(601, 1, 400, "024", LocalDateTime.of(2020, 1, 11, 9, 0), null, null, null, "anteckning.pdf"));

		final var errand = data.factory().inspections().candidates().getFirst();

		// The type of the event tells, whatever the file is called (decision 60)
		assertThat(errand.detailsOf(new FileReference("rapport.pdf", ATTACHMENTS)).investigation()).isTrue();
		assertThat(errand.detailsOf(new FileReference("anteckning.pdf", ATTACHMENTS)).investigation()).isFalse();
	}

	@Test
	void anInspectionErrandStillOpenEndsAtItsLatestEventOrDecision() {
		final var data = new Data()
			.place(place(1, 10))
			.view(new PermitViewRow(1, true, false, false))
			.errand(new ErrandRow(401, 1, "IT", null, LocalDate.of(2020, 1, 1), null, null))
			.decision(new DecisionRow(500, 401, "VT", LocalDateTime.of(2020, 3, 1, 9, 0), null, null, null, null))
			.event(new EventRow(600, 1, 401, "024", LocalDateTime.of(2020, 3, 5, 10, 0), null, null, null, null))
			.event(new EventRow(601, 1, 401, "024", null, null, null, null, null));

		final var investigation = data.factory().inspections().candidates().getFirst().investigation();

		assertThat(investigation.startedAt()).isEqualTo(LocalDateTime.of(2020, 1, 1, 0, 0));
		assertThat(investigation.completedAt()).isEqualTo(LocalDateTime.of(2020, 3, 5, 10, 0));
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

	@Test
	void theInspectionErrandsOfACeasedSalesPlaceAreNotMigrated() {
		final var data = new Data()
			.place(place(1, 10))
			.place(place(3, 10).ceased())
			.view(new PermitViewRow(1, true, false, false))
			.view(new PermitViewRow(3, true, false, false))
			.errand(new ErrandRow(400, 1, "Tills", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null))
			.errand(new ErrandRow(404, 3, "Tills", null, LocalDate.of(2020, 1, 1), LocalDate.of(2020, 2, 1), null));

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
			.view(new PermitViewRow(1, true, false, false))
			.view(new PermitViewRow(3, true, false, false))
			.visit(new VisitRow(900, 1, LocalDate.of(2024, 5, 3), null, "21:30", "22:15", "Kontr", "Köpet nekades.", "55555555.rtf", "protokoll.pdf", List.of("4"), null, 7,
				List.of("Tobak", "E-cigaretter"), true))
			.round(new RoundRow(7, "Kontrollköp vecka 18", LocalDate.of(2024, 5, 3), LocalDate.of(2024, 5, 3), null))
			.visit(new VisitRow(901, 2, LocalDate.of(2024, 5, 3), null, null, "Kontr", null, null, null))
			.visit(new VisitRow(902, 3, LocalDate.of(2024, 5, 3), null, null, "Kontr", null, null, null))
			.visit(new VisitRow(903, 1, TODAY.plusDays(1), null, null, null, null, null, null))
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
		assertThat(visit.title()).isEqualTo("Tillsynsbesök – Butik 1");
		assertThat(visit.description()).isEqualTo("""
			Tillsynsbesök på Butik 1 2024-05-03, migrerat från AlkOL.
			Tillsynsart: Kontrollköp
			Tid: 21:30–22:15
			Tillsynsomgång: Kontrollköp vecka 18, 2024-05-03
			Tillsynen gällde: Tobak, E-cigaretter
			Anmält besök: Ja
			Handläggare: Per Eriksson
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
			.containsEntry("handlaggare", List.of("Per Eriksson"))
			.doesNotContainKey("serveringspersonal")
			.containsEntry("anmarkningar", List.of("Påträngande reklam i butik"))
			.containsEntry("anteckning", List.of("Köpet nekades."))
			.containsEntry("migrerad", List.of(TODAY + " från AlkOL"));
		assertThat(visit.investigation().texts()).isEqualTo(InvestigationTexts.of(new Part("Tillsynsomgång", "Kontrollköp vecka 18, 2024-05-03"), new Part("Anteckning", "Köpet nekades."),
			new Part("Anmärkningar", "Påträngande reklam i butik")));
		assertThat(visit.measures()).containsExactly(new MeasureData("Påträngande reklam i butik", null, LocalDateTime.of(2024, 5, 3, 22, 15)));
		// Every file of the visit is material of its investigation (decision 60)
		assertThat(visit.files()).allSatisfy(file -> assertThat(visit.detailsOf(file).investigation()).isTrue());
		assertThat(candidates.get(1).measures()).isEmpty();
		assertThat(parameters(candidates.get(1))).doesNotContainKeys("tillsynsomgang", "tillsynenGallde", "anmaltBesok");
		assertThat(visit.place().name()).isEqualTo("Butik 1");
		assertThat(visit.files()).containsExactly(new FileReference("55555555.rtf", DOCUMENTS), new FileReference("protokoll.pdf", ATTACHMENTS), new FileReference("foto.jpg", ATTACHMENTS));
		assertThat(candidates.get(1).ruleHits()).extracting(RuleHit::toReportText)
			.containsExactly("G28 Tillsynsbesöket saknar datum eller har ett datum efter migreringsdagen och kan vara planerat men inte genomfört: " + TODAY.plusDays(1));

		final var allPlaces = TestData.properties(ErrandType.TOBACCO_INSPECTION_VISIT, false, Map.of());
		assertThat(new AlkolCandidateFactory(data.build(), TODAY, allPlaces).visits().candidates()).extracting(candidate -> candidate.key().sourceId())
			.as("A ceased sales place gets no visits, whatever the settings say")
			.containsExactly("900", "901", "903");
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
				Map.of("GTDE", "Tillstånd tobaksförsäljning – detaljhandel", "AÄDE", "Godkänd anmälan ändring i bolag", "VT", "Varning"),
				Map.of("024", "Anteckningar", "061", "Tillsyn - Tillsynsbesök enl plan"),
				Map.of("Tills", "Tillsynsärende", "IT", "Inre tillsyn"),
				Map.of("LBK", "Livsmedelsbutik, butikskedja", "SERV", "Serveringsställe grill/pizza"),
				Map.of("12", "City"),
				Map.of("VD", "Verkställande direktör"),
				new InspectionVisitRows(visits, remarks, visitFiles, Map.of("Kontr", "Kontrollköp"), Map.of("Rekl I", "Påträngande reklam i butik"),
					// The handlers by the numbers of the users (AnvID), the codes the visits and errands name them with
					Map.of("4", "Per Eriksson", "2", "Karin Andersson"), rounds),
				// The decision makers by the codes of the users (AnvändarKod)
				Map.of("KA", "Karin Andersson", "2", "Fel användare"));
		}

		AlkolCandidateFactory factory() {
			return new AlkolCandidateFactory(build(), TODAY, PROPERTIES);
		}
	}
}
