package se.sundsvall.aotloader.service.model;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.source.CodeTexts;
import se.sundsvall.aotloader.source.ContactRow;
import se.sundsvall.aotloader.source.PersonRow;
import se.sundsvall.aotloader.source.alkol.AlkolSnapshot;
import se.sundsvall.aotloader.source.alkt.AlktSnapshot;

import static org.assertj.core.api.Assertions.assertThat;
import static se.sundsvall.aotloader.service.model.PartyType.ENTERPRISE;
import static se.sundsvall.aotloader.service.model.PartyType.PRIVATE;

/**
 * Whatever holds names or numbers of persons and companies never shows them when it is written to a log (K6).
 */
class PersonalDataTest {

	private static final String NUMBER = "191212121212";
	private static final String NAME = "Tolvan Tolvansson";

	@Test
	void theModelHidesPersonalData() {
		final var legalId = new LegalId(NUMBER, PRIVATE);

		assertHidden(legalId);
		assertHidden(new Holder(legalId, NAME, "Gatan 1", "85230", "Sundsvall", "tolvan@example.com", "070-1", NAME));
		assertHidden(new Person(NAME, legalId, List.of("VD")));
		assertHidden(new PermitLink(new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), legalId, legalId));
		assertHidden(Candidate.builder(new ItemKey(Source.ALKT, Kind.TILLSTAND, "1"), ErrandType.SERVING_PERMIT).holder(new Holder(legalId, NAME, null, null, null, null, null, null)).build());
		assertHidden(new AssetData(null, "Tillstånd", null, null, Map.of("permitHolderName", NAME, "permitHolderOrgNumber", NUMBER)));
	}

	@Test
	void theRowsOfTheSourcesHidePersonalData() {
		assertHidden(new ContactRow(1, NUMBER, NAME, NAME, null, null, null, null, null, null));
		assertHidden(new AlktSnapshot(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
		assertHidden(new PersonRow(1, NAME, NUMBER, List.of(), false));
		assertHidden(new AlkolSnapshot.SalesPlaceRow(1, NAME, null, null, null, null, null, null, null, NUMBER, null, false, false, false, false, false, 1));
		assertHidden(new AlkolSnapshot(List.of(), Map.of(), Map.of(), List.of(), List.of(), List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of()));
	}

	@Test
	void permitLink() {
		final var holder = new LegalId("5566778899", ENTERPRISE);
		final var other = new LegalId("5566778800", ENTERPRISE);

		assertThat(new PermitLink(null, holder, holder).sameHolder()).isTrue();
		assertThat(new PermitLink(null, holder, other).sameHolder()).isFalse();
		assertThat(new PermitLink(null, null, holder).sameHolder()).isFalse();
		assertThat(new PermitLink(null, holder, null).sameHolder()).isFalse();
	}

	@Test
	void snapshots() {
		final var alkt = new AlktSnapshot(List.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of(), Map.of("A", Map.of("07", "Tillsyn")));
		assertThat(alkt.codeText("A", "07")).isEqualTo("Tillsyn");
		assertThat(alkt.codeText("A", "08")).isEqualTo("08");
		assertThat(alkt.codeText("X", "08")).isEqualTo("08");
		assertThat(alkt.codeText("A", null)).isNull();
		assertThat(alkt.conditionsOf(1)).isEmpty();
		assertThat(alkt.filesOf(1)).isEmpty();
		assertThat(alkt.personsOf(1)).isEmpty();

		assertThat(CodeTexts.textOf(Map.of("KIO", "Kiosk"), "KIO")).isEqualTo("Kiosk");
		assertThat(CodeTexts.textOf(Map.of(), "KIO")).isEqualTo("KIO");
		assertThat(CodeTexts.textOf(Map.of(), null)).isNull();
		assertThat(new AlkolSnapshot.DecisionRow(1, 1, "GTDE", LocalDateTime.of(2020, 1, 2, 3, 4), null, null, null).beslutsDatum()).isEqualTo(LocalDate.of(2020, 1, 2));
		assertThat(new AlkolSnapshot.DecisionRow(1, 1, "GTDE", null, null, null, null).beslutsDatum()).isNull();
	}

	private static void assertHidden(final Object object) {
		assertThat(object.toString()).doesNotContain(NUMBER, "Tolvan", "tolvan@example.com", "Gatan");
	}
}
