package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.InvestigationTexts.Part;
import se.sundsvall.aotloader.service.model.JsonContent;
import se.sundsvall.aotloader.service.model.JsonParameterType;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Place;
import se.sundsvall.aotloader.source.InspectionVisitRows.StaffMarks;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;

class InspectionProtocolsTest {

	private static final JsonMapper JSON = JsonMapper.builder().build();
	private static final Place PLACE = new Place("Krogen", "Storgatan 1", "85230", "Sundsvall", null, null, null, null);

	@Test
	void aVisitFillsTheFieldsOfTheServingPermitProtocolByTheKeysOfTheTemplate() {
		final var fields = InspectionProtocols.visit(PLACE, LocalDate.of(2023, 11, 4), "21:30", "24:00", true, List.of("Anna Berg", "Per Eriksson", "Karin Andersson"),
			InvestigationTexts.of(new Part("Anteckning", "Mycket folk."), new Part("Anmärkningar", "Servering till minderårig\nBerusad gäst")));

		// Draken takes midnight as 00:00, and has room for two inspectors
		assertThat(fields.type()).isEqualTo(JsonParameterType.SERVING_PERMIT_PROTOCOL);
		assertThat(json(fields)).isEqualTo("""
			{"premisesName":"Krogen","premisesAddress":"Storgatan 1, 85230 Sundsvall","inspectionDate":"2023-11-04","visitStartTime":"21:30","visitEndTime":"00:00",\
			"responsiblePerson":"present","additionalRemarks":"Anteckning:\\nMycket folk.\\n\\nAnmärkningar:\\nServering till minderårig\\nBerusad gäst",\
			"inspector1NameAndTitle":"Anna Berg","inspector2NameAndTitle":"Per Eriksson"}""");
	}

	@Test
	void aVisitWithoutWhatTheFieldsAskForLeavesThemOut() {
		assertThat(InspectionProtocols.visit(null, null, null, null, null, List.of(), InvestigationTexts.NONE).isEmpty()).isTrue();
		assertThat(InspectionProtocols.visit(new Place(" ", null, null, null, null, null, null, null), null, "", "9", null, List.of(" "), InvestigationTexts.NONE).isEmpty()).isTrue();
		assertThat(json(InspectionProtocols.visit(null, null, null, null, null, List.of("Anna Berg"), InvestigationTexts.NONE))).isEqualTo("""
			{"inspector1NameAndTitle":"Anna Berg"}""");
	}

	@ParameterizedTest
	@CsvSource(nullValues = "-", value = {
		// owner on site, staff not checked, staff not on site, staff recorded: the option of the field, none when unknown
		"true, false, false, false, present",
		"false, false, false, true, present",
		"true, true, false, true, present",
		"false, false, true, false, absent",
		"false, true, true, false, absent",
		// Contradicting marks tell nothing, and neither does staff not checked alone
		"true, false, true, false, -",
		"false, false, true, true, -",
		"false, true, false, false, -",
		"false, false, false, false, -"
	})
	void whetherSomeoneResponsibleWasOnSiteFollowsTheMarksAboutTheStaff(final boolean ownerOnSite, final boolean staffNotChecked, final boolean staffNotOnSite,
		final boolean staffRecorded, final String expected) {
		final var marks = new StaffMarks(ownerOnSite, staffNotChecked, staffNotOnSite, staffRecorded);

		final var fields = InspectionProtocols.visit(null, null, null, null, marks.responsibleOnSite().orElse(null), List.of(), InvestigationTexts.NONE);

		assertThat(json(fields)).isEqualTo(expected == null ? "{}" : "{\"responsiblePerson\":\"%s\"}".formatted(expected));
	}

	@Test
	void theMarksAboutTheStaffReadAsTheBusinessNamesThem() {
		assertThat(new StaffMarks(true, true, true, true).texts())
			.containsExactly("Ägaren på plats", "Personalen inte kontrollerad", "Personalen inte på plats", "Personal registrerad vid besöket");
		assertThat(StaffMarks.NONE.texts()).isEmpty();
		assertThat(StaffMarks.NONE.responsibleOnSite()).isEmpty();
	}

	@ParameterizedTest
	@CsvSource(nullValues = "-", value = {
		"9.05, 09:05",
		"00:00, 00:00",
		"24:00, 00:00",
		"24:01, -",
		"25:00, -",
		"9, -"
	})
	void theTimesOfAVisitAreWrittenAsDrakenTakesThem(final String written, final String expected) {
		final var fields = InspectionProtocols.visit(null, null, written, null, null, List.of(), InvestigationTexts.NONE);

		assertThat(json(fields)).isEqualTo(expected == null ? "{}" : "{\"visitStartTime\":\"%s\"}".formatted(expected));
	}

	@Test
	void aLongFieldIsKeptWhole() {
		final var text = "ord ".repeat(1000).strip();

		final var fields = InspectionProtocols.visit(null, null, null, null, null, List.of(), InvestigationTexts.of(new Part("Anteckning", text)));

		// The JSON parameter has no limit of length
		assertThat(fields.value().get("additionalRemarks").asString()).isEqualTo("Anteckning:\n" + text);
	}

	@Test
	void anInternalInspectionFillsTheFieldsOfItsProtocol() {
		final var holder = new Holder(new LegalId("5566778899", ENTERPRISE), "Bolaget AB", null, null, null, null, null, null);

		final var fields = InspectionProtocols.internal(holder, PLACE, "Tillsyn, ifrågasatt åtgärd", LocalDate.of(2020, 1, 1), List.of("Anna Berg", "ZZ"));

		assertThat(fields.type()).isEqualTo(JsonParameterType.INTERNAL_INSPECTION_PROTOCOL);
		assertThat(json(fields)).isEqualTo("""
			{"businessName":"Bolaget AB","businessOrgNumber":"556677-8899","businessAddress":"Storgatan 1, 85230 Sundsvall","inspectionType":"Tillsyn, ifrågasatt åtgärd",\
			"inspectionDate":"2020-01-01","inspectorName":"Anna Berg, ZZ"}""");
		assertThat(json(InspectionProtocols.internal(null, null, "Tillsyn", null, List.of()))).isEqualTo("""
			{"inspectionType":"Tillsyn"}""");
		// A sole trader's number is a personal identity number, which Draken leaves out of the field (decision 81)
		assertThat(json(InspectionProtocols.internal(new Holder(new LegalId("191212121212", PRIVATE), "Tolvans Krog", null, null, null, null, null, null), null, "Tillsyn", null,
			List.of()))).isEqualTo("""
				{"businessName":"Tolvans Krog","inspectionType":"Tillsyn"}""");
	}

	private static String json(final JsonContent content) {
		return JSON.writeValueAsString(content.value());
	}
}
