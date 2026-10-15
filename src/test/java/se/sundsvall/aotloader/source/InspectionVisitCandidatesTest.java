package se.sundsvall.aotloader.source;

import java.time.LocalDate;
import java.time.LocalDateTime;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import se.sundsvall.aotloader.service.model.InvestigationData;
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

		assertThat(InspectionVisitCandidates.investigation("Tillsynsbesök", visit)).isEqualTo(new InvestigationData("Tillsynsbesök", startedAt, completedAt));
	}
}
