package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.aotloader.service.TestData;
import se.sundsvall.aotloader.service.model.ErrandType;
import se.sundsvall.aotloader.service.model.ItemKey;
import se.sundsvall.aotloader.service.model.Kind;
import se.sundsvall.aotloader.service.model.Parameter;
import se.sundsvall.aotloader.service.model.PermitLink;
import se.sundsvall.aotloader.service.model.PlacePermit;
import se.sundsvall.aotloader.service.model.RuleHit;
import se.sundsvall.aotloader.service.model.Source;
import se.sundsvall.aotloader.source.DocumentationCandidates.PlaceEvent;

import static org.assertj.core.api.Assertions.assertThat;

class DocumentationCandidatesTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 23);

	@ParameterizedTest
	@CsvSource(nullValues = "-", textBlock = """
		Ringde, false, true
		' ',    false, false
		-,      false, false
		-,      true,  true
		""")
	void anEventBecomesAnErrandWhenItHasATextOrAFile(final String text, final boolean withFile, final boolean expected) {
		assertThat(DocumentationCandidates.documents(text, withFile)).isEqualTo(expected);
	}

	@Test
	void anEventWithoutAPlaceATypeOrADayIsStillAnErrandFiledUnderThePermit() {
		final var event = new PlaceEvent(Source.ALKOL, 303, " ", null, " Ringde om skylten. ", true, "AOT 2020-5");
		final var permit = new PlacePermit(new ItemKey(Source.ALKOL, Kind.TILLSTAND, "1"), null, null, true);
		final var documentation = new DocumentationCandidates(ErrandType.TOBACCO_DOCUMENTATION, TestData.properties(), "okänt försäljningsställe", TODAY);

		final var candidate = documentation.builder(event, null, null, new FormerHolders(Set.of(), null), permit).build();

		assertThat(candidate.key()).isEqualTo(new ItemKey(Source.ALKOL, Kind.DOKUMENTATION, "303"));
		assertThat(candidate.ruleHits()).isEmpty();
		assertThat(candidate.holder()).isNull();
		assertThat(candidate.errand().title()).isEqualTo("Händelse – okänt försäljningsställe");
		assertThat(candidate.errand().description()).isEqualTo("""
			Händelse på okänt försäljningsställe, migrerad från AlkOL, där den inte hörde till något ärende.
			Diarienummer: AOT 2020-5
			Händelsen är markerad med sekretess i AlkOL.
			Ringde om skylten.""");
		assertThat(candidate.errand().labelPaths()).containsExactly(TestData.DOCUMENTATION);
		assertThat(candidate.errand().parameters()).extracting(Parameter::key).containsExactly("alkolHandelseId", "handelsetyp", "diarienummer", "sekretess", "migrerad");
		assertThat(candidate.permitLink().permit()).isEqualTo(permit);
		assertThat(candidate.permitLink().basis()).isEqualTo(PermitLink.Basis.FILED);
	}

	@ParameterizedTest
	@CsvSource(nullValues = "-", textBlock = """
		2018-12-31T23:59, true
		2019-01-01T00:00, false
		-,                false
		""")
	void anEventFromTheTimeOfAFormerHolderIsNotMigrated(final LocalDateTime time, final boolean former) {
		final var event = new PlaceEvent(Source.ALKT, 13, "Anteckning", time, "Samtal", false, null);
		final var documentation = new DocumentationCandidates(ErrandType.ALCOHOL_DOCUMENTATION, TestData.properties(), "okänt serveringsställe", TODAY);

		final var candidate = documentation.builder(event, null, null, new FormerHolders(Set.of(703), LocalDate.of(2018, 12, 31)), null).build();

		assertThat(candidate.ruleHits()).extracting(RuleHit::toReportText)
			.containsExactlyElementsOf(former
				? List.of("G32 Ärendet eller händelsen gällde en tidigare ägare av stället, och bara den nuvarande tillståndshavarens följer med (beslut 72): "
					+ "händelsen var 2018-12-31, och stället hade en tidigare ägare till och med 2018-12-31")
				: List.of());
	}

	@Test
	void anEventShowsOnlyWhichItIsInALog() {
		final var event = new PlaceEvent(Source.ALKT, 31020, "Anteckning", LocalDateTime.of(2026, 10, 13, 10, 0), "Tolvan Tolvansson 191212121212", true, "AOT 2026-1");

		assertThat(event).hasToString("PlaceEvent[source=ALKT, handelseId=31020]");
		assertThat(event.day()).isEqualTo(LocalDate.of(2026, 10, 13));
	}
}
