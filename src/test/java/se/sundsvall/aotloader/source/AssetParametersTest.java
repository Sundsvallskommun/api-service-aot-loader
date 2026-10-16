package se.sundsvall.aotloader.source;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.model.Holder;
import se.sundsvall.aotloader.service.model.LegalId;
import se.sundsvall.aotloader.service.model.Place;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;

class AssetParametersTest {

	@Test
	void blankValuesAreLeftOutAndLinesAreJoined() {
		final var parameters = new AssetParameters()
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
		final var holder = new Holder(new LegalId("191212121212", PRIVATE), "Tolvans Krog", "Gatan 1", "85230", null, null, null, null);
		final var place = new Place("Krogen", null, null, "Sundsvall", null, null, "City", List.of());

		final var parameters = new AssetParameters().holder(holder).premises(place, null).build();

		assertThat(parameters).containsExactly(
			Map.entry("permitHolderName", "Tolvans Krog"),
			Map.entry("permitHolderOrgNumber", "19121212-1212"),
			Map.entry("permitHolderStreet", "Gatan 1"),
			Map.entry("permitHolderPostalAddress", "85230"),
			Map.entry("premisesName", "Krogen"),
			Map.entry("premisesPostalAddress", "Sundsvall"));
		assertThat(new AssetParameters().holder(null).premises(null, "12345678").build()).isEmpty();
	}
}
