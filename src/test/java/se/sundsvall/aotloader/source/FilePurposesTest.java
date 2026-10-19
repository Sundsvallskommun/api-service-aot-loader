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
		ALKT  | HAND  | Planritning.pdf                | SERVING_AREA_DRAWING
		ALKT  | -     | Köksritning.pdf                | KITCHEN_FLOOR_PLAN
		ALKT  | -     | ritning kok.pdf                | KITCHEN_FLOOR_PLAN
		ALKT  | -     | ritning kokbok.pdf             | SERVING_AREA_DRAWING
		ALKT  | -     | ritning skok.pdf               | SERVING_AREA_DRAWING
		ALKT  | -     | ritning kök.pdf                | KITCHEN_FLOOR_PLAN
		ALKOL | 016   | ritning butik.pdf              | SALES_PREMISES_DRAWING
		ALKOL | 016   | ansokan.pdf                    | -
		ALKOL | -     | -                              | -
		""")
	void anAttachmentIsAReferralAnswerOrASelfMonitoringProgramByItsEventAndADrawingByItsName(final Source source, final String eventType, final String name,
		final String purpose) {
		assertThat(FilePurposes.ofAttachment(source, eventType, name)).isEqualTo(purpose);
	}

	@Test
	void aNameWithDecomposedLettersGetsThePurposeOfTheComposedName() {
		final var kitchen = Normalizer.normalize("Köksritning.pdf", Normalizer.Form.NFD);
		final var rescueService = Normalizer.normalize("Räddningstjänsten svar.pdf", Normalizer.Form.NFD);
		final var noEnforcementAuthority = Normalizer.normalize("svar åkfm.pdf", Normalizer.Form.NFD);

		assertThat(kitchen).isNotEqualTo("Köksritning.pdf");
		assertThat(FilePurposes.ofAttachment(ALKT, null, kitchen)).isEqualTo(FilePurposes.KITCHEN_FLOOR_PLAN);
		assertThat(FilePurposes.ofAttachment(ALKT, "REMS", rescueService)).isEqualTo(FilePurposes.RESCUE_SERVICE_RESPONSE);
		// The letter before kfm still keeps it from being the abbreviation
		assertThat(FilePurposes.ofAttachment(ALKT, "REMS", noEnforcementAuthority)).isEqualTo(FilePurposes.STATEMENT_RESPONSE);
	}

	@Test
	void theDetailsOfAFileOnNoEvent() {
		final var received = LocalDateTime.of(2019, 6, 1, 0, 0);

		assertThat(FilePurposes.detailsOf(ALKT, ATTACHMENTS, "ritning.pdf", received)).isEqualTo(new FileDetails(FilePurposes.SERVING_AREA_DRAWING, received));
		assertThat(FilePurposes.detailsOf(ALKOL, DOCUMENTS, "ritning.rtf", null)).isEqualTo(FileDetails.NONE);
	}

	@Test
	void drawingsAndTheDetailsOfAFileAddedTwice() {
		assertThat(FilePurposes.ALL).allSatisfy(purpose -> assertThat(FilePurposes.isDrawing(purpose))
			.isEqualTo(purpose.endsWith("_DRAWING") || FilePurposes.KITCHEN_FLOOR_PLAN.equals(purpose)));
		assertThat(FilePurposes.isDrawing(null)).isFalse();
		final var first = new FileDetails(FilePurposes.STATEMENT_RESPONSE, null);
		assertThat(first.orElse(new FileDetails(FilePurposes.POLICE_RESPONSE, LocalDateTime.of(2019, 6, 1, 0, 0))))
			.isEqualTo(new FileDetails(FilePurposes.STATEMENT_RESPONSE, LocalDateTime.of(2019, 6, 1, 0, 0)));
		assertThat(first.orElse(null)).isSameAs(first);
	}
}
