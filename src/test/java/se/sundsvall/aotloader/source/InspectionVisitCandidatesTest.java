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
import se.sundsvall.aotloader.service.model.MeasureData;
import se.sundsvall.aotloader.source.InspectionVisitRows.RemarkRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.RoundRow;
import se.sundsvall.aotloader.source.InspectionVisitRows.VisitRow;

import static org.assertj.core.api.Assertions.assertThat;

class InspectionVisitCandidatesTest {

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		2023-11-04 | 21:30    | 23:15    | 2023-11-04T21:30 | 2023-11-04T23:15
		2023-11-04 | 9.15     | 10:00:00 | 2023-11-04T09:15 | 2023-11-04T10:00
		2023-11-04 | 22:00    | 01:30    | 2023-11-04T22:00 | 2023-11-05T01:30
		2023-11-04 | 22:00    | 24:00    | 2023-11-04T22:00 | 2023-11-05T00:00
		2023-11-04 | 14:00:00 | -        | 2023-11-04T14:00 | -
		2023-11-04 | -        | 10:00    | 2023-11-04T00:00 | 2023-11-04T10:00
		2023-11-04 | kväll    | sent     | 2023-11-04T00:00 | -
		-          | 21:30    | 23:15    | -                | -
		""")
	void aVisitStartsOnItsDateAtItsStartTimeAndEndsAtItsEndTime(final LocalDate date, final String start, final String end, final LocalDateTime startedAt,
		final LocalDateTime completedAt) {
		final var visit = new VisitRow(900, 1, date, start, end, "04", null, null, null);

		assertThat(InspectionVisitCandidates.investigation("Tillsynsbesök", visit, List.of(), null)).isEqualTo(new InvestigationData("Tillsynsbesök", startedAt, completedAt));
	}

	@ParameterizedTest
	@CsvSource(delimiter = '|', nullValues = "-", textBlock = """
		Krogrunda | 2023-11-04 | 2023-11-05 | Krogrunda, 2023-11-04–2023-11-05
		Krogrunda | 2023-11-04 | 2023-11-04 | Krogrunda, 2023-11-04
		Krogrunda | 2023-11-04 | -          | Krogrunda, 2023-11-04
		Krogrunda | -          | 2023-11-05 | Krogrunda, 2023-11-05
		Krogrunda | -          | -          | Krogrunda
		-         | 2023-11-04 | 2023-11-05 | 2023-11-04–2023-11-05
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

		// A remark without a code is headed "Anmärkning", and one with neither a code nor a note is no measure
		assertThat(InspectionVisitCandidates.measures(remarks, rows)).containsExactly(
			new MeasureData("Servering till minderårig", "Ungdomar i baren"),
			new MeasureData("77", null),
			new MeasureData("Anmärkning", "Bara en notering"));
	}

	@Test
	void theRoundOfAVisitIsTheDescriptionOfItsInvestigation() {
		final var visit = new VisitRow(900, 1, LocalDate.of(2023, 11, 4), null, null, "04", " Lugnt i baren. ", null, null);

		assertThat(InspectionVisitCandidates.investigation("Tillsynsbesök", visit, List.of("Anmärkning"), new RoundRow(40, "Krogrunda", null, null, "Med polisen.")).texts())
			.isEqualTo(new InvestigationTexts("Tillsynsomgång: Krogrunda\nMed polisen.", "Lugnt i baren.", "Anmärkning"));
		// A round without a name, days or a note tells nothing
		assertThat(InspectionVisitCandidates.investigation("Tillsynsbesök", visit, List.of(), new RoundRow(41, null, null, null, " ")).texts())
			.isEqualTo(new InvestigationTexts(null, "Lugnt i baren.", null));
		assertThat(InspectionVisitCandidates.roundName(null)).isNull();
	}
}
