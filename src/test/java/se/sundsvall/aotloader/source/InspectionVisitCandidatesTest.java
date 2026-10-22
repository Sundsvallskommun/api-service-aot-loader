package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.aotloader.service.model.InvestigationData;
import se.sundsvall.aotloader.service.model.InvestigationTexts;
import se.sundsvall.aotloader.service.model.InvestigationTexts.Part;
import se.sundsvall.aotloader.service.model.MeasureData;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.RoundRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.StaffMarks;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionVisitCandidatesTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		2023-11-04 | -          | 21:30    | 23:15    | 2023-11-04T21:30 | 2023-11-04T23:15
		2023-11-04 | -          | 9.15     | 10:00:00 | 2023-11-04T09:15 | 2023-11-04T10:00
		2023-11-04 | -          | 22:00    | 01:30    | 2023-11-04T22:00 | 2023-11-05T01:30
		2023-11-04 | 2023-11-04 | 22:00    | 01:30    | 2023-11-04T22:00 | 2023-11-05T01:30
		2023-11-04 | 2023-11-05 | 22:00    | 01:30    | 2023-11-04T22:00 | 2023-11-05T01:30
		2023-11-04 | 2023-11-06 | 22:00    | 01:30    | 2023-11-04T22:00 | 2023-11-06T01:30
		2023-11-04 | 2023-11-03 | 14:00    | 15:00    | 2023-11-04T14:00 | 2023-11-04T15:00
		2023-11-04 | -          | 22:00    | 24:00    | 2023-11-04T22:00 | 2023-11-05T00:00
		2023-11-04 | -          | 14:00:00 | -        | 2023-11-04T14:00 | 2023-11-04T14:00
		2023-11-04 | 2023-11-05 | 14:00    | -        | 2023-11-04T14:00 | 2023-11-05T00:00
		2023-11-04 | -          | -        | 10:00    | 2023-11-04T00:00 | 2023-11-04T10:00
		2023-11-04 | -          | kväll    | sent     | 2023-11-04T00:00 | 2023-11-04T00:00
		-          | 2023-11-05 | 21:30    | 23:15    | -                | -
		""")
	void aVisitStartsOnItsDateAtItsStartTimeAndEndsAtItsEndTimeOnItsEndDayOrWhenItStarted(final LocalDate date, final LocalDate endDay, final String start, final String end,
		final LocalDateTime startedAt, final LocalDateTime completedAt) {
		final var visit = new VisitRow(900, 1, date, endDay, start, end, "04", null, null, null, List.of(), StaffMarks.NONE, null, List.of(), false);

		assertThat(InspectionVisitCandidates.investigation("Tillsynsbesök", visit, List.of(), null)).isEqualTo(new InvestigationData("Tillsynsbesök", startedAt, completedAt));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		Krogrunda | 2023-11-04 | 2023-11-05 | Krogrunda, 2023-11-04 – 2023-11-05
		Krogrunda | 2023-11-04 | 2023-11-04 | Krogrunda, 2023-11-04
		Krogrunda | 2023-11-04 | -          | Krogrunda, 2023-11-04
		Krogrunda | -          | 2023-11-05 | Krogrunda, 2023-11-05
		Krogrunda | -          | -          | Krogrunda
		-         | 2023-11-04 | 2023-11-05 | 2023-11-04 – 2023-11-05
		' '       | -          | -          | -
		""")
	void aRoundIsNamedWithItsDays(final String name, final LocalDate start, final LocalDate end, final String expected) {
		assertThat(InspectionVisitCandidates.roundName(new RoundRow(40, name, start, end, null))).isEqualTo(expected);
	}

	@Test
	void theRemarksOfAVisitAreItsMeasures() {
		final var rows = new InspectionVisitRows(List.of(), Map.of(), Map.of(), Map.of(), Map.of("05", "Servering till minderårig"));
		final var remarks = List.of(
			new RemarkRow(900, "05", " Ungdomar i baren ", null),
			new RemarkRow(900, "77", null, null),
			new RemarkRow(900, null, "Bara en notering", null),
			new RemarkRow(900, null, " ", "bild.jpg"));

		final var visited = LocalDateTime.of(2023, 11, 4, 23, 0);

		// A remark without a code is headed "Anmärkning", and one with neither a code nor a note is no measure
		assertThat(InspectionVisitCandidates.measures(remarks, rows, visited)).containsExactly(
			new MeasureData("Servering till minderårig", "Ungdomar i baren", visited),
			new MeasureData("77", null, visited),
			new MeasureData("Anmärkning", "Bara en notering", visited));
	}

	@Test
	void theTextsOfTheInvestigationOfAVisitAreItsRoundItsNoteAndItsRemarks() {
		final var visit = new VisitRow(900, 1, LocalDate.of(2023, 11, 4), null, null, "04", " Lugnt i baren. ", null, null);

		assertThat(InspectionVisitCandidates.investigation("Tillsynsbesök", visit, List.of("Anmärkning"), new RoundRow(40, "Krogrunda", null, null, "Med polisen.")).texts())
			.isEqualTo(InvestigationTexts.of(new Part("Tillsynsomgång", "Krogrunda\nMed polisen."), new Part("Anteckning", "Lugnt i baren."), new Part("Anmärkningar", "Anmärkning")));
		// A round without a name, days or a note tells nothing
		assertThat(InspectionVisitCandidates.investigation("Tillsynsbesök", visit, List.of(), new RoundRow(41, null, null, null, " ")).texts())
			.isEqualTo(InvestigationTexts.of(new Part("Anteckning", "Lugnt i baren.")));
		assertThat(InspectionVisitCandidates.roundName(null)).isNull();
	}

	@Test
	void theContentOfAVisitShowsNoNamesOrRemarks() {
		final var visit = new VisitRow(900, 1, LocalDate.of(2023, 11, 4), null, null, "04", " Lugnt i baren. ", null, null);
		final var content = new InspectionVisitCandidates.VisitContent(visit, "Tillsynsbesök", "14:05", List.of("Anmärkning"), List.of("Tolvan Tolvansson"), List.of("Saknade utbildning"), null);

		assertThat(content).hasToString("VisitContent[visit=VisitRow[tillfalleNr=900], kind=Tillsynsbesök]");
	}
}
