package se.sundsvall.aotloader.source;

import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.source.Journal.Entry;

import static org.assertj.core.api.Assertions.assertThat;

class JournalTest {

	@Test
	void theEntriesAreLinesInOrderOfTimeWithoutWhatTheyLack() {
		final var entries = List.of(
			new Entry(LocalDateTime.of(2020, 3, 1, 9, 0), "Anteckning", "  Andra raden\nfortsätter  "),
			new Entry(LocalDateTime.of(2020, 1, 1, 9, 0), "Beslut", null),
			new Entry(LocalDateTime.of(2020, 3, 1, 9, 0), null, "Samma tid, efter den första"),
			new Entry(null, "Utan tid", " "),
			new Entry(null, null, null),
			new Entry(LocalDateTime.of(2020, 4, 1, 9, 0), " ", null),
			new Entry(null, null, "Bara text"));

		assertThat(Journal.lines(entries)).containsExactly(
			"Utan tid",
			"Bara text",
			"2020-01-01 Beslut",
			"2020-03-01 Anteckning: Andra raden\nfortsätter",
			"2020-03-01: Samma tid, efter den första");
		assertThat(Journal.text(List.of())).isNull();
	}

	@Test
	void theInvestigationGetsTheNotesTheEventsAndTheDecisions() {
		final var texts = Journal.investigation(List.of("Första", " "), List.of(new Entry(LocalDateTime.of(2020, 1, 2, 0, 0), "Anteckning", "Ringde")),
			List.of(new Entry(LocalDateTime.of(2020, 2, 1, 0, 0), "Erinran", null)));

		assertThat(texts).isEqualTo(new InvestigationTexts("Första", "2020-01-02 Anteckning: Ringde", "2020-02-01 Erinran"));
		assertThat(Journal.investigation(List.of(), List.of(), List.of())).isEqualTo(InvestigationTexts.NONE);
	}

	@Test
	void anEventTellsItsDiaryNumberOnlyWhenItIsAnotherThanThatOfItsErrand() {
		assertThat(Journal.otherDiaryNumber(" IAN-2019-5 ", "IAN-2019-1")).isEqualTo("IAN-2019-5");
		assertThat(Journal.otherDiaryNumber("ian-2019-1 ", "IAN-2019-1")).isNull();
		assertThat(Journal.otherDiaryNumber(" ", "IAN-2019-1")).isNull();
		assertThat(Journal.otherDiaryNumber("IAN-2019-5", null)).isNull();
	}

	@Test
	void anEventIsMarkedWhenConfidentialAndADecisionTellsWhoMadeIt() {
		assertThat(Journal.eventHeading("Anteckning", true, null)).isEqualTo("Anteckning (sekretess)");
		assertThat(Journal.eventHeading("Anteckning", false, null)).isEqualTo("Anteckning");
		assertThat(Journal.eventHeading(null, true, null)).isEqualTo("(sekretess)");
		assertThat(Journal.eventHeading("Remissvar", false, "IAN-2019-5")).isEqualTo("Remissvar (diarienummer IAN-2019-5)");
		assertThat(Journal.eventHeading("Remissvar", true, "IAN-2019-5")).isEqualTo("Remissvar (sekretess, diarienummer IAN-2019-5)");
		assertThat(Journal.decisionHeading("Varning", "Anna Berg")).isEqualTo("Varning, beslutsfattare Anna Berg");
		assertThat(Journal.decisionHeading("Varning", null)).isEqualTo("Varning");
		assertThat(Journal.decisionHeading(null, "Anna Berg")).isEqualTo("beslutsfattare Anna Berg");
	}
}
