package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.InvestigationTexts.Part;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.Place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.tuple;
import static se.sundsvall.aotloader.Constants.ERRAND_PARAMETER_MAX_LENGTH;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;

class InspectionProtocolsTest {

	private static final Place PLACE = new Place("Krogen", "Storgatan 1", "85230", "Sundsvall", null, null, null, List.of());

	@Test
	void aVisitFillsTheFieldsOfTheServingPermitProtocolByTheKeysAndLabelsOfTheTemplate() {
		final var fields = InspectionProtocols.visit(PLACE, LocalDate.of(2023, 11, 4), "21:30", "24:00", List.of("Anna Berg", "Per Eriksson", "Karin Andersson"),
			InvestigationTexts.of(new Part("Anteckning", "Mycket folk."), new Part("Anmärkningar", "Servering till minderårig\nBerusad gäst")));

		// Draken takes midnight as 00:00, and has room for two inspectors
		assertThat(fields).containsExactly(
			new Parameter("premisesName", "Restaurang", List.of("Krogen")),
			new Parameter("premisesAddress", "Adress", List.of("Storgatan 1, 85230 Sundsvall")),
			new Parameter("inspectionDate", "Tillsynsdatum", List.of("2023-11-04")),
			new Parameter("visitStartTime", "Inleds kl.", List.of("21:30")),
			new Parameter("visitEndTime", "Avslutades kl.", List.of("00:00")),
			new Parameter("additionalRemarks", "Beskrivning", List.of("Anteckning:\nMycket folk.\n\nAnmärkningar:\nServering till minderårig\nBerusad gäst")),
			new Parameter("inspector1NameAndTitle", "Namn, befattning", List.of("Anna Berg")),
			new Parameter("inspector2NameAndTitle", "Namn, befattning", List.of("Per Eriksson")));
	}

	@Test
	void aVisitWithoutWhatTheFieldsAskForLeavesThemOut() {
		assertThat(InspectionProtocols.visit(null, null, null, null, List.of(), InvestigationTexts.NONE)).isEmpty();
		assertThat(InspectionProtocols.visit(new Place(" ", null, null, null, null, null, null, List.of()), null, "", "9", List.of(" "), InvestigationTexts.NONE)).isEmpty();
		assertThat(InspectionProtocols.visit(null, null, null, null, List.of("Anna Berg"), InvestigationTexts.NONE))
			.containsExactly(new Parameter("inspector1NameAndTitle", "Namn, befattning", List.of("Anna Berg")));
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
		final var fields = InspectionProtocols.visit(null, null, written, null, List.of(), InvestigationTexts.NONE);

		assertThat(fields).extracting(Parameter::key, parameter -> parameter.values().getFirst())
			.containsExactlyElementsOf(expected == null ? List.of() : List.of(tuple("visitStartTime", expected)));
	}

	@Test
	void aFieldLongerThanAValueIsShortened() {
		final var fields = InspectionProtocols.visit(null, null, null, null, List.of(), InvestigationTexts.of(new Part("Anteckning", "ord ".repeat(1000))));

		assertThat(fields).singleElement().satisfies(field -> {
			assertThat(field.key()).isEqualTo("additionalRemarks");
			assertThat(field.values().getFirst()).hasSizeLessThanOrEqualTo(ERRAND_PARAMETER_MAX_LENGTH).startsWith("Anteckning:\nord ord").endsWith("…");
		});
	}

	@Test
	void anInternalInspectionFillsTheFieldsOfItsProtocol() {
		final var holder = new Holder(new LegalId("5566778899", ENTERPRISE), "Bolaget AB", null, null, null, null, null, null);

		assertThat(InspectionProtocols.internal(holder, PLACE, "Tillsyn, ifrågasatt åtgärd", LocalDate.of(2020, 1, 1), List.of("Anna Berg", "ZZ"))).containsExactly(
			new Parameter("businessName", "Verksamhetens namn", List.of("Bolaget AB")),
			new Parameter("businessOrgNumber", "Organisationsnummer", List.of("556677-8899")),
			new Parameter("businessAddress", "Adress", List.of("Storgatan 1, 85230 Sundsvall")),
			new Parameter("inspectionType", "Typ av tillsyn", List.of("Tillsyn, ifrågasatt åtgärd")),
			new Parameter("inspectionDate", "Datum", List.of("2020-01-01")),
			new Parameter("inspectorName", "Utförd av", List.of("Anna Berg, ZZ")));
		assertThat(InspectionProtocols.internal(null, null, "Tillsyn", null, List.of())).containsExactly(new Parameter("inspectionType", "Typ av tillsyn", List.of("Tillsyn")));
	}
}
