package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.model.DecisionData;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.Place;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;

class CertificateParametersTest {

	@Test
	void blankValuesAreLeftOutAndLinesAreJoined() {
		final var parameters = new CertificateParameters()
			.text("blank", " ")
			.text("missing", null)
			.text("stripped", " text ")
			.flag("yes", true)
			.flag("no", false)
			.lines("lines", List.of("11:00–14:00", "17:00–01:00"))
			.lines("noLines", List.of())
			.build();

		assertThat(parameters).containsExactly(
			Map.entry("stripped", "text"),
			Map.entry("yes", "true"),
			Map.entry("no", "false"),
			Map.entry("lines", "11:00–14:00\n17:00–01:00"));
	}

	@Test
	void holderAndPremises() {
		final var holder = new Holder(new LegalId("191212121212", PRIVATE), "Tolvans Krog", "Gatan 1", "85230", null, null, "060-1", null);
		final var place = new Place("Krogen", null, "852 30", "Sundsvall", "060-2", null, "City", List.of(new Parameter("restaurantNumber", null, List.of("12345678"))));

		final var parameters = new CertificateParameters().holder(holder).premises(place).build();

		// A sole trader's number is a personal identity number, which is left out as Draken leaves it out (decision 81), and
		// the restaurant number is left to the process that issues the certificate, as Draken leaves it
		assertThat(parameters).containsExactly(
			Map.entry("permitHolderName", "Tolvans Krog"),
			Map.entry("permitHolderStreet", "Gatan 1"),
			Map.entry("permitHolderPostalAddress", "85230"),
			Map.entry("premisesName", "Krogen"),
			Map.entry("premisesPostalAddress", "85230 Sundsvall"),
			Map.entry("premisesPhone", "060-2"));
		assertThat(new CertificateParameters().holder(null).premises(null).build()).isEmpty();
		assertThat(new CertificateParameters().holder(new Holder(new LegalId("5566778899", ENTERPRISE), "Bolaget AB", null, null, null, null, null, null)).build())
			.containsEntry("permitHolderOrgNumber", "556677-8899");
	}

	@Test
	void theDecisionAsDrakenWritesIt() {
		final var decision = new DecisionData("Anna Berg", "Anna Berg", LocalDateTime.of(2019, 4, 30, 9, 0), null, null, LocalDate.of(2019, 5, 1), null, List.of(), List.of())
			.withCase("SN 2019/123", "Bo Ek");

		assertThat(new CertificateParameters().decision(decision).build()).containsExactly(
			Map.entry("decisionCaseNumber", "SN 2019/123"),
			Map.entry("decisionMaker", "Anna Berg"),
			Map.entry("decisionDate", "2019-04-30"),
			Map.entry("validFrom", "2019-05-01"));
		assertThat(CertificateParameters.decisionDateAndCaseNumber(decision)).isEqualTo("2019-04-30, SN 2019/123");
		assertThat(CertificateParameters.decisionOfficerAndCaseNumber(decision)).isEqualTo("Anna Berg, SN 2019/123");

		// A decision whose maker and diary number the source does not give has neither
		final var unknown = new DecisionData("Uppgift saknas i AlkT", null, LocalDateTime.of(2019, 4, 30, 0, 0), null, null, null, null, List.of(), List.of());
		assertThat(new CertificateParameters().decision(unknown).build()).containsExactly(Map.entry("decisionDate", "2019-04-30"));
		assertThat(CertificateParameters.decisionDateAndCaseNumber(unknown)).isEqualTo("2019-04-30");
		assertThat(CertificateParameters.decisionOfficerAndCaseNumber(unknown)).isNull();
	}
}
