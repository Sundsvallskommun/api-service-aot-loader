package se.sundsvall.aotloader.source;

import java.text.Normalizer;
import java.time.LocalDateTime;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.aotloader.service.model.FileDetails;
import se.sundsvall.aotloader.service.model.Source;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.ATTACHMENTS;
import static se.sundsvall.aotloader.service.model.FileReference.FileFolder.DOCUMENTS;
import static se.sundsvall.aotloader.service.model.Source.ALKOL;
import static se.sundsvall.aotloader.service.model.Source.ALKT;

class FilePurposesTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		ALKT  | REMP    | REFERRAL_POLICE_REQUEST
		ALKT  | rembl   | REFERRAL_POLICE_REQUEST
		ALKT  | REMK    | REFERRAL_ENFORCEMENT_AUTHORITY_REQUEST
		ALKT  | REMR    | REFERRAL_RESCUE_SERVICE_REQUEST
		ALKT  | REMM    | REFERRAL_ENVIRONMENT_OFFICE_REQUEST
		ALKT  | SKVNF   | REFERRAL_TAX_AGENCY_REQUEST
		ALKT  | SKVIN   | REFERRAL_TAX_AGENCY_REQUEST
		ALKT  | REMA    | STATEMENT_REQUEST
		ALKT  | TDB     | -
		ALKT  | BeBel   | -
		ALKT  | -       | -
		ALKOL | BeBel   | REFERRAL_POLICE_REQUEST
		ALKOL | RemSkv  | REFERRAL_TAX_AGENCY_REQUEST
		ALKOL | RemSkvB | REFERRAL_TAX_AGENCY_REQUEST
		ALKOL | RemKro  | REFERRAL_ENFORCEMENT_AUTHORITY_REQUEST
		ALKOL | REMP    | -
		ALKOL | BekFol  | -
		""")
	void aDocumentIsAReferralByItsTemplate(final Source source, final String template, final String purpose) {
		assertThat(FilePurposes.ofDocument(source, template)).isEqualTo(purpose);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		ALKT  | REMS  | Remissvar Polisen.pdf          | REFERRAL_POLICE_RESPONSE
		ALKT  | rems  | yttrande skatteverket.pdf      | REFERRAL_TAX_AGENCY_RESPONSE
		ALKT  | REMS  | Svar KFM.pdf                   | REFERRAL_ENFORCEMENT_AUTHORITY_RESPONSE
		ALKT  | REMS  | svar kronofogden.pdf           | REFERRAL_ENFORCEMENT_AUTHORITY_RESPONSE
		ALKT  | REMS  | Räddningstjänsten ritning.pdf  | REFERRAL_RESCUE_SERVICE_RESPONSE
		ALKT  | REMS  | miljokontoret.pdf              | REFERRAL_ENVIRONMENT_OFFICE_RESPONSE
		ALKT  | REMS  | Syna 2020.pdf                  | REFERRAL_CREDIT_REPORT_RESPONSE
		ALKT  | REMS  | synar.pdf                      | STATEMENT_RESPONSE
		ALKT  | REMS  | svar.pdf                       | STATEMENT_RESPONSE
		ALKOL | 029   | svar polisen.pdf               | REFERRAL_POLICE_RESPONSE
		ALKOL | 035   | egentillsyn.pdf                | SELF_MONITORING_PROGRAM
		ALKOL | 037   | ritning.pdf                    | SELF_MONITORING_PROGRAM
		ALKOL | 038   | program.pdf                    | SELF_MONITORING_PROGRAM
		ALKT  | 035   | program.pdf                    | -
		ALKOL | REMS  | svar.pdf                       | -
		ALKT  | HAND  | Planritning.pdf                | FLOOR_PLAN
		ALKT  | -     | Köksritning.pdf                | KITCHEN_FLOOR_PLAN
		ALKT  | -     | ritning kok.pdf                | KITCHEN_FLOOR_PLAN
		ALKT  | -     | ritning kokbok.pdf             | FLOOR_PLAN
		ALKT  | -     | ritning skok.pdf               | FLOOR_PLAN
		ALKT  | -     | ritning kök.pdf                | KITCHEN_FLOOR_PLAN
		ALKOL | 016   | ritning butik.pdf              | SALES_PREMISES_DRAWING
		ALKOL | 016   | ansokan.pdf                    | -
		ALKOL | -     | -                              | -
		""")
	void anAttachmentIsAReferralAnswerOrASelfMonitoringProgramByItsEventAndADrawingByItsName(final Source source, final String eventType, final String name,
		final String purpose) {
		assertThat(FilePurposes.ofAttachment(source, eventType, name)).isEqualTo(purpose);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		ALKT  | HAND  | Dryckesmeny 2019.pdf                  | DRINKS_MENU
		ALKT  | HAND  | Mat- och dryckeslista.pdf             | DRINKS_MENU
		ALKT  | HAND  | Smörgåsmeny.pdf                       | MENU
		ALKT  | IKOMP | Brandskyddsdokumentation.pdf          | FIRE_SAFETY_DOCUMENTATION
		ALKT  | HAND  | Egenkontrollprogram.pdf               | SELF_MONITORING_PROGRAM
		ALKOL | 016   | egentillsyn.pdf                       | SELF_MONITORING_PROGRAM
		ALKOL | 018   | Fullmakt.pdf                          | POWER_OF_ATTORNEY
		ALKT  | BILAG | Registreringsbevis.pdf                | CERTIFICATE_OF_REGISTRATION
		ALKT  | BILAG | Aktiebok.pdf                          | OWNERSHIP_INFORMATION
		ALKT  | BILAG | uppgift om agarforhallanden.pdf       | OWNERSHIP_INFORMATION
		ALKT  | BILAG | Kontoutdrag.pdf                       | BANK_STATEMENT
		ALKT  | BILAG | Resultatbudget.xlsx                   | BUDGET
		ALKT  | BILAG | Verksamhetsbeskrivning.docx           | BUSINESS_DESCRIPTION
		ALKT  | BILAG | Köpeavtal.pdf                         | PURCHASE_AGREEMENT
		ALKT  | BILAG | kopavtal.pdf                          | PURCHASE_AGREEMENT
		ALKT  | BILAG | Överlåtelseavtal.pdf                  | TRANSFER_AGREEMENT
		ALKT  | BILAG | Hyresavtal.pdf                        | LEASE_OR_OWNERSHIP_PROOF
		ALKOL | 016   | hyreskontrakt lokal.pdf               | LEASE_OR_OWNERSHIP_PROOF
		ALKT  | BILAG | Samarbetsavtal.pdf                    | BUSINESS_AGREEMENT
		ALKT  | HAND  | Polistillstånd.pdf                    | POLICE_PERMIT
		ALKT  | -     | Hyresavtal.pdf                        | LEASE_OR_OWNERSHIP_PROOF
		ALKT  | ANST  | Ansökan alkoholdrycker - 123456.pdf   | -
		ALKT  | HAND  | avtal.pdf                             | -
		ALKT  | HAND  | Registerutdrag.pdf                    | -
		ALKT  | HAND  | Lånebevis.pdf                         | -
		ALKT  | REMS  | Räddningstjänsten brandskydd.pdf      | REFERRAL_RESCUE_SERVICE_RESPONSE
		ALKT  | HAND  | Planritning med brandskydd.pdf        | FLOOR_PLAN
		ALKOL | 035   | budget.pdf                            | SELF_MONITORING_PROGRAM
		""")
	void anotherAttachmentGetsAPurposeByAWordOfItsNameAfterTheEventAndTheDrawing(final Source source, final String eventType, final String name, final String purpose) {
		assertThat(FilePurposes.ofAttachment(source, eventType, name)).isEqualTo(purpose);
	}

	@Test
	void aNameWithDecomposedLettersGetsThePurposeOfTheComposedName() {
		final var kitchen = Normalizer.normalize("Köksritning.pdf", Normalizer.Form.NFD);
		final var rescueService = Normalizer.normalize("Räddningstjänsten svar.pdf", Normalizer.Form.NFD);
		final var noEnforcementAuthority = Normalizer.normalize("svar åkfm.pdf", Normalizer.Form.NFD);
		final var transfer = Normalizer.normalize("Överlåtelseavtal.pdf", Normalizer.Form.NFD);

		assertThat(kitchen).isNotEqualTo("Köksritning.pdf");
		assertThat(FilePurposes.ofAttachment(ALKT, null, kitchen)).isEqualTo(FilePurposes.KITCHEN_FLOOR_PLAN);
		assertThat(FilePurposes.ofAttachment(ALKT, "REMS", rescueService)).isEqualTo(FilePurposes.RESCUE_SERVICE_RESPONSE);
		// The letter before kfm still keeps it from being the abbreviation
		assertThat(FilePurposes.ofAttachment(ALKT, "REMS", noEnforcementAuthority)).isEqualTo(FilePurposes.STATEMENT_RESPONSE);
		assertThat(FilePurposes.ofAttachment(ALKT, "HAND", transfer)).isEqualTo(FilePurposes.TRANSFER_AGREEMENT);
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		ALKT  | tdu     | -     | -                       | true
		ALKT  | TNT     | ANT   | -                       | true
		ALKT  | KOUT    | -     | -                       | true
		ALKT  | REMP    | UTSKR | REFERRAL_POLICE_REQUEST | true
		ALKT  | TDBV2   | UTSKR | -                       | false
		ALKT  | -       | KOMU  | -                       | true
		ALKT  | -       | NAMF  | -                       | true
		ALKT  | -       | ANT   | -                       | false
		ALKOL | UtrÄndT | -     | -                       | true
		ALKOL | TOBrapp | -     | -                       | true
		ALKOL | TillstT | -     | -                       | false
		ALKOL | -       | 092   | -                       | true
		ALKOL | -       | <Kod> | -                       | true
		ALKOL | -       | 063   | -                       | true
		ALKOL | -       | 024   | -                       | false
		""")
	void aDocumentIsMaterialOfTheInvestigationByItsTemplateOrItsEvent(final Source source, final String template, final String eventType, final String purpose,
		final boolean investigation) {
		final var received = LocalDateTime.of(2020, 1, 10, 9, 0);

		assertThat(FilePurposes.ofGenerated(source, template, eventType, received)).isEqualTo(new FileDetails(purpose, received, investigation));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		ALKT  | REMS | svar polisen.pdf | REFERRAL_POLICE_RESPONSE | true
		ALKT  | REMS | svar.pdf         | STATEMENT_RESPONSE       | true
		ALKT  | KOMS | svar.pdf         | -                        | true
		ALKT  | HAND | ritning.pdf      | FLOOR_PLAN     | false
		ALKOL | 029  | svar.pdf         | STATEMENT_RESPONSE       | true
		ALKOL | 094  | förslag.pdf      | -                        | true
		ALKOL | 035  | program.pdf      | SELF_MONITORING_PROGRAM  | false
		ALKOL | -    | ansokan.pdf      | -                        | false
		""")
	void anAttachmentIsMaterialOfTheInvestigationByItsPurposeOrItsEvent(final Source source, final String eventType, final String name, final String purpose,
		final boolean investigation) {
		assertThat(FilePurposes.ofAttached(source, eventType, name, null)).isEqualTo(new FileDetails(purpose, null, investigation));
	}

	@Test
	void everyFileOfAVisitIsMaterialOfItsInvestigation() {
		final var visited = LocalDateTime.of(2023, 11, 4, 0, 0);

		assertThat(FilePurposes.ofVisit(ALKT, ATTACHMENTS, "ritning.pdf", visited)).isEqualTo(new FileDetails(FilePurposes.FLOOR_PLAN, visited, true));
		assertThat(FilePurposes.ofVisit(ALKOL, DOCUMENTS, "protokoll.rtf", null)).isEqualTo(new FileDetails(null, null, true));
	}

	@Test
	void theDetailsOfAFileOnNoEvent() {
		final var received = LocalDateTime.of(2019, 6, 1, 0, 0);

		assertThat(FilePurposes.detailsOf(ALKT, ATTACHMENTS, "ritning.pdf", received)).isEqualTo(new FileDetails(FilePurposes.FLOOR_PLAN, received));
		assertThat(FilePurposes.detailsOf(ALKOL, ATTACHMENTS, "Fullmakt.pdf", received)).isEqualTo(new FileDetails(FilePurposes.POWER_OF_ATTORNEY, received));
		assertThat(FilePurposes.detailsOf(ALKOL, DOCUMENTS, "ritning.rtf", null)).isEqualTo(FileDetails.NONE);
		// A document the source made is known by its template, never by its name
		assertThat(FilePurposes.detailsOf(ALKT, DOCUMENTS, "budget.rtf", null)).isEqualTo(FileDetails.NONE);
	}

	@Test
	void drawingsAndTheDetailsOfAFileAddedTwice() {
		assertThat(FilePurposes.ALL).allSatisfy(purpose -> assertThat(FilePurposes.isDrawing(purpose))
			.isEqualTo(purpose.endsWith("_DRAWING") || purpose.endsWith("FLOOR_PLAN")));
		assertThat(FilePurposes.isDrawing(null)).isFalse();
		final var first = new FileDetails(FilePurposes.STATEMENT_RESPONSE, null);
		assertThat(first.orElse(new FileDetails(FilePurposes.POLICE_RESPONSE, LocalDateTime.of(2019, 6, 1, 0, 0))))
			.isEqualTo(new FileDetails(FilePurposes.STATEMENT_RESPONSE, LocalDateTime.of(2019, 6, 1, 0, 0)));
		assertThat(first.orElse(null)).isSameAs(first);
		// A file is material of the investigation when it is so on either of its events
		assertThat(first.orElse(new FileDetails(null, null, true)).investigation()).isTrue();
		assertThat(new FileDetails(null, null, true).orElse(first).investigation()).isTrue();
	}
}
